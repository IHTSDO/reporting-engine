package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.util.*;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.stereotype.Service;

@Service
public interface ScheduleService {

	public List<JobType> listJobTypes();

	public List<JobCategory> listJobTypeCategories(String typeName) throws BusinessServiceException;

	public Job getJob(String jobName);
	
	public List<JobRun> listJobsRun(String typeName, String jobName, String user);

	public JobRun runJob(String jobType, String jobName, JobRun jobRun) throws BusinessServiceException;

	public JobRun runJob(JobRun jobRun) throws BusinessServiceException;

	public JobSchedule scheduleJob(String jobType, String jobName, JobSchedule jobSchedule);

	public void deleteSchedule(String jobType, String jobName, UUID scheduleId);

	public JobRun getJobRun(String typeName, String jobName, UUID runId);

	public void initialise();

	public void processResponse(JobRun jobRun);

	public void processMetadata(JobMetadata metadata);

	public boolean deleteJobRun(String typeName, String jobName, UUID runId);

	public Set<WhiteListedConcept> getWhiteList(String typeName, String jobName);

	public void setWhiteList(String typeName, String jobName, Set<WhiteListedConcept> whiteList);

	public List<JobRun> listJobsRun(JobRunListRequest listRequest);

}
