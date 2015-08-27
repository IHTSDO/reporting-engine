package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.datastore.server.events.*;
import com.b2international.snowowl.datastore.server.review.ConceptChanges;
import com.b2international.snowowl.datastore.server.review.Review;
import com.b2international.snowowl.datastore.server.review.ReviewStatus;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.ISnomedDescriptionService;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ChangeType;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.service.ts.SnomedServiceHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashSet;
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
	
	private enum DIFF_DIRECTION { TASK_FROM_PROJECT, PROJECT_FROM_TASK }

	@Override
	public void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException {
		createProjectBranchIfNeeded(projectKey);
		snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, getBranchPath(projectKey), taskKey, null), BranchReply.class, "Failed to create project branch.", this);
	}

	@Override
	public AuthoringTaskReview diffTaskAgainstProject(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(projectKey, taskKey, locales, DIFF_DIRECTION.TASK_FROM_PROJECT);
	}
	
	@Override
	public AuthoringTaskReview diffProjectAgainstTask(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(projectKey, taskKey, locales, DIFF_DIRECTION.PROJECT_FROM_TASK);
	}
	
	private AuthoringTaskReview doDiff(String projectKey, String taskKey, List<Locale> locales, DIFF_DIRECTION diffDirection) throws ExecutionException, InterruptedException {
			
		final Timer timer = new Timer("Review");
		final AuthoringTaskReview review = new AuthoringTaskReview();
		logger.info("Creating TS review - " + diffDirection.toString());
		final String taskPath = getTaskPath(projectKey, taskKey);
		final String projectPath = getBranchPath(projectKey);
		String sourcePath, targetPath;
		if (diffDirection.equals(DIFF_DIRECTION.TASK_FROM_PROJECT)) {
			sourcePath = taskPath;
			targetPath = projectPath;
		} else {
			sourcePath = projectPath;
			targetPath = taskPath;
		}
		
		final ReviewReply reviewReply = new CreateReviewEvent(SNOMED_TS_REPOSITORY_ID, sourcePath, targetPath)
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
		logger.info("TS review {} status {}", tsReview.id(), tsReview.status());
		review.setReviewId(tsReview.id());
		
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

	@Override
	public ConflictReport retrieveConflictReport(String projectKey,
			String taskKey, ArrayList<Locale> locales) throws BusinessServiceException {
		try {
			//Get changes in the task compared to the project, plus changes in the project
			//compared to the task and determine which ones intersect
			AuthoringTaskReview taskChangesReview = diffTaskAgainstProject (projectKey, taskKey, locales);
			AuthoringTaskReview projectChangesReview = diffProjectAgainstTask(projectKey, taskKey, locales);
			
			//Form Set of ProjectChanges so as to avoid n x m iterations
			Set<String> projectChanges = new HashSet<String>();
			for (ReviewConcept thisConcept : projectChangesReview.getConcepts()) {
				projectChanges.add(thisConcept.getId());
			}
			
			List<ReviewConcept> conflictingConcepts = new ArrayList<ReviewConcept>();
			//Work through taskChanges to find concepts in common
			for (ReviewConcept thisConcept : taskChangesReview.getConcepts()) {
				if (projectChanges.contains(thisConcept.getId())) {
					conflictingConcepts.add(thisConcept);
				}
			}
			
			ConflictReport conflictReport = new ConflictReport();
			conflictReport.setConcepts(conflictingConcepts);
			conflictReport.setTaskReviewId(taskChangesReview.getReviewId());
			conflictReport.setProjectReviewId(projectChangesReview.getReviewId());
			return conflictReport;
		} catch (ExecutionException|InterruptedException e) {
			throw new BusinessServiceException ("Unable to retrieve Conflict report for " + getTaskPath(projectKey, taskKey), e);
		}
	}

	@Override
	public void rebaseTask(String projectKey, String taskKey,
			MergeRequest mergeRequest, String username) {
		logger.warn("Task rebasing not yet implemented");
		return ;
	}

}
