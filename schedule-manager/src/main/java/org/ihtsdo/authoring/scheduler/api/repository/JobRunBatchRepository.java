package org.ihtsdo.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobRunBatch;
import org.springframework.data.domain.Limit;
import org.springframework.data.repository.CrudRepository;

public interface JobRunBatchRepository extends CrudRepository<JobRunBatch, Long> {
    Iterable<JobRunBatch> findByOrderByIdDesc(Limit of);
}
