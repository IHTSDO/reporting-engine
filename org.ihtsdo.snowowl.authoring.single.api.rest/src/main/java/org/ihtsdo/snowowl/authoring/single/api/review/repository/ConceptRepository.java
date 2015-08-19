package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Concept;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ConceptRepository extends CrudRepository<Concept, Long> {

	@Query("select c from Concept c where c.id in ?1")
	List<Concept> findById(List<String> conceptIds);
}
