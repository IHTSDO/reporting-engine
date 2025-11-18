package org.ihtsdo.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface JobRunRepository extends CrudRepository<JobRun, UUID> {

	Optional<JobRun> findById(UUID id);
	
	Page<JobRun> findByJobNameAndProjectInOrderByRequestTimeDesc(String jobName, Set<String> projects, Pageable pageable);
	
	Page<JobRun> findByJobNameAndUserAndProjectInOrderByRequestTimeDesc(String jobName, String user, Set<String> projects, Pageable pageable);

	@Query("SELECT jr FROM JobRun jr WHERE (requestTime > ?1 OR resultTime > ?1) ")
	Page<JobRun> findSinceDate(Date sinceDate, Pageable pageable);
	
	@Query("SELECT jr FROM JobRun jr WHERE status IN (?1)")
	Page<JobRun> findByStatus(Set<JobStatus> statusFilter, Pageable pageable);

	@Query("SELECT jr FROM JobRun jr WHERE status IN (?1)")
	List<JobRun> findAllByStatus(Set<JobStatus> statusFilter);

	@Query("SELECT jr FROM JobRun jr WHERE (requestTime > ?1 OR resultTime > ?1) " +
	" AND status IN (?2)")
	Page<JobRun> findByStatusSinceDate(Date sinceDate, Set<JobStatus> statusFilter, Pageable pageable);

	@Query(nativeQuery=true, value="SELECT * FROM job_run WHERE result_url IS NOT NULL AND job_name = :jobName AND status = 3 ORDER BY parameters_id DESC LIMIT 1")
	Optional<JobRun> findLastRunByJobName(@Param("jobName") String jobName);

	@Query(nativeQuery=true, value="SELECT * FROM job_run WHERE run_batch_id = :runBatchId")
	List<JobRun> findByRunBatchId(@Param("runBatchId")Long runBatchId);
}
