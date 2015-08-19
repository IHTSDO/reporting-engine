package org.ihtsdo.snowowl.authoring.single.api.review.repository;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Concept;
import org.springframework.data.repository.CrudRepository;

import java.util.List;

public interface ConceptRepository extends CrudRepository<Concept, Long> {

	List<Concept> findByName(String name);

}
