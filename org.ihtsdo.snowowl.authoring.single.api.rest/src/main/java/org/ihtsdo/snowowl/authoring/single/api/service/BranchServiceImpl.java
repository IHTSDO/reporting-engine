package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.datastore.server.events.*;
import com.b2international.snowowl.datastore.server.review.ConceptChanges;
import com.b2international.snowowl.datastore.server.review.Review;
import com.b2international.snowowl.datastore.server.review.ReviewStatus;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.ISnomedDescriptionService;

import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ChangeType;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.service.ts.SnomedServiceHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public class BranchServiceImpl implements BranchService {

	@Autowired
	private SnowOwlBusHelper snowOwlBusHelper;
	
	@Autowired
	private IEventBus eventBus;

	@Autowired
	private ISnomedDescriptionService descriptionService;

	private static final String SNOMED_STORE = "snomedStore";
	private static final String MAIN = "MAIN";
	public static final String SNOMED_TS_REPOSITORY_ID = "snomedStore";
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Override
	public void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException {
		createProjectBranchIfNeeded(projectKey);
		snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, getBranchPath(projectKey), taskKey, null), BranchReply.class, "Failed to create project branch.", this);
	}

	@Override
	public AuthoringTaskReview diffTaskBranch(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		final Timer timer = new Timer("Review");
		final AuthoringTaskReview review = new AuthoringTaskReview();
		logger.info("Creating TS review");
		final String taskPath = getTaskPath(projectKey, taskKey);
		final ReviewReply reviewReply = new CreateReviewEvent(SNOMED_TS_REPOSITORY_ID, taskPath, getBranchPath(projectKey))
				.send(eventBus, ReviewReply.class).get();
		timer.checkpoint("request review");

		Review tsReview = reviewReply.getReview();
		logger.info("Waiting for TS review to complete");
		for (int a = 0; a < 60; a++) {
			final ReviewReply latestReviewReply = new ReadReviewEvent(SNOMED_TS_REPOSITORY_ID, tsReview.id()).send(eventBus, ReviewReply.class).get();
			tsReview = latestReviewReply.getReview();
			if (!ReviewStatus.PENDING.equals(tsReview.status())) {
				break;
			}
			Thread.sleep(1000);
		}
		timer.checkpoint("waiting for review to finish");
		logger.info("TS review status {}", tsReview.status());

		final ConceptChangesReply conceptChangesReply = new ReadConceptChangesEvent(SNOMED_TS_REPOSITORY_ID, tsReview.id())
				.send(eventBus, ConceptChangesReply.class).get();
		timer.checkpoint("getting changes");

		final ConceptChanges conceptChanges = conceptChangesReply.getConceptChanges();
		addAllToReview(review, ChangeType.created, conceptChanges.newConcepts(), taskPath, locales);
		addAllToReview(review, ChangeType.modified, conceptChanges.changedConcepts(), taskPath, locales);
		addAllToReview(review, ChangeType.deleted, conceptChanges.deletedConcepts(), taskPath, locales);
		timer.checkpoint("building review with terms");
		timer.finish();
		logger.info("Review built");
		return review;
	}

	private void addAllToReview(AuthoringTaskReview review, ChangeType changeType, Set<String> conceptIds, String branchPath, List<Locale> locales) {
		for (String conceptId : conceptIds) {
			final String term = descriptionService.getFullySpecifiedName(SnomedServiceHelper.createComponentRef(branchPath, conceptId), locales).getTerm();
			review.addConcept(new ReviewConcept(conceptId, term, changeType));
		}
	}

	private void createProjectBranchIfNeeded(String projectKey) throws ServiceException {
		try {
			snowOwlBusHelper.makeBusRequest(new ReadBranchEvent(SNOMED_STORE, getBranchPath(projectKey)), BranchReply.class, "Failed to find project branch.", this);
		} catch (ServiceException e) {
			snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, MAIN, projectKey, null), BranchReply.class, "Failed to create project branch.", this);
		}
	}

	private String getBranchPath(String projectKey) {
		return MAIN + "/" + projectKey;
	}

	@Override
	public String getTaskPath(String projectKey, String taskKey) {
		return getBranchPath(projectKey + "/" + taskKey);
	}

	@Override
	public String getProjectPath(String projectKey) {
		return getBranchPath(projectKey);
	}

}
