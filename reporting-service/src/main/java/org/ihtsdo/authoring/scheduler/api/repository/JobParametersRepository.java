package org.ihtsdo.authoring.scheduler.api.repository;

import org.snomed.otf.scheduler.domain.JobParameters;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface JobParametersRepository extends CrudRepository<JobParameters, Long> {

	@Query(nativeQuery=true, value="SELECT * FROM job_parameters WHERE id IN (SELECT parameters_id FROM job_run GROUP BY parameters_id HAVING COUNT(parameters_id) > 1)")
	List<JobParameters> findAllCoupled();
}
