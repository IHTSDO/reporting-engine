package org.ihtsdo.snowowl.authoring.scheduler.api.rest.tools;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.scheduler.api.repository.JobRepository;
import org.ihtsdo.snowowl.authoring.scheduler.api.repository.JobRunRepository;
import org.ihtsdo.snowowl.authoring.scheduler.api.service.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.Job;
import org.snomed.otf.scheduler.domain.JobParameter;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobRunParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class AllReportRunner {
    private static final Logger LOG = LoggerFactory.getLogger(AllReportRunner.class);
    public static final String ECL_PARAMETER_NAME = "ECL";

    @Autowired
    private JobRunRepository jobRunRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ScheduleService scheduleService;

    public static final String DEFAULT_ECL_VALUE = "*";

    private Boolean allReportRunnerIsEnabled;

    private String allReportRunnerUserName;

    public List<AllReportRunnerResult> runAllReports(String username, boolean enabled) {
        allReportRunnerIsEnabled = enabled;
        allReportRunnerUserName = username;

        List<AllReportRunnerResult> allReportRunnerResults = new ArrayList<>();
        List<Job> listOfJobs = jobRepository.findAll();
        LOG.info("Run {} reports ({}ENABLED) for user '{}'", listOfJobs.size(), allReportRunnerIsEnabled ? "" : "NOT ", allReportRunnerUserName);

        for (Job job : listOfJobs) {
            allReportRunnerResults.add(createReportJobAndRunIt(job));
        }

        return allReportRunnerResults;
    }

    private AllReportRunnerResult createReportJobAndRunIt(Job job) {
        String jobName = job.getName();
        Optional<JobRun> jobRun = jobRunRepository.findLastRunByJobName(jobName);

        if (jobRun.isEmpty()) {
            LOG.error("Unable to find job history to run : '{}'", jobName);
            return new AllReportRunnerResult(jobName, "ERROR", "Unable to find job history");
        }

        JobRun lastJobRun = jobRun.get();
        checkAndUpdateEclParameterIfBlank(jobName, lastJobRun);

        if (allReportRunnerIsEnabled) {
            return runTheReport(jobName, lastJobRun);
        }

        LOG.info("Dry run of report job : '{}'", jobName);
        return new AllReportRunnerResult(jobName, "OK", "Dry run");
    }

    private AllReportRunnerResult runTheReport(String jobName, JobRun lastJobRun) {
        try {
            JobRun result = scheduleService.runJob(lastJobRun);
            LOG.info("Running report job : '{}'", jobName);
            return new AllReportRunnerResult(jobName, "OK", "Running", result.getId());
        } catch (BusinessServiceException e) {
            LOG.error("Error running report job : '{}'", jobName);
            return new AllReportRunnerResult(jobName, "ERROR", e.getMessage());
        }
    }

    private void checkAndUpdateEclParameterIfBlank(String jobName, JobRun lastJobRun) {
        lastJobRun.setUser(allReportRunnerUserName);
        JobRunParameters paramMap = lastJobRun.getParameters();

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
