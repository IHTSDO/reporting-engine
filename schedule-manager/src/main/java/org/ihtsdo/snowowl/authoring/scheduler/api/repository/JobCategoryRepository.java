package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;

public interface JobCategoryRepository extends CrudRepository<JobCategory, Long> {

	JobCategory findByName(String name);

}
