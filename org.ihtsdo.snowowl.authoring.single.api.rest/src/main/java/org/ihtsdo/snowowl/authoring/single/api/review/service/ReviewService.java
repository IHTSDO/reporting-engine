package org.ihtsdo.snowowl.authoring.single.api.review.service;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.Concept;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewMessageCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.BranchRepository;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.ConceptRepository;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.ReviewMessageRepository;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

@Service
public class ReviewService {

	@Autowired
	private ReviewMessageRepository messageRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private ConceptRepository conceptRepository;

	@Autowired
	private BranchService branchService;

	public AuthoringTaskReview retrieveTaskReview(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return branchService.diffTaskBranch(projectKey, taskKey, locales);
	}

	public ReviewMessage postReviewMessage(String projectKey, String taskKey, ReviewMessageCreateRequest createRequest, String fromUsername) {
		final Branch branch = getCreateBranch(projectKey, taskKey);
		return messageRepository.save(
				new ReviewMessage(branch, createRequest.getMessageHtml(),
						getCreateConcepts(createRequest.getSubjectConceptIds()), fromUsername));
	}

	private List<Concept> getCreateConcepts(List<String> conceptIds) {
		List<Concept> concepts = conceptRepository.findById(conceptIds);
		List<String> missingConceptIds = new ArrayList<>(conceptIds);
		for (Concept concept : concepts) {
			missingConceptIds.remove(concept.getId());
		}
		for (String missingConceptId : missingConceptIds) {
			concepts.add(conceptRepository.save(new Concept(missingConceptId)));
		}
		return concepts;
	}

	private Branch getCreateBranch(String projectKey, String taskKey) {
		Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch == null) {
			branch = branchRepository.save(new Branch(projectKey, taskKey));
		}
		return branch;
	}

}
