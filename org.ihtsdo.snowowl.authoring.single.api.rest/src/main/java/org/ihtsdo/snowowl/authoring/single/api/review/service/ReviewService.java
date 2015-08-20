package org.ihtsdo.snowowl.authoring.single.api.review.service;

import org.ihtsdo.snowowl.authoring.single.api.review.domain.Branch;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewMessageCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.BranchRepository;
import org.ihtsdo.snowowl.authoring.single.api.review.repository.ReviewMessageRepository;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class ReviewService {

	@Autowired
	private ReviewMessageRepository messageRepository;

	@Autowired
	private BranchRepository branchRepository;

	@Autowired
	private BranchService branchService;

	public AuthoringTaskReview retrieveTaskReview(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		final AuthoringTaskReview authoringTaskReview = branchService.diffTaskBranch(projectKey, taskKey, locales);
		final List<ReviewConcept> reviewConcepts = authoringTaskReview.getConcepts();
		final Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch != null) {
			final List<ReviewMessage> reviewMessages = messageRepository.findByBranch(branch);
			Map<String, List<ReviewMessage>> conceptMessagesMap = new HashMap<>();
			for (ReviewMessage reviewMessage : reviewMessages) {
				for (String conceptId : reviewMessage.getSubjectConceptIds()) {
					if (!conceptMessagesMap.containsKey(conceptId)) {
						conceptMessagesMap.put(conceptId, new ArrayList<ReviewMessage>());
					}
					conceptMessagesMap.get(conceptId).add(reviewMessage);
				}
			}
			for (ReviewConcept reviewConcept : reviewConcepts) {
				reviewConcept.setMessages(conceptMessagesMap.get(reviewConcept.getId()));
			}
		}
		return authoringTaskReview;
	}

	public ReviewMessage postReviewMessage(String projectKey, String taskKey, ReviewMessageCreateRequest createRequest, String fromUsername) {
		final Branch branch = getCreateBranch(projectKey, taskKey);
		return messageRepository.save(
				new ReviewMessage(branch, createRequest.getMessageHtml(),
						createRequest.getSubjectConceptIds(), fromUsername));
	}

	private Branch getCreateBranch(String projectKey, String taskKey) {
		Branch branch = branchRepository.findOneByProjectAndTask(projectKey, taskKey);
		if (branch == null) {
			branch = branchRepository.save(new Branch(projectKey, taskKey));
		}
		return branch;
	}

}
