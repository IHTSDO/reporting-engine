package org.ihtsdo.snowowl.authoring.single.api.review.service;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Concept;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.ConceptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;

import java.util.List;

@Service
public class ReviewService {

	@Autowired
	private ConceptRepository conceptRepository;

	public List<Concept> findConcepts() {
		Assert.notNull(conceptRepository, "conceptRepository can not be null");
		conceptRepository.save(new Concept("One"));
		conceptRepository.save(new Concept("Two"));
		conceptRepository.save(new Concept("Three"));

		return conceptRepository.findByName("Two");
	}

}
