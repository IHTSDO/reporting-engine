package org.ihtsdo.authoring.scheduler.api.service;

import org.ihtsdo.authoring.scheduler.api.repository.JobParametersRepository;
import org.ihtsdo.authoring.scheduler.api.repository.JobRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobParameters;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobRunParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UpdateProcessor {

	private static final Logger LOGGER = LoggerFactory.getLogger(UpdateProcessor.class);

	private final JobRunRepository jobRunRepository;
	private final JobParametersRepository jobParametersRepository;

	@Autowired
	public UpdateProcessor(JobRunRepository jobRunRepository, JobParametersRepository jobParametersRepository) {
		this.jobRunRepository = jobRunRepository;
		this.jobParametersRepository = jobParametersRepository;
	}

	@Transactional
	public void updateJobRunParameters(JobParameters jobParameters) {
		List<JobRun> jobRuns = jobRunRepository.findJobRunsByParametersId(jobParameters.getId());

		JobRun originalJobRun = jobRuns.removeFirst();

		LOGGER.info(
				"Keeping parameters with id {} attached to original job run {} and decoupling {} duplicate job runs",
				jobParameters.getId(),
				originalJobRun.getId(),
				jobRuns.size()
		);

		for (JobRun jobRun : jobRuns) {
			JobRunParameters newJobParameters = new JobRunParameters(jobParameters);
			JobRunParameters savedJobParameters = jobParametersRepository.save(newJobParameters);

			int updated = jobRunRepository.updateParameters(jobRun.getId(), savedJobParameters.getId());

			if (updated != 1) {
				throw new IllegalStateException("Expected to update one JobRun but updated " + updated + " for " + jobRun.getId());
			}
		}
	}
}
