package org.ihtsdo.authoring.scheduler.api.repository;

import java.util.List;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;

public interface JobCategoryRepository extends CrudRepository<JobCategory, Long> {

	JobCategory findByName(String name);
	
	List<JobCategory> findByType(JobType type);

}
