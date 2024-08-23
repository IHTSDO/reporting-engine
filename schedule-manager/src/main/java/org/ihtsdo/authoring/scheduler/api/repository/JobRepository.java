package org.ihtsdo.authoring.scheduler.api.repository;

import java.util.List;

import org.snomed.otf.scheduler.domain.*;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import reactor.util.annotation.NonNull;

public interface JobRepository extends CrudRepository<Job, Long> {

	Job findByName(String name);

	List<Job> findByCategoryId(long id);

	@NonNull
	List<Job> findAll();

	@NonNull
	@Query(nativeQuery=true, value="SELECT * FROM job WHERE production_status <> 2 ORDER BY name")
	List<Job> findAllNotHidden();
}
