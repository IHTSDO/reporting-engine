package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.util.List;
import java.util.UUID;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.stereotype.Service;

@Service
public interface ScheduleService {

	public List<JobType> listJobTypes();

	public List<JobCategory> listJobTypeCategories(String typeName);

	public Job getJob(String typeName, String jobName);
	
	public List<JobRun> listJobsRun(String typeName, String jobName, String user);

	public JobRun runJob(String jobType, String jobName, JobRun jobRun) throws BusinessServiceException;

	public JobSchedule scheduleJob(String jobType, String jobName, JobSchedule jobSchedule);

	public void deleteSchedule(String jobType, String jobName, String scheduleId);

	public JobRun getJobRun(String typeName, String jobName, UUID runId);

}
