package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.api.domain.IComponent;
import com.b2international.snowowl.datastore.server.branch.Branch;
import com.b2international.snowowl.datastore.server.events.*;
import com.b2international.snowowl.datastore.server.review.ConceptChanges;
import com.b2international.snowowl.datastore.server.review.Review;
import com.b2international.snowowl.datastore.server.review.ReviewStatus;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.ISnomedDescriptionService;

import org.apache.commons.lang.time.StopWatch;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConceptConflict;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ChangeType;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.CdoStore;
import org.ihtsdo.snowowl.authoring.single.api.service.ts.SnomedServiceHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
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
	
	@Autowired
	private CdoStore cdoStore;

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
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch (getProjectPath(projectKey), getTaskPath(projectKey, taskKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Rebase from project to " + taskKey +  " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Rebase, resultMessage));
		return branch;
	}
	
	@Override
	public Branch promoteTask(String projectKey, String taskKey, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch (getTaskPath(projectKey, taskKey), getProjectPath(projectKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Promotion of " + taskKey + " completed without conflicts in " + stopwatch;
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
		return createConflictReport (getProjectPath(projectKey), getTaskPath(projectKey, taskKey), locales);
	}
	

	private ConflictReport createConflictReport(String sourcePath,
			String targetPath, ArrayList<Locale> locales) throws BusinessServiceException {	
	try {
			//Get changes in the target compared to the source, plus changes in the source
			//compared to the target and determine which ones intersect
			ExecutorService executor = Executors.newFixedThreadPool(2);
			AuthoringTaskReviewRunner targetChangesReviewRunner = new AuthoringTaskReviewRunner (targetPath, sourcePath, locales);
			AuthoringTaskReviewRunner sourceChangesReviewRunner = new AuthoringTaskReviewRunner (sourcePath, targetPath, locales);
			
			Future<AuthoringTaskReview> targetChangesReview = executor.submit(targetChangesReviewRunner);
			Future<AuthoringTaskReview> sourceChangesReview = executor.submit(sourceChangesReviewRunner);
			
			//Wait for both of these to complete
			executor.shutdown();
			executor.awaitTermination(REVIEW_TIMEOUT, TimeUnit.MINUTES);

			//Form Set of source changes so as to avoid n x m iterations
			Set<String> sourceChanges = new HashSet<String>();
			for (ReviewConcept thisConcept : sourceChangesReview.get().getConcepts()) {
				sourceChanges.add(thisConcept.getId());
			}
			
			List<ConceptConflict> conflictingConcepts = new ArrayList<ConceptConflict>();
			//Work through Target Changes to find concepts in common
			for (ReviewConcept thisConcept : targetChangesReview.get().getConcepts()) {
				if (sourceChanges.contains(thisConcept.getId())) {
					conflictingConcepts.add(new ConceptConflict(thisConcept.getId()));
				}
			}
			
			populateLastUpdateTimes(sourcePath, targetPath, conflictingConcepts);
			
			ConflictReport conflictReport = new ConflictReport();
			conflictReport.setConcepts(conflictingConcepts);
			conflictReport.setTargetReviewId(targetChangesReview.get().getReviewId());
			conflictReport.setSourceReviewId(sourceChangesReview.get().getReviewId());
			return conflictReport;
		} catch (ExecutionException|InterruptedException|SQLException e) {
			throw new BusinessServiceException ("Unable to retrieve Conflict report for " + targetPath + " due to " + e.getMessage(), e);
		}
	}
	
	private void populateLastUpdateTimes(String sourcePath, String targetPath,
			List<ConceptConflict> conflictingConcepts) throws SQLException {
		
		//No conflicts, no work to do!
		if ( conflictingConcepts == null || conflictingConcepts.size() == 0) {
			return;
		}
		
		//We need these last updated times from both the source and the targetPath
		BranchPath sourceBranchPath = new BranchPath(sourcePath);
		BranchPath targetBranchPath = new BranchPath(targetPath);
		
		Integer sourceBranchId = cdoStore.getBranchId(sourceBranchPath.getParentName(), sourceBranchPath.getChildName());
		Integer targetBranchId = cdoStore.getBranchId(targetBranchPath.getParentName(), targetBranchPath.getChildName());
		List<IComponent> concepts = new ArrayList<IComponent>(conflictingConcepts);
		
		Map<String, Date> sourceLastUpdatedMap = cdoStore.getLastUpdated(sourceBranchId, concepts);
		Map<String, Date> targetLastUpdatedMap = cdoStore.getLastUpdated(targetBranchId, concepts);
		
		//Now loop through our conflicting concepts and populate those last updated times, if known
		for(ConceptConflict thisConflict : conflictingConcepts) {
			if (sourceLastUpdatedMap.containsKey(thisConflict.getId())) {
				thisConflict.setSourceLastUpdate(sourceLastUpdatedMap.get(thisConflict.getId()));
			}
			
			if (targetLastUpdatedMap.containsKey(thisConflict.getId())) {
				thisConflict.setTargetLastUpdate(targetLastUpdatedMap.get(thisConflict.getId()));
			}
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
			ArrayList<Locale> locales) throws BusinessServiceException {
		return createConflictReport (MAIN, getProjectPath(projectKey), locales);
	}

	@Override
	public void rebaseProject(String projectKey, MergeRequest mergeRequest,
			String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch (MAIN, getProjectPath(projectKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Rebase from MAIN to " + projectKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Rebase, resultMessage));
	}

	@Override
	public void promoteProject(String projectKey,
			MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch (getProjectPath(projectKey), MAIN, mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Promotion of " + projectKey + " to MAIN completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Promotion, resultMessage));
	}

	private class BranchPath {
		String [] pathElements;
		BranchPath (String path) {
			this.pathElements = path.split("/");
		}
		
		//Child is the last string in the path
		String getChildName() {
			return pathElements[pathElements.length - 1];
		}
		
		//Parent is the String before the last slash
		String getParentName() {
			return pathElements.length > 1 ? pathElements[pathElements.length - 2] : null;
		}
	}
}
