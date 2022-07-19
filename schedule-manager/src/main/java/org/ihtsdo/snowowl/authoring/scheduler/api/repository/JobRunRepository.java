package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.CrudRepository;

import java.util.*;

public interface JobRunRepository extends CrudRepository<JobRun, UUID> {

	Optional<JobRun> findById(UUID id);
	
	Page<JobRun> findByJobNameAndProjectInOrderByRequestTimeDesc(String jobName, Set<String> projects, Pageable pageable);
	
	Page<JobRun> findByJobNameAndUserAndProjectInOrderByRequestTimeDesc(String jobName, String user, Set<String> projects, Pageable pageable);

}
