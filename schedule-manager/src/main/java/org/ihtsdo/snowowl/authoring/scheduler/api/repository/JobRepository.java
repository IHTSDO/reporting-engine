package org.ihtsdo.snowowl.authoring.scheduler.api.repository;

import java.util.List;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;

public interface JobRepository extends CrudRepository<Job, Long> {

	Job findByName(String name);

	List<Job> findByCategoryId(long id);

}
