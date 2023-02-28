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
    public static final String DEFAULT_ECL_VALUE = "*";

    @Autowired
    private JobRunRepository jobRunRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private ScheduleService scheduleService;

    private Boolean dryRun;
    private String userName;

    public List<AllReportRunnerResult> runAllReports(boolean dryRun, String userName) {
        this.dryRun = dryRun;
        this.userName = userName;

        List<AllReportRunnerResult> allReportRunnerResults = new ArrayList<>();
        List<Job> listOfJobs = jobRepository.findAll();
        LOG.info("Running {} reports ({}ENABLED) for user '{}'", listOfJobs.size(), this.dryRun ? "" : "NOT ", userName);

        for (Job job : listOfJobs) {
            allReportRunnerResults.add(createReportJobAndRunIt(job));
        }

        return allReportRunnerResults;
    }

    private AllReportRunnerResult createReportJobAndRunIt(Job job) {
        String jobName = job.getName();
        Optional<JobRun> jobRun = jobRunRepository.findLastRunByJobName(jobName);
        JobRun lastJobRun;

        if (jobRun.isPresent()) {
            lastJobRun = jobRun.get();
        } else {
            LOG.warn("Unable to find job history to run, so creating new : '{}'", jobName);
            lastJobRun = JobRun.create(jobName, userName);
        }

        checkAndUpdateEclParameterIfBlank(jobName, lastJobRun);

        if (dryRun) {
            LOG.info("Dry run of report job : '{}'", jobName);
            return new AllReportRunnerResult(jobName, "OK", "Dry run");
        }

        return runTheReport(jobName, lastJobRun);
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
        lastJobRun.setUser(userName);
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
