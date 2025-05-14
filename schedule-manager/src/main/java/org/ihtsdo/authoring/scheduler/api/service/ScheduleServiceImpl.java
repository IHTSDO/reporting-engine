package org.ihtsdo.authoring.scheduler.api.service;

import java.util.*;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PostConstruct;

import org.ihtsdo.authoring.scheduler.api.repository.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.authoring.scheduler.api.AuthenticationService;
import org.ihtsdo.authoring.scheduler.api.mq.Transmitter;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.sso.integration.SecurityUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

public class ScheduleServiceImpl implements ScheduleService {

	private static final int STUCK_JOB_HOURS = 10;

	private static final int DEBUG_LENGTH_LIMIT = 50000;

	private static final String JOB_UNKNOWN = "Job unknown to Schedule Service: '";

	private static final String CHECK_INITIALISE = "'. If job exists and is active, re-run initialise.";

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
	WhiteListRepository whiteListRepository;
	
	@Autowired
	TaskScheduler engineScheduler;
	
	@Autowired
	Transmitter transmitter;
	
	@Autowired
	AuthenticationService authenticationService;
	
	@Value("${schedule.manager.terminology.server.uri}")
	String terminologyServerUrl;
	
	static final JobRun metadataRequest = JobRun.create("METADATA", null);
	private static final Logger LOGGER = LoggerFactory.getLogger(ScheduleServiceImpl.class);

	@PostConstruct
	public void init() {
		LOGGER.info("Recovering previously saved Job Schedules from repository");
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
	public Page<JobRun> listJobsRun(String typeName, String jobName, String user, Set<String> projects, Pageable pageable) {
		if (user != null && !user.isEmpty()) {
			return jobRunRepository.findByJobNameAndUserAndProjectInOrderByRequestTimeDesc(jobName, user, projects, pageable);
		} else {
			return jobRunRepository.findByJobNameAndProjectInOrderByRequestTimeDesc(jobName, projects, pageable);
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
			throw new ResourceNotFoundException(JOB_UNKNOWN + jobRun.getJobName() + CHECK_INITIALISE);
		}
		
		jobRun.setRequestTime(new Date());
		jobRun.setStatus(JobStatus.Scheduled);
		jobRun.setTerminologyServerUrl(terminologyServerUrl);
		jobRun.setWhiteList(job.getWhiteListConcepts(jobRun.getcodeSystemShortname()));
		LOGGER.info("Whitelisting {} concepts for {} in codeSystem {}", jobRun.getWhiteList().size(), jobRun.getJobName(), jobRun.getcodeSystemShortname());
		populateAuthenticationDetails(jobRun);
		
		//We protect the json from having parent links and redundant keys, 
		//but these are needed when saving to the database
		//Also reinforce the display order that is specified by the job
		boolean populateProject = StringUtils.isEmpty(jobRun.getProject());
		for (String parameterKey : jobRun.getParameters().keySet()) {
			jobRun.getParameters().get(parameterKey).setParentParams(jobRun.getParameters());
			jobRun.getParameters().get(parameterKey).setParamKey(parameterKey);
			JobParameter jobParam = job.getParameters().get(parameterKey);
			if (jobParam == null) {
				throw new BusinessServiceException(jobRun.getJobName() + " didn't expect user supplied parameter: '" + parameterKey + "'");
			} else {
				if (populateProject && parameterKey.equalsIgnoreCase("project")) {
					jobRun.setProject(jobRun.getParameters().get(parameterKey).getValue());
				}
				
				Integer displayOrder = job.getParameters().get(parameterKey).getDisplayOrder();
				if (displayOrder == null) {
					LOGGER.warn("{} parameter {} does not specify a display order", jobRun.getJobName(), parameterKey);
					displayOrder = 0;
				}
				jobRun.getParameters().get(parameterKey).setDisplayOrder(displayOrder);
			}
		}
		if (StringUtils.isEmpty(jobRun.getProject())) {
			jobRun.setProject("MAIN");
			LOGGER.warn("Failed to find Project parameter, defaulting to MAIN");
		}

		JobRun savedJobRun = jobRunRepository.save(jobRun);
		jobRun.setId(savedJobRun.getId());
		LOGGER.info("Running job: {}", jobRun);
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
		LOGGER.info("Sending request for metadata");
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
					LOGGER.error("Job already at status {}, ignoring response {}", existingStatus, jobRun);
					return;
				}
				//We need to grab the ID of the saved parameters objects 
				//so we can update the correct one in the db, otherwise we save a fresh copy
				jobRun.getParameters().setId(savedJob.get().getParameters().getId());
			}
			//We'll allow for the sender to have already truncated the debug info to the limit and added ...[truncated]
			jobRun.setDebugInfo(StringUtils.truncate(jobRun.getDebugInfo(), DEBUG_LENGTH_LIMIT + 15));
			LOGGER.info("Saving job response: {}", jobRun);
			jobRunRepository.save(jobRun);
		} catch (Exception e) {
			LOGGER.error("Unable to process response for jobRun '{}'", jobRun, e);
		}
	}

	@Override
	public void processMetadata(JobMetadata metadata) {
		LOGGER.info("Processing metadata for {} job types",metadata.getJobTypes().size());
		for (JobType jobType : metadata.getJobTypes()) {
			try {
				//Do we know about this jobType?
				JobType knownType = jobTypeRepository.findByName(jobType.getName());
				if (knownType == null) {
					jobTypeRepository.save(jobType);
				}
				
				//What categories are contained?
				LOGGER.info("Processing metadata for {} categories in type '{}'",jobType.getCategories().size(), jobType.getName());
				for (JobCategory jobCategory : jobType.getCategories()) {
					processJobCategory(jobCategory, jobType);
				}
			} catch (Exception e) {
				LOGGER.error("Unable to process metadata", e);
			}
		}
		LOGGER.info("Metadata processing complete");
	}

	private void processJobCategory(JobCategory jobCategory, JobType jobType) {
		//Do we know about this job category?
		String categoryName = jobCategory.getName();
		jobCategory.setType (jobType);
		JobCategory knownCategory = jobCategoryRepository.findByName(categoryName);
		if (knownCategory == null) {
			knownCategory = jobCategoryRepository.save(jobCategory);
		}

		LOGGER.info("Processing metadata for {} jobs in category '{}'",jobCategory.getJobs().size(), jobCategory.getName());
		for (Job job : jobCategory.getJobs()) {
			job.setCategory(knownCategory);

			//Do we know about this job already
			Job knownJob = jobRepository.findByName(job.getName());
			if (knownJob == null) {
				LOGGER.info("Saving job: {}", job);
			} else {
				job.setId(knownJob.getId());
				//Whitelists are maintained by schedule manager, so retain
				job.setWhiteListMap(knownJob.getWhiteListMap());
				LOGGER.info("Updating job: {}", job);
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
			LOGGER.info("Marking job as withdrawn/hidden: {}", withdrawnJob);
			withdrawnJob.setProductionStatus(Job.ProductionStatus.HIDEME);
			jobRepository.save(withdrawnJob);
		}
	}

	@Override
	public boolean deleteJobRun(String typeName, String jobName, UUID runId) {
		//Do we have this job?
		JobRun jobRun = getJobRun(typeName, jobName, runId);
		if (jobRun == null) {
			LOGGER.error("Unable to delete. JobRun with id {} not found.", runId);
			return false;
		}
		LOGGER.info("Deleting JobRun {}", jobRun);
		jobRunRepository.delete(jobRun);
		return true;
	}

	@Override
	public Set<WhiteListedConcept> getWhiteList(String typeName, String codeSystemShortname, String jobName) throws ResourceNotFoundException {
		//Do we know about this job?
		Job job = getJob(jobName);
		if (job == null) {
			throw new ResourceNotFoundException(JOB_UNKNOWN + jobName + CHECK_INITIALISE);
		}
		return job.getWhiteListConcepts(codeSystemShortname);
	}

	@Override
	public void setWhiteList(String typeName, String jobName, String codeSystemShortname, Set<WhiteListedConcept> whiteListConcepts) throws ResourceNotFoundException {
		//Do we know about this job?
		Job job = getJob(jobName);
		if (job == null) {
			throw new ResourceNotFoundException(JOB_UNKNOWN + jobName + CHECK_INITIALISE);
		}
		
		WhiteList whiteList = null;
		if (whiteListConcepts == null || whiteListConcepts.isEmpty()) {
			LOGGER.info("Removing all whitelisted concepts for job: {}", jobName);
		} else {
			LOGGER.info("Whitelisting {} concepts for job: {}", whiteListConcepts.size(), jobName);
			//If this job doesn't have a whitelist for this code system then we'll need to save it first so
			//that it has an identifier - I'm sure this can be improved, hibernate should take care of this
			whiteList = job.getWhiteList(codeSystemShortname);
			if (whiteList == null) {
				whiteList = new WhiteList(codeSystemShortname, null);
				whiteList = whiteListRepository.save(whiteList);
				LOGGER.info("Provisional save of whitelist {}", whiteList.getId());
				whiteList.setConcepts(whiteListConcepts);
			} else {
				whiteList.getConcepts().retainAll(whiteListConcepts);
				whiteList.getConcepts().addAll(whiteListConcepts);
			} 
			//To keep hibernate happy, we need to tell each concept in this list about its parent
			for (WhiteListedConcept whiteListedConcept : whiteListConcepts) {
				whiteListedConcept.setWhiteList(whiteList);
			}
		}
		job.setWhiteList(codeSystemShortname, whiteList);
		jobRepository.save(job);
	}

	@Override
	public Page<JobRun> listAllJobsRun(Set<JobStatus> statusFilter, Integer sinceMins, Pageable pageable) {
		if (statusFilter == null && sinceMins == null) {
			throw new IllegalArgumentException("Either statusFilter or sinceMins must be specified.");
		}
		
		Date sinceDate = null;
		if (sinceMins != null) {
			sinceDate = new Date(System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(sinceMins));
		}
		
		if (statusFilter != null) {
			if (sinceMins == null) {
				return jobRunRepository.findByStatus(statusFilter, pageable);
			} else {
				return jobRunRepository.findByStatusSinceDate(sinceDate, statusFilter, pageable);
			}
		} else {
			return jobRunRepository.findSinceDate(sinceDate, pageable);
		}
	}

	@Override
	public int clearStuckJobs() {
		int jobsCleared = 0;
		Set<JobStatus> stuckStatuses = Set.of(JobStatus.Scheduled, JobStatus.Running);
		for (JobRun jobRun : jobRunRepository.findAllByStatus(stuckStatuses)) {
			if (jobRun.getRequestTime().before(new Date(System.currentTimeMillis() - TimeUnit.HOURS.toMillis(STUCK_JOB_HOURS)))) {
				LOGGER.warn("JobRun {} has been stuck in {} status for over {} hours.  Marking as failed.", jobRun, jobRun.getStatus(), STUCK_JOB_HOURS);
				jobRun.setStatus(JobStatus.Failed);
				jobRun.setDebugInfo("Job status manually updated to 'failed'");
				jobRunRepository.save(jobRun);
				jobsCleared++;
			}
		}
		return jobsCleared;
	}

}
