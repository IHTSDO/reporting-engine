package org.ihtsdo.authoring.scheduler.api.rest.tools;

import org.ihtsdo.authoring.scheduler.api.repository.JobRunBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRunBatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import java.util.Optional;

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

        return batches;
    }

    public JobRunBatch getBatch(Long id) {
        Optional<JobRunBatch> batch = jobRunBatchRepository.findById(id);
        LOGGER.info("Get Batch: {} (is present {})", id, batch.isPresent());
        return batch.orElse(null);
    }
}
