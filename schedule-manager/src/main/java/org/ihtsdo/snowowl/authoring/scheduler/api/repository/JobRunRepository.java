package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface JobRunRepository extends CrudRepository<JobRun, UUID> {

	Optional<JobRun> findById(UUID id);
	
	List<JobRun> findByJobNameOrderByRequestTimeDesc(String jobName);
	
	List<JobRun> findByJobNameAndUser(String jobName, String user);

}
