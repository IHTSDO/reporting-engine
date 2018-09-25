package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.util.*;

import javax.annotation.PostConstruct;

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
		return new ArrayList<>();
	}

	@Override
	public List<JobCategory> listJobTypeCategories(String typeName) {
		return new ArrayList<>();
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
			return jobRunRepository.findByJobName(jobName);
		}
	}

	@Override
	public JobRun runJob(String jobType, String jobName, JobRun jobRun) throws BusinessServiceException {
		//Make sure we know what this job is before we run it!
		Job job = getJob(jobName);
		if (job == null) {
			throw new BusinessServiceException("Unknown job : '" + jobType + "/" + jobName);
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
		populateAuthenticationToken(jobRun);
		
		jobRunRepository.save(jobRun);
		logger.info("Running {} for {} - {} ", jobRun.getJobName(), jobRun.getUser(), jobRun.getId());
		transmitter.send(jobRun);
		return jobRun;
	}

	private void populateAuthenticationToken(JobRun jobRun) {
		//Can we get a token from our security context?
		String token = SecurityUtil.getAuthenticationToken();
		if (token == null) {
			token = authenticationService.getSystemAuthorisation();
		}
		jobRun.setAuthToken(token);
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
			logger.info("Received job response: {}", jobRun);
			jobRunRepository.save(jobRun);
		} catch (Exception e) {
			logger.error("Unable to process response for jobRun '{}'", jobRun, e);
		}
	}

	@Override
	public void processMetadata(JobMetadata metadata) {
		logger.info("Processing metadata for {} jobs",metadata.getJobs().size());
		for (Job job : metadata.getJobs()) {
			try {
				//Do we know about this job category?
				JobCategory jobCategory = job.getCategory();
				JobCategory knownCategory = jobCategoryRepository.findByName(jobCategory.getName());
				if (knownCategory == null) {
					//Default all jobs to be types of reports for the moment
					if (jobCategory.getType() == null) {
						JobType type = new JobType(JobType.REPORT);
						type = jobTypeRepository.save(type);
						jobCategory.setType (type);
					}
					knownCategory = jobCategoryRepository.save(jobCategory);
				}
				job.setCategory(knownCategory);
				
				//Do we know about this job already
				Job knownJob = jobRepository.findByName(job.getName());
				if (knownJob == null) {
					logger.info("Saving job: " + job);
					jobRepository.save(job);
				} else {
					job.setId(knownJob.getId());
					logger.info("Updating job: " + job);
					jobRepository.save(job);
				}
			} catch (Exception e) {
				logger.error("Unable to process metadata for job '{}'", job, e);
			}
		}
	}
	
}
