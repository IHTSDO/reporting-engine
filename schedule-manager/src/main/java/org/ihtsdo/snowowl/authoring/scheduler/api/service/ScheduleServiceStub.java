package org.ihtsdo.snowowl.authoring.scheduler.api.service;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.snomed.otf.scheduler.domain.*;

public class ScheduleServiceStub implements ScheduleService {
	
	private static final String TYPE_REPORT = "Report";
	private static final String JOB_CS = "Case Sensitivity";
	private static final String JOB_IA = "Initial Analysis";
	private static final String JOB_AAP = "Attributes as Parents";
	private URL resultURL;
	
	Map<String, JobType> jobTypes = new HashMap<>();
	Map<Job, List<JobRun>> jobRuns = new HashMap<>();
	
	public ScheduleServiceStub() throws MalformedURLException {
		resultURL = new URL ("https://docs.google.com/spreadsheets/d/1OkNqnFmjNhe5IOcoCmK4P3-1uEHqWO0Xf1mq0mya-PE/edit");
		createDummyData();
	}

	@Override
	public List<JobType> listJobTypes() {
		return jobTypes.values().stream().collect(Collectors.toList()); 
	}

	@Override
	public List<JobCategory> listJobTypeCategories(String typeName) {
		if (jobTypes.containsKey(typeName)) {
			return jobTypes.get(typeName).getCategories();
		}
		return null;
	}

	@Override
	public Job getJob(String typeName, String jobName) {
		for (JobCategory category : listJobTypeCategories(typeName)) {
			for (Job job : category.getJobs()) {
				if (job.getName().equals(jobName)) {
					return job;
				}
			}
		}
		return null;
	}

	@Override
	public List<JobRun> listJobsRun(String typeName, String jobName, String user) {
		Job job = getJob(typeName, jobName);
		//Are we filtering?
		if (user == null) {
			return jobRuns.get(job);
		} 
		
		List<JobRun> filteredRuns = new ArrayList<>();
		for (JobRun run : jobRuns.get(job)) {
			if (run.getUser().equals(user)) {
				filteredRuns.add(run);
			}
		}
		return filteredRuns;
	}

	@Override
	public JobRun runJob(String jobType, String jobName, JobRun jobRun) throws BusinessServiceException {
		//Make sure we know what this job is before we run it!
		Job job = getJob(jobType, jobName);
		if (job == null) {
			throw new BusinessServiceException("Unknown job : " + jobType + "/" + jobName);
		}
		jobRun.setId(UUID.randomUUID());
		jobRun.setRequestTime(new Date());
		jobRun.setStatus(JobStatus.Scheduled);
		List<JobRun> runs = jobRuns.get(job);
		if (runs == null) {
			runs = new ArrayList<>();
			jobRuns.put(job, runs);
		}
		runs.add(jobRun);
		return jobRun;
	}

	@Override
	public JobSchedule scheduleJob(String jobType, String jobName, JobSchedule jobSchedule) {
		jobSchedule.setId(UUID.randomUUID());
		return jobSchedule;
	}

	@Override
	public void deleteSchedule(String jobType, String jobName, String scheduleId) {
		return;
	}

	@Override
	public JobRun getJobRun(String typeName, String jobName, UUID runId) {
		for (JobRun run : listJobsRun(typeName, jobName, null)) {
			if (run.getId().equals(runId)) {
				//Complete all running jobs
				run.setResult(resultURL);
				run.setStatus(JobStatus.Complete);
				if (run.getResultTime()==null) {
					run.setResultTime(new Date());
				}
				return run;
			}
		}
		return null;
	}

	
	/***********************  DUMMY DATA *************************/

	private void createDummyData() {
		try {
			createJobs();
			createRuns();
		} catch (Exception e) {
			throw new IllegalStateException("Unable to set up schedule service dummy data", e);
		}
	}
	
	private void createJobs() {
		String[] params = new String[] { "subHierarchy", "project" };
		
		Job qiReport = new Job (JOB_IA, "Produces tabs to show intermediate primitives and counts for attribute type occurrance.", params);
		JobCategory qiReports = new JobCategory("Quality Improvement");
		qiReports.addJob(qiReport);
		
		JobCategory qaReports = new JobCategory("General QA");
		Job csReport = new Job (JOB_CS, "Produces a list of terms which appear to have an incorrect case sensitivity setting.", params);
		
		params = new String[] { "AttributeType", "project" };
		Job aapReport = new Job (JOB_AAP, "For a given attribute type, produces a list of concepts which have the same concept as an attribute value and parent. For example, 'Is Modification Of'.", params);
		qaReports.addJob(csReport);
		qaReports.addJob(aapReport);
		
		JobType reports = new JobType(TYPE_REPORT);
		reports.addCategory(qaReports);
		reports.addCategory(qiReports);
		jobTypes.put(reports.getName(), reports);
	}
	
	private void createRuns() throws MalformedURLException  {
		
		JobRun scheduledJob = JobRun.create(JOB_CS, "system");
		scheduledJob.setStatus(JobStatus.Scheduled);
		populateParameter(scheduledJob, "subHierarchy", "105590001 |Substance (substance)|");
		populateParameter(scheduledJob, "Project", "SUBST2019");
		
		JobRun completeJob = JobRun.create(JOB_CS, "system");
		completeJob.setStatus(JobStatus.Complete);
		completeJob.setResult(new URL("https://docs.google.com/spreadsheets/d/1OkNqnFmjNhe5IOcoCmK4P3-1uEHqWO0Xf1mq0mya-PE/edit"));
		populateParameter(completeJob, "SubHierarchy", "105590001 |Substance (substance)|");
		populateParameter(completeJob, "Project", "SUBST2019");
		
		List <JobRun> csRuns = new ArrayList<>();
		csRuns.add(scheduledJob);
		csRuns.add(completeJob);
		Job csJob = getJob(TYPE_REPORT, JOB_CS);
		jobRuns.put(csJob, csRuns);
		
		JobRun completeQIJob = JobRun.create(JOB_IA, "jcase");
		completeQIJob.setStatus(JobStatus.Complete);
		completeQIJob.setResult(new URL("https://docs.google.com/spreadsheets/d/1HMtHqUaIP-DTKbt-7Jae8lrCf9SzP7QlK8DDwu00nZ8/edit#gid=0"));
		populateParameter(completeQIJob, "Project", "QI2018");
		
		List <JobRun> iaRuns = new ArrayList<>();
		iaRuns.add(completeQIJob);
		Job iaJob = getJob(TYPE_REPORT, JOB_IA);
		jobRuns.put(iaJob, iaRuns);
		
		JobRun completeAAPJob = JobRun.create(JOB_AAP, "tmorrison");
		completeAAPJob.setStatus(JobStatus.Complete);
		completeAAPJob.setResult(new URL("https://docs.google.com/spreadsheets/d/1pXOQNEnSnSra2nISCG9eGcfsHuLewfjEWqUiSLz-6b4/edit"));
		populateParameter(completeAAPJob, "AttributeType", "738774007 |Is modification of (attribute)|");
		
		List <JobRun> aapRuns = new ArrayList<>();
		aapRuns.add(completeAAPJob);
		Job aapJob = getJob(TYPE_REPORT, JOB_AAP);
		jobRuns.put(aapJob, aapRuns);
	}

	private void populateParameter(JobRun jobRun, String key, String value) {
		List<JobParameter> params = jobRun.getParameters();
		if (params == null) {
			params = new ArrayList<>();
			jobRun.setParameters(params);
		}
		JobParameter param = new JobParameter();
		param.setKey(key);
		param.setValue(value);
		params.add(param);
	}
}
