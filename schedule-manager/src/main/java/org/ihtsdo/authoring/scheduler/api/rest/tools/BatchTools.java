package org.ihtsdo.authoring.scheduler.api.rest.tools;

import org.ihtsdo.authoring.scheduler.api.repository.JobRunBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobRunBatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

@Service
public class BatchTools {
    private static final Logger LOGGER = LoggerFactory.getLogger(BatchTools.class);

    @Autowired
    private JobRunBatchRepository jobRunBatchRepository;

    public Iterable<JobRunBatch> getLastNBatches(Long limit) {
        Iterable<JobRunBatch> batches;
        LOGGER.info("Get Last N Batches: {}", limit);

        if (limit == null || limit < 1) {
            batches = jobRunBatchRepository.findByOrderByIdDesc(Limit.unlimited());
        } else {
            batches = jobRunBatchRepository.findByOrderByIdDesc(Limit.of(limit.intValue()));
        }

        // Can't use JsonIgnore annotation, as we need the json in some situations (ActivMQ),
        // so clear the data we don't want, to stop any infinite recursion!
        batches.forEach(batch -> batch.setJobRuns(null));

        return batches;
    }

    public JobRunBatch getBatch(Long id) {
        JobRunBatch batch = jobRunBatchRepository.findById(id).orElse((null));

        if (batch == null) {
            LOGGER.warn("Get Batch: {} (is not present)", id);
            return null;
        }

        LOGGER.info("Get Batch: {} (is present)", id);

        // Can't use JsonIgnore annotation, as we need the json in some situations (ActivMQ),
        // so clear the data we don't want, to stop any infinite recursion!
        for (JobRun jobRun : batch.getJobRuns()) {
            jobRun.setBatch(null);
        }

        return batch;
    }
}
