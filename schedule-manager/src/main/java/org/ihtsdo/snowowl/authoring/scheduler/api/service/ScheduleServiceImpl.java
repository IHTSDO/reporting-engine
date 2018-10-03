package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.util.*;

import javax.annotation.PostConstruct;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
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
			throw new BusinessServiceException("Unknown jobType : '" + typeName + "'");
		}
	}

	@Override
	public Job getJob(String jobName) {
		return jobRepository.findByName(jobName);
	}

	@Override
	public List<JobRun> listJobsRun(String typeName, String jobName, String user) {
		if (user != null && !user.isEmpty()) {
			return jobRunRepository.findByJobNameAndUser(jobName, user);
		} else {
			return jobRunRepository.findByJobNameOrderByRequestTimeDesc(jobName);
		}
	}

	@Override
	public JobRun runJob(String jobType, String jobName, JobRun jobRun) throws BusinessServiceException {
		//Make sure we know what this job is before we run it!
		Job job = getJob(jobName);
		if (job == null) {
			throw new BusinessServiceException("Unknown job : '" + jobType + "/" + jobName + "'");
		}
		return runJob(jobRun);
	}
	
	public JobRun runJob(JobRun jobRun) throws BusinessServiceException {
		//Do we know about this job?
		Job job = getJob(jobRun.getJobName());
		if (job == null) {
			throw new BusinessServiceException("Job unknown to Schedule Service: '" + jobRun.getJobName() +"' If job exists and is active, re-run initialise.");
		}
		
		jobRun.setRequestTime(new Date());
		jobRun.setStatus(JobStatus.Scheduled);
		jobRun.setTerminologyServerUrl(terminologyServerUrl);
		populateAuthenticationDetails(jobRun);
		
		jobRun = jobRunRepository.save(jobRun);
		logger.info("Running job {}", jobRun);
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
	public void processResponse(JobRun jobRun) {
		try {
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
					JobCategory knownCategory = jobCategoryRepository.findByName(categoryName);
					if (knownCategory == null) {
						jobCategory.setType (jobType);
						knownCategory = jobCategoryRepository.save(jobCategory);
					}
					
					logger.info("Processing metadata for {} jobs in category '{}'",jobCategory.getJobs().size(), jobCategory.getName());
					for (Job job : jobCategory.getJobs()) {
						//Do we know about this job already
						Job knownJob = jobRepository.findByName(job.getName());
						if (knownJob == null) {
							logger.info("Saving job: " + job);
						} else {
							job.setId(knownJob.getId());
							logger.info("Updating job: " + job);
						}
						job.setCategory(knownCategory);
						jobRepository.save(job);
					}
				}
			} catch (Exception e) {
				logger.error("Unable to process metadata", e);
			}
		}
	}
	
}
