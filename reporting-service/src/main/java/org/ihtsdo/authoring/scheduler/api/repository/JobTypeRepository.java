package org.ihtsdo.authoring.scheduler.api.repository;

import java.util.List;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;

public interface JobTypeRepository extends CrudRepository<JobType, String> {

	JobType findByName(String name);
	
	List<JobType> findAll();

}
