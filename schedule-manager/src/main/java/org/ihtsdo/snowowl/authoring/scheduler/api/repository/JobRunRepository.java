package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface JobRunRepository extends CrudRepository<JobRun, UUID> {

	Optional<JobRun> findById(UUID id);
	
	List<JobRun> findByJobNameAndProjectInOrderByRequestTimeDesc(String jobName, Set<String> projects);
	
	List<JobRun> findByJobNameAndUserAndProjectIn(String jobName, String user, Set<String> projects);

}
