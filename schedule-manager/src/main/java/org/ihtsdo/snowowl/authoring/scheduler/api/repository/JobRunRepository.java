package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobRun;
import org.springframework.data.repository.CrudRepository;

import java.util.List;
import java.util.UUID;

public interface JobRunRepository extends CrudRepository<JobRun, UUID> {

	List<JobRun> findByJobName(String jobName);
	
	List<JobRun> findByJobNameAndUser(String jobName, String user);

}
