package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.util.*;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.snowowl.authoring.scheduler.api.AuthenticationService;
import org.ihtsdo.snowowl.authoring.scheduler.api.mq.Transmitter;
import org.ihtsdo.snowowl.authoring.scheduler.api.repository.*;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

public class ScheduleServiceImpl implements ScheduleService {
	
	@Autowired
	JobRunRepository jobRunRepository;
	
	@Autowired
	JobRepository jobRepository;
	
	@Autowired
	JobTypeRepository jobTypeRepository;
	
	@Autowired
	JobCategoryRepository jobCategoryRepository;
	
	@Autowired
	JobScheduleRepository jobScheduleRepository;
	
	@Autowired
	TaskScheduler engineScheduler;
	
	@Autowired
	Transmitter transmitter;
	
	@Autowired
	AuthenticationService authenticationService;
	
	@Value("${schedule.manager.terminoloy.server.uri}")
	String terminologyServerUrl;
	
	static final JobRun metadataRequest = JobRun.create("METADATA", null);
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());

	@PostConstruct
	public void init() {
		logger.info("Recovering previously saved Job Schedules from repository");
		//Schedule all known cron jobs in memory
		for (JobSchedule jobSchedule : jobScheduleRepository.findAll()) {
			scheduleJob(jobSchedule);
		}
		
		//Always refresh list of known jobs on startup
		initialise();
	}

	@Override
	public List<JobType> listJobTypes() {
		return jobTypeRepository.findAll();
	}

	@Override
	public List<JobCategory> listJobTypeCategories(String typeName) throws BusinessServiceException {
		//Do we know about this job type?
		JobType jobType = jobTypeRepository.findByName(typeName);
		
		if (jobType != null) {
			return jobCategoryRepository.findByType(jobType);
		} else {
			throw new ResourceNotFoundException("Unknown jobType : '" + typeName + "'");
		}
	}

	@Override
	public Job getJob(String jobName) {
		return jobRepository.findByName(jobName);
	}

	@Override
	public List<JobRun> listJobsRun(String typeName, String jobName, String user, Set<String> projects) {
		if (user != null && !user.isEmpty()) {
			return jobRunRepository.findByJobNameAndUserAndProjectIn(jobName, user, projects);
		} else {
			return jobRunRepository.findByJobNameAndProjectInOrderByRequestTimeDesc(jobName, projects);
		}
	}

	@Override
	public JobRun runJob(String jobType, String jobName, JobRun jobRun) throws BusinessServiceException {
		//Make sure we know what this job is before we run it!
		Job job = getJob(jobName);
		if (job == null) {
			throw new ResourceNotFoundException("Unknown job : '" + jobType + "/" + jobName + "'");
		}
		return runJob(jobRun);
	}
	
	public JobRun runJob(JobRun jobRun) throws BusinessServiceException {
		//Do we know about this job?
		Job job = getJob(jobRun.getJobName());
		if (job == null) {
			throw new ResourceNotFoundException("Job unknown to Schedule Service: '" + jobRun.getJobName() +"' If job exists and is active, re-run initialise.");
		}
		
		jobRun.setRequestTime(new Date());
		jobRun.setStatus(JobStatus.Scheduled);
		jobRun.setTerminologyServerUrl(terminologyServerUrl);
		jobRun.setWhiteList(job.getWhiteList());
		populateAuthenticationDetails(jobRun);
		
		//We protect the json from having parent links and redundant keys, 
		//but these are needed when saving to the database
		//Also reinforce the display order that is specified by the job
		for (String parameterKey : jobRun.getParameters().keySet()) {
			jobRun.getParameters().get(parameterKey).setParentParams(jobRun.getParameters());
			jobRun.getParameters().get(parameterKey).setParamKey(parameterKey);
			JobParameter jobParam = job.getParameters().get(parameterKey);
			if (jobParam == null) {
				throw new BusinessServiceException(jobRun.getJobName() + " didn't expect user supplied parameter: '" + parameterKey + "'");
			} else {
				if (parameterKey.equals("project")) {
					jobRun.setProject(jobParam.getValue());
				}
				
				Integer displayOrder = job.getParameters().get(parameterKey).getDisplayOrder();
				if (displayOrder == null) {
					logger.warn(jobRun.getJobName() + " parameter " + parameterKey + " does not specify a display order");
					displayOrder = 0;
				}
				jobRun.getParameters().get(parameterKey).setDisplayOrder(displayOrder);
			}
		}
		
		jobRun = jobRunRepository.save(jobRun);
		logger.info("Running job: {}", jobRun);
		transmitter.send(jobRun);
		return jobRun;
	}

	private void populateAuthenticationDetails(JobRun jobRun) {
		//Can we get a token from our security context?
		String token = SecurityUtil.getAuthenticationToken();
		if (token == null) {
			token = authenticationService.getSystemAuthorisation();
		}
		jobRun.setAuthToken(token);
		
		String user = SecurityUtil.getUsername();
		if (StringUtils.isEmpty(user)) {
			user = "System";
		}
		jobRun.setUser(user);
	}

	@Override
	public JobSchedule scheduleJob(String jobType, String jobName, JobSchedule jobSchedule) {
		jobSchedule.setId(UUID.randomUUID());
		return jobSchedule;
	}
	
	
	private void scheduleJob(JobSchedule jobSchedule) {
		Trigger trigger = new CronTrigger(jobSchedule.getSchedule());
		JobRun jobRun = JobRun.create(jobSchedule);
		JobRunner runner = new JobRunner (this, jobRun);
		engineScheduler.schedule(runner, trigger);
	}

	@Override
	public void deleteSchedule(String jobType, String jobName, UUID scheduleId) {
		jobScheduleRepository.deleteById(scheduleId);
	}

	@Override
	public JobRun getJobRun(String typeName, String jobName, UUID runId) {
		Optional<JobRun> result = jobRunRepository.findById(runId);
		return result.orElse(null);
	}

	@Override
	public void initialise() {
		logger.info("Sending request for metadata");
		transmitter.send(metadataRequest);
	}

	@Override
	public synchronized void processResponse(JobRun jobRun) {
		try {
			//If we already know about this jobRun, don't allow the status to be reverted
			Optional<JobRun> savedJob = jobRunRepository.findById(jobRun.getId());
			
			//We protect the json from having parent links in them, but this is needed 
			//when saving to the database
			for (String parameterKey : jobRun.getParameters().keySet()) {
				jobRun.getParameters().get(parameterKey).setParamKey(parameterKey);
				jobRun.getParameters().get(parameterKey).setParentParams(jobRun.getParameters());
			}
			
			if (savedJob.isPresent()) {
				JobStatus existingStatus = savedJob.get().getStatus();
				if (existingStatus.equals(JobStatus.Complete) || existingStatus.equals(JobStatus.Failed)) {
					logger.error("Job already at status {}, ignoring response {}", existingStatus, jobRun);
					return;
				}
				//We need to grab the ID of the saved parameters objects 
				//so we can update the correct one in the db, otherwise we save a fresh copy
				jobRun.getParameters().setId(savedJob.get().getParameters().getId());
			}
			logger.info("Saving job response: {}", jobRun);
			jobRunRepository.save(jobRun);
		} catch (Exception e) {
			logger.error("Unable to process response for jobRun '{}'", jobRun, e);
		}
	}

	@Override
	public void processMetadata(JobMetadata metadata) {
		logger.info("Processing metadata for {} job types",metadata.getJobTypes().size());
		for (JobType jobType : metadata.getJobTypes()) {
			try {
				//Do we know about this jobType?
				JobType knownType = jobTypeRepository.findByName(jobType.getName());
				if (knownType == null) {
					jobTypeRepository.save(jobType);
				}
				
				//What categories are contained?
				logger.info("Processing metadata for {} categories in type '{}'",jobType.getCategories().size(), jobType.getName());
				for (JobCategory jobCategory : jobType.getCategories()) {
					//Do we know about this job category?
					String categoryName = jobCategory.getName();
					jobCategory.setType (jobType);
					JobCategory knownCategory = jobCategoryRepository.findByName(categoryName);
					if (knownCategory == null) {
						knownCategory = jobCategoryRepository.save(jobCategory);
					}
					
					logger.info("Processing metadata for {} jobs in category '{}'",jobCategory.getJobs().size(), jobCategory.getName());
					for (Job job : jobCategory.getJobs()) {
						job.setCategory(knownCategory);

						//Do we know about this job already
						Job knownJob = jobRepository.findByName(job.getName());
						if (knownJob == null) {
							logger.info("Saving job: " + job);
						} else {
							job.setId(knownJob.getId());
							//Whitelists are maintained by schedule manager, so retain
							job.replaceWhiteList(knownJob.getWhiteList());
							logger.info("Updating job: " + job);
						}
						
						//We protect the json from having parent links and redundant keys, 
						//but these are needed  when saving to the database
						for (String parameterKey : job.getParameters().keySet()) {
							job.getParameters().get(parameterKey).setParentParams(job.getParameters());
							job.getParameters().get(parameterKey).setParamKey(parameterKey);
						}
						jobRepository.save(job);
					}
					
					//Do we have to remove any jobs that have been withdrawn?
					List<Job> savedJobs = jobRepository.findByCategoryId(knownCategory.getId());
					savedJobs.removeAll(jobCategory.getJobs());
					//All the jobs we're left with were not represented in the metadata, so hide
					for (Job withdrawnJob : savedJobs) {
						logger.info("Marking job as withdrawn/hidden: {}", withdrawnJob);
						withdrawnJob.setProductionStatus(Job.ProductionStatus.HIDEME);
						jobRepository.save(withdrawnJob);
					}
				}
			} catch (Exception e) {
				logger.error("Unable to process metadata", e);
			}
		}
		logger.info("Metadata processing complete");
	}

	@Override
	public boolean deleteJobRun(String typeName, String jobName, UUID runId) {
		//Do we have this job?
		JobRun jobRun = getJobRun(typeName, jobName, runId);
		if (jobRun == null) {
			logger.error("Unable to delete. JobRun with id {} not found.", runId);
			return false;
		}
		logger.info("Deleting JobRun {}", jobRun);
		jobRunRepository.delete(jobRun);
		return true;
	}

	@Override
	public Set<WhiteListedConcept> getWhiteList(String typeName, String jobName) throws ResourceNotFoundException {
		//Do we know about this job?
		Job job = getJob(jobName);
		if (job == null) {
			throw new ResourceNotFoundException("Job unknown to Schedule Service: '" + jobName+"' If job exists and is active, re-run initialise.");
		}
		return job.getWhiteList();
	}

	@Override
	public void setWhiteList(String typeName, String jobName, Set<WhiteListedConcept> whiteList) throws ResourceNotFoundException {
		//Do we know about this job?
		Job job = getJob(jobName);
		if (job == null) {
			throw new ResourceNotFoundException("Job unknown to Schedule Service: '" + jobName +"' If job exists and is active, re-run initialise.");
		}
		logger.info("Whitelisted {} concepts for job: {}", whiteList.size(), jobName);
		job.setWhiteList(whiteList);
		jobRepository.save(job);
	}
	
}
