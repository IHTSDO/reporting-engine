package org.ihtsdo.authoring.scheduler.api.rest.tools;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.authoring.scheduler.api.repository.JobRepository;
import org.ihtsdo.authoring.scheduler.api.repository.JobRunBatchRepository;
import org.ihtsdo.authoring.scheduler.api.repository.JobRunRepository;
import org.ihtsdo.authoring.scheduler.api.service.ScheduleService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AllReportRunner {
	private static final Logger LOGGER = LoggerFactory.getLogger(AllReportRunner.class);
	public static final String ECL_PARAMETER_NAME = "ECL";
	public static final String DEFAULT_ECL_VALUE = "*";

	@Autowired
	private JobRunRepository jobRunRepository;

	@Autowired
	private JobRepository jobRepository;

	@Autowired
	private JobRunBatchRepository jobRunBatchRepository;

	@Autowired
	private ScheduleService scheduleService;

	public JobRunBatch runAllReports(boolean dryRun, boolean international, boolean managedService, String projectName, String userName, String authToken) {
		List<Job> listOfJobs = jobRepository.findAllNotHidden();
		LOGGER.info("{} {} reports for user '{}' [INT={}, MS={}, Project={}]",
				dryRun ? "Dry run of" : "Scheduling",
				listOfJobs.size(),
				userName,
				international,
				managedService,
				projectName);

		JobRunBatch runBatch = new JobRunBatch(international, managedService, projectName, userName);

		if (!dryRun) {
			jobRunBatchRepository.save(runBatch);
		}

		for (Job job : listOfJobs) {
			if ((international && job.getTags().contains("INT")) || (managedService && job.getTags().contains("MS"))) {
				createReportJobAndRunIt(job, runBatch, projectName, userName, authToken, dryRun);
			}
		}

		return runBatch;
	}

	private void createReportJobAndRunIt(Job job, JobRunBatch runBatch, String projectName, String userName, String authToken, boolean dryRun) {
		String jobName = job.getName();
		Optional<JobRun> jobRun = jobRunRepository.findLastRunByJobName(jobName);
		JobRun reRunJob;

		if (jobRun.isPresent()) {
			reRunJob = jobRun.get().cloneForRerun();
		} else {
			LOGGER.warn("Unable to find job history to run, so creating new : '{}'", jobName);
			reRunJob = JobRun.create(jobName, userName);
		}

		checkAndUpdateEclParameterIfBlank(jobName, reRunJob, userName);
		reRunJob.setAuthToken(authToken);

		if (StringUtils.isNotEmpty(projectName)) {
			reRunJob.setProject(projectName);
		}

		reRunJob.setBatch(runBatch);

		if (dryRun) {
			LOGGER.info("Dry run of report job : '{}'", jobName);
			return;
		}

		runTheReport(jobName, reRunJob);
	}

	private void runTheReport(String jobName, JobRun reRunJob) {
		try {
			JobRun result = scheduleService.runJob(reRunJob);
			LOGGER.info("Scheduling report job : '{}'", jobName);
			new AllReportRunnerResult(jobName, JobStatus.Scheduled, result.getId());
		} catch (BusinessServiceException e) {
			LOGGER.error("Error running report job : '{}'", jobName);
			new AllReportRunnerResult(jobName, JobStatus.Failed, e.getMessage());
		}
	}

	private void checkAndUpdateEclParameterIfBlank(String jobName, JobRun reRunJob, String userName) {
		reRunJob.setUser(userName);
		JobRunParameters paramMap = reRunJob.getParameters();

		for (Map.Entry<String, JobParameter> entry : paramMap.getParameterMap().entrySet()) {
			JobParameter jobParameter = entry.getValue();
			String parameterName = entry.getKey().trim();

			if (parameterName.equalsIgnoreCase(ECL_PARAMETER_NAME) && jobParameter.getValue() == null) {
				LOGGER.warn("Setting ECL parameter to '*' for job '{}'", jobName);
				jobParameter.setValue(DEFAULT_ECL_VALUE);
			}
		}
	}
}
