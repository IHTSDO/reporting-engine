package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.util.*;

import javax.annotation.PostConstruct;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.scheduler.api.mq.Transmitter;
import org.ihtsdo.snowowl.authoring.scheduler.api.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.Trigger;
import org.springframework.scheduling.support.CronTrigger;

public class ScheduleServiceImpl implements ScheduleService {
	
	@Autowired
	JobRunRepository jobRunRepository;
	
	@Autowired
	JobRepository jobRepository;
	
	@Autowired
	JobScheduleRepository jobScheduleRepository;
	
	@Autowired
	TaskScheduler engineScheduler;
	
	@Autowired
	Transmitter transmitter;
	
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
			throw new BusinessServiceException("Unknown job: '" + jobRun.getJobName() +"'");
		}
		jobRun.setId(UUID.randomUUID());
		jobRun.setRequestTime(new Date());
		jobRun.setStatus(JobStatus.Scheduled);
		logger.info("Running {} for {} - {} ", jobRun.getJobName(), jobRun.getUser(), jobRun.getId());
		jobRunRepository.save(jobRun);
		transmitter.send(jobRun);
		return jobRun;
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
	public Optional<JobRun> getJobRun(String typeName, String jobName, UUID runId) {
		return jobRunRepository.findById(runId);
	}

	@Override
	public void initialise() {
		// TODO Auto-generated method stub
		
	}
	
}
