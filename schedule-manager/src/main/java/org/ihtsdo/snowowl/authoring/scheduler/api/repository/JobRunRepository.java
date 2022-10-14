package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.scheduler.domain.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.*;

public interface JobRunRepository extends CrudRepository<JobRun, UUID> {

	Optional<JobRun> findById(UUID id);
	
	Page<JobRun> findByJobNameAndProjectInOrderByRequestTimeDesc(String jobName, Set<String> projects, Pageable pageable);
	
	Page<JobRun> findByJobNameAndUserAndProjectInOrderByRequestTimeDesc(String jobName, String user, Set<String> projects, Pageable pageable);

	@Query("SELECT jr FROM JobRun jr WHERE (requestTime > ?1 OR resultTime > ?1) ")
	Page<JobRun> findSinceDate(Date sinceDate, Pageable pageable);
	
	@Query("SELECT jr FROM JobRun jr WHERE status IN (?1)")
	Page<JobRun> findByStatus(Set<JobStatus> statusFilter, Pageable pageable);

	@Query("SELECT jr FROM JobRun jr WHERE (requestTime > ?1 OR resultTime > ?1) " +
	" AND status IN (?2)")
	Page<JobRun> findByStatusSinceDate(Date sinceDate, Set<JobStatus> statusFilter, Pageable pageable);

}
