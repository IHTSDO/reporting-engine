package org.ihtsdo.authoring.scheduler.api.repository;

import java.util.List;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.repository.CrudRepository;
import reactor.util.annotation.NonNull;

public interface JobRepository extends CrudRepository<Job, Long> {

	Job findByName(String name);

	List<Job> findByCategoryId(long id);

	@NonNull
	List<Job> findAll();
}
