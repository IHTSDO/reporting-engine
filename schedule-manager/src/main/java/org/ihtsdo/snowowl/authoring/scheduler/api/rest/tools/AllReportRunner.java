package org.ihtsdo.snowowl.authoring.scheduler.api.rest.tools;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.scheduler.api.repository.JobRepository;
import org.ihtsdo.snowowl.authoring.scheduler.api.repository.JobRunRepository;
import org.ihtsdo.snowowl.authoring.scheduler.api.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AllReportRunner {
	private static final Logger LOG = LoggerFactory.getLogger(AllReportRunner.class);
	public static final String ECL_PARAMETER_NAME = "ECL";
	public static final String DEFAULT_ECL_VALUE = "*";

	@Autowired
	private JobRunRepository jobRunRepository;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private ScheduleService scheduleService;

	public List<AllReportRunnerResult> runAllReports(boolean dryRun, String userName, String authToken) {
		List<AllReportRunnerResult> allReportRunnerResults = new ArrayList<>();
		List<Job> listOfJobs = jobRepository.findAll();
		LOG.info("{} {} reports for user '{}'", dryRun ? "Dry run of" : "Scheduling", listOfJobs.size(), userName);

		for (Job job : listOfJobs) {
			allReportRunnerResults.add(createReportJobAndRunIt(job, userName, authToken, dryRun));
		}

		return allReportRunnerResults;
	}

	private AllReportRunnerResult createReportJobAndRunIt(Job job, String userName, String authToken, boolean dryRun) {
		String jobName = job.getName();
		Optional<JobRun> jobRun = jobRunRepository.findLastRunByJobName(jobName);
		JobRun reRunJob;

		if (jobRun.isPresent()) {
			reRunJob = jobRun.get().cloneForRerun();
		} else {
			LOG.warn("Unable to find job history to run, so creating new : '{}'", jobName);
			reRunJob = JobRun.create(jobName, userName);
		}

		checkAndUpdateEclParameterIfBlank(jobName, reRunJob, userName);
		reRunJob.setAuthToken(authToken);

		if (dryRun) {
			LOG.info("Dry run of report job : '{}'", jobName);
			return new AllReportRunnerResult(jobName);
		}

		return runTheReport(jobName, reRunJob);
	}

	private AllReportRunnerResult runTheReport(String jobName, JobRun reRunJob) {
		try {
			JobRun result = scheduleService.runJob(reRunJob);
			LOG.info("Scheduling report job : '{}'", jobName);
			return new AllReportRunnerResult(jobName, JobStatus.Scheduled, result.getId());
		} catch (BusinessServiceException e) {
			LOG.error("Error running report job : '{}'", jobName);
			return new AllReportRunnerResult(jobName, JobStatus.Failed, e.getMessage());
		}
	}

	private void checkAndUpdateEclParameterIfBlank(String jobName, JobRun reRunJob, String userName) {
		reRunJob.setUser(userName);
		JobRunParameters paramMap = reRunJob.getParameters();

		for (Map.Entry<String, JobParameter> entry : paramMap.getParameterMap().entrySet()) {
			JobParameter jobParameter = entry.getValue();
			String parameterName = entry.getKey().trim();

			if (parameterName.equalsIgnoreCase(ECL_PARAMETER_NAME) && jobParameter.getValue() == null) {
				LOG.warn("Setting ECL parameter to '*' for job '{}'", jobName);
				jobParameter.setValue(DEFAULT_ECL_VALUE);
			}
		}
	}
}
