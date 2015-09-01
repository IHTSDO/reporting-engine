package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.datastore.server.branch.Branch;
import com.b2international.snowowl.datastore.server.events.*;
import com.b2international.snowowl.datastore.server.review.ConceptChanges;
import com.b2international.snowowl.datastore.server.review.Review;
import com.b2international.snowowl.datastore.server.review.ReviewStatus;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.ISnomedDescriptionService;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ChangeType;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.service.ts.SnomedServiceHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.*;

public class BranchServiceImpl implements BranchService {

	@Autowired
	private SnowOwlBusHelper snowOwlBusHelper;
	
	@Autowired
	private IEventBus eventBus;
	
	@Autowired
	private NotificationService notificationService;

	@Autowired
	private ISnomedDescriptionService descriptionService;

	private static final String SNOMED_STORE = "snomedStore";
	private static final String MAIN = "MAIN";
	public static final String SNOMED_TS_REPOSITORY_ID = "snomedStore";
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private static final int REVIEW_TIMEOUT = 60; //Minutes
	private static final int MERGE_TIMEOUT = 60; //Minutes

	@Override
	public void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException {
		createProjectBranchIfNeeded(projectKey);
		snowOwlBusHelper.makeBusRequest(new CreateBranchEvent(SNOMED_STORE, getBranchPath(projectKey), taskKey, null), BranchReply.class, "Failed to create project branch.", this);
	}

	@Override
	public AuthoringTaskReview diffTaskAgainstProject(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(getTaskPath(projectKey, taskKey), getProjectPath(projectKey), locales);
	}

	@Override
	public AuthoringTaskReview diffProjectAgainstMain(String projectKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(getProjectPath(projectKey), MAIN, locales);
	}
	
	@Override
	public Branch rebaseTask(String projectKey, String taskKey, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		Branch branch = mergeBranch (getProjectPath(projectKey), getTaskPath(projectKey, taskKey), mergeRequest.getSourceReviewId(), username);
		String resultMessage = "Rebase completed without conflicts";
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Rebase, resultMessage));
		return branch;
	}
	
	@Override
	public Branch promoteTask(String projectKey, String taskKey, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		Branch branch = mergeBranch (getTaskPath(projectKey, taskKey), getProjectPath(projectKey), mergeRequest.getSourceReviewId(), username);
		String resultMessage = "Promotion completed without conflicts";
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Promotion, resultMessage));
		return branch;
	}
	
	@Override
	public AuthoringTaskReview diffProjectAgainstTask(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException {
		return doDiff(getProjectPath(projectKey), getTaskPath(projectKey, taskKey), locales);
	}
	
	private AuthoringTaskReview doDiff(String sourcePath, String targetPath, List<Locale> locales) throws ExecutionException, InterruptedException {
		final Timer timer = new Timer("Review");
		final AuthoringTaskReview review = new AuthoringTaskReview();
		logger.info("Creating TS review - source {}, target {}", sourcePath, targetPath);
		final ReviewReply reviewReply = new CreateReviewEvent(SNOMED_TS_REPOSITORY_ID, sourcePath, targetPath)
				.send(eventBus, ReviewReply.class).get();
		timer.checkpoint("request review");

		Review tsReview = reviewReply.getReview();
		logger.info("Waiting for TS review to complete");
		for (int a = 0; a < REVIEW_TIMEOUT; a++) {
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
		addAllToReview(review, ChangeType.created, conceptChanges.newConcepts(), sourcePath, locales);
		addAllToReview(review, ChangeType.modified, conceptChanges.changedConcepts(), sourcePath, locales);
		addAllToReview(review, ChangeType.deleted, conceptChanges.deletedConcepts(), sourcePath, locales);
		timer.checkpoint("building review with terms");
		timer.finish();
		logger.info("Review {} built", tsReview.id());
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
	
	private Branch mergeBranch(String sourcePath, String targetPath, String reviewId, String username) throws BusinessServiceException {
		try {
			String commitMsg = username + " performed merge of " + sourcePath + " to " + targetPath;
			MergeEvent mergeEvent = new MergeEvent( SNOMED_TS_REPOSITORY_ID, sourcePath, targetPath, commitMsg, reviewId);
			BranchReply branchReply = mergeEvent.send(eventBus, BranchReply.class).get(MERGE_TIMEOUT, TimeUnit.MINUTES);
			return branchReply.getBranch();
		} catch (InterruptedException | ExecutionException | TimeoutException e) {
			throw new BusinessServiceException ("Failure while attempting to merge " + sourcePath + " to " + targetPath + " due to " + e.getMessage(), e );
		} 
	}

	@Override
	public ConflictReport retrieveConflictReport(String projectKey,
			String taskKey, ArrayList<Locale> locales) throws BusinessServiceException {
		try {
			//Get changes in the task compared to the project, plus changes in the project
			//compared to the task and determine which ones intersect
			ExecutorService executor = Executors.newFixedThreadPool(2);
			AuthoringTaskReviewRunner taskChangesReviewRunner = new AuthoringTaskReviewRunner (getTaskPath(projectKey, taskKey), getProjectPath(projectKey), locales);
			AuthoringTaskReviewRunner projectChangesReviewRunner = new AuthoringTaskReviewRunner (getProjectPath(projectKey), getTaskPath(projectKey, taskKey), locales);
			
			Future<AuthoringTaskReview> taskChangesReview = executor.submit(taskChangesReviewRunner);
			Future<AuthoringTaskReview> projectChangesReview = executor.submit(projectChangesReviewRunner);
			
			//Wait for both of these to complete
			executor.shutdown();
			executor.awaitTermination(REVIEW_TIMEOUT, TimeUnit.MINUTES);

			//Form Set of ProjectChanges so as to avoid n x m iterations
			Set<String> projectChanges = new HashSet<String>();
			for (ReviewConcept thisConcept : projectChangesReview.get().getConcepts()) {
				projectChanges.add(thisConcept.getId());
			}
			
			List<ReviewConcept> conflictingConcepts = new ArrayList<ReviewConcept>();
			//Work through taskChanges to find concepts in common
			for (ReviewConcept thisConcept : taskChangesReview.get().getConcepts()) {
				if (projectChanges.contains(thisConcept.getId())) {
					conflictingConcepts.add(thisConcept);
				}
			}
			
			ConflictReport conflictReport = new ConflictReport();
			conflictReport.setConcepts(conflictingConcepts);
			conflictReport.setTaskReviewId(taskChangesReview.get().getReviewId());
			conflictReport.setProjectReviewId(projectChangesReview.get().getReviewId());
			return conflictReport;
		} catch (ExecutionException|InterruptedException e) {
			throw new BusinessServiceException ("Unable to retrieve Conflict report for " + getTaskPath(projectKey, taskKey), e);
		}
	}
	
	class AuthoringTaskReviewRunner implements Callable< AuthoringTaskReview> {
		
		final String sourcePath;
		final String targetPath;
		final List<Locale> locales;
		
		AuthoringTaskReviewRunner(String sourcePath, String targetPath, List<Locale> locales) {
			this.sourcePath = sourcePath;
			this.targetPath = targetPath;
			this.locales = locales;
		}

		@Override
		public AuthoringTaskReview call() throws Exception {
			return doDiff(sourcePath, targetPath, locales);
		}
		
	}

	@Override
	public ConflictReport retrieveConflictReport(String projectKey,
			ArrayList<Locale> list) throws BusinessServiceException {
		throw new BusinessServiceException ("Not yet implemented");
	}

	@Override
	public void rebaseProject(String projectKey, MergeRequest mergeRequest,
			String username) throws BusinessServiceException {
		throw new BusinessServiceException ("Not yet implemented");
	}

	@Override
	public void promoteProject(String projectKey, String taskKey,
			MergeRequest mergeRequest, String username) throws BusinessServiceException {
		throw new BusinessServiceException ("Not yet implemented");
	}

}
