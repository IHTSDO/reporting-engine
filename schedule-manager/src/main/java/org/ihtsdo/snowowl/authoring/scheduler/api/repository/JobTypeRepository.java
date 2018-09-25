package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;

public interface JobTypeRepository extends CrudRepository<JobType, String> {

	JobType findByName(String name);

}
