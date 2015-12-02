package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.domain.IComponent;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.b2international.snowowl.datastore.review.ConceptChanges;
import com.b2international.snowowl.datastore.review.Review;
import com.b2international.snowowl.datastore.review.ReviewStatus;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.api.impl.DescriptionService;
import com.b2international.snowowl.snomed.core.domain.ISnomedDescription;
import com.b2international.snowowl.snomed.datastore.server.request.SnomedRequests;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.lang.time.StopWatch;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.*;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ChangeType;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.CdoStore;
import org.ihtsdo.snowowl.authoring.single.api.service.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;

public class BranchService {

	@Autowired
	private IEventBus eventBus;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private CdoStore cdoStore;

	@Autowired
	private TaskService taskService;

	private static final String MAIN = "MAIN";

	//TODO Investigate wtih B2i why we see Refsets showing as modified in reviews
	private static final String TEMPORARY_CONCEPT_REMOVAL = "(foundation metadata concept)";

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final int REVIEW_TIMEOUT = 60; //Minutes
	private static final int MERGE_TIMEOUT = 60; //Minutes
	private static final int CONFLICT_REVIEW_TIMEOUT = 45; //Seconds

	public void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException {
		createProjectBranchIfNeeded(projectKey);
		createBranch(PathHelper.getPath(projectKey), taskKey);
	}

	public Branch.BranchState getBranchState(String project, String taskKey) throws NotFoundException {
		return SnomedRequests.branching().prepareGet(PathHelper.getPath(project, taskKey)).executeSync(eventBus).state();
	}

	public Branch.BranchState getBranchStateNoThrow(String projectKey, String issueKey) {
		try {
			return getBranchState(projectKey, issueKey);
		} catch (NotFoundException e) {
			return null;
		}
	}

	public AuthoringTaskReview diffTaskAgainstProject(String projectKey, String taskKey, List<ExtendedLocale> locales) throws ExecutionException, InterruptedException {
		return doDiff(PathHelper.getPath(projectKey, taskKey), PathHelper.getPath(projectKey), locales);
	}

	public AuthoringTaskReview diffProjectAgainstMain(String projectKey, List<ExtendedLocale> locales) throws ExecutionException, InterruptedException {
		return doDiff(PathHelper.getPath(projectKey), MAIN, locales);
	}

	public Branch rebaseTask(String projectKey, String taskKey, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch(PathHelper.getPath(projectKey), PathHelper.getPath(projectKey, taskKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Rebase from project to " + taskKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Rebase, resultMessage));
		return branch;
	}

	public Branch promoteTask(String projectKey, String taskKey, MergeRequest mergeRequest, String username) throws BusinessServiceException, JiraException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch(PathHelper.getPath(projectKey, taskKey), PathHelper.getPath(projectKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
		String resultMessage = "Promotion of " + taskKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Promotion, resultMessage));
		return branch;
	}

	public ReviewStatus getReviewStatus(String id) throws ExecutionException, InterruptedException {
		return getReview(id).status();
	}

	private void createBranch(String parentPath, String branchName) {
		SnomedRequests
				.branching()
				.prepareCreate()
				.setParent(parentPath)
				.setName(branchName)
				.build()
				.executeSync(eventBus);
	}

	private void createProjectBranchIfNeeded(String projectKey) throws ServiceException {
		try {
			SnomedRequests
					.branching()
					.prepareGet(PathHelper.getPath(projectKey))
					.executeSync(eventBus);
		} catch (NotFoundException e) {
			createBranch("MAIN", PathHelper.getPath(projectKey));
		}
	}

	private AuthoringTaskReview doDiff(String sourcePath, String targetPath, List<ExtendedLocale> locales) throws ExecutionException, InterruptedException {
		final TimerUtil timer = new TimerUtil("Review");
		final AuthoringTaskReview review = new AuthoringTaskReview();
		logger.info("Creating TS review - source {}, target {}", sourcePath, targetPath);
		Review tsReview = SnomedRequests.review().prepareCreate().setSource(sourcePath).setTarget(targetPath).build().executeSync(eventBus);
		timer.checkpoint("request review");

		logger.info("Waiting for TS review to complete");
		final String reviewId = tsReview.id();
		for (int a = 0; a < REVIEW_TIMEOUT; a++) {
			tsReview = getReview(reviewId);
			if (!ReviewStatus.PENDING.equals(tsReview.status())) {
				break;
			}
			Thread.sleep(1000);
		}
		timer.checkpoint("waiting for review to finish");
		logger.info("TS review {} status {}", reviewId, tsReview.status());
		review.setReviewId(reviewId);

		final ConceptChanges conceptChanges = SnomedRequests.review().prepareGetConceptChanges(reviewId).executeSync(eventBus);
		Set<String> conceptIds = new HashSet<>();
		conceptIds.addAll(conceptChanges.newConcepts());
		conceptIds.addAll(conceptChanges.changedConcepts());
		conceptIds.addAll(conceptChanges.deletedConcepts());
		final Map<String, ISnomedDescription> fullySpecifiedNames = new DescriptionService(eventBus, sourcePath).getFullySpecifiedNames(conceptIds, locales);
		addAllToReview(review, ChangeType.created, conceptChanges.newConcepts(), fullySpecifiedNames);
		addAllToReview(review, ChangeType.modified, conceptChanges.changedConcepts(), fullySpecifiedNames);
		addAllToReview(review, ChangeType.deleted, conceptChanges.deletedConcepts(), fullySpecifiedNames);
		timer.checkpoint("building review with terms");
		timer.finish();
		logger.info("Review {} built", reviewId);
		return review;
	}

	private Review getReview(String id) throws InterruptedException, ExecutionException {
		return SnomedRequests.review().prepareGet(id).executeSync(eventBus);
	}

	private void addAllToReview(AuthoringTaskReview review, ChangeType changeType, Set<String> conceptIds,
			Map<String, ISnomedDescription> fullySpecifiedNames) {
		for (String conceptId : conceptIds) {
			final ISnomedDescription description = fullySpecifiedNames.get(conceptId);
			String term = description != null ? description.getTerm() : conceptId;
			review.addConcept(new ReviewConcept(conceptId, term, changeType));
		}
	}

	private Branch mergeBranch(String sourcePath, String targetPath, String reviewId, String username) throws BusinessServiceException {
		String commitMsg = username + " performed merge of " + sourcePath + " to " + targetPath;
		return SnomedRequests
				.branching()
				.prepareMerge()
				.setSource(sourcePath)
				.setTarget(targetPath)
				.setCommitComment(commitMsg)
				.setReviewId(reviewId)
				.build()
				.executeSync(eventBus);
	}

	public ConflictReport createConflictReport(String projectKey, String taskKey, List<ExtendedLocale> locales) throws BusinessServiceException {
		String projectPath = PathHelper.getPath(projectKey);
		String taskPath = PathHelper.getPath(projectKey, taskKey);
		return doCreateConflictReport(projectPath, taskPath, locales);
	}

	private ConflictReport doCreateConflictReport(String sourcePath,
			String targetPath, List<ExtendedLocale> locales) throws BusinessServiceException {
		try {
			//Get changes in the target compared to the source, plus changes in the source
			//compared to the target and determine which ones intersect
			ExecutorService executor = Executors.newFixedThreadPool(2);
			AuthoringTaskReviewRunner targetChangesReviewRunner = new AuthoringTaskReviewRunner(targetPath, sourcePath, locales);
			AuthoringTaskReviewRunner sourceChangesReviewRunner = new AuthoringTaskReviewRunner(sourcePath, targetPath, locales);

			Future<AuthoringTaskReview> targetChangesReview = executor.submit(targetChangesReviewRunner);
			Future<AuthoringTaskReview> sourceChangesReview = executor.submit(sourceChangesReviewRunner);

			//Wait for both of these to complete
			logger.info("Waiting for both review reports to complete for target {} ", targetPath);
			executor.shutdown();
			executor.awaitTermination(CONFLICT_REVIEW_TIMEOUT, TimeUnit.SECONDS);
			logger.info("Both reviews completed for target {} ", targetPath);

			//Form Set of source changes so as to avoid n x m iterations
			Set<String> sourceChanges = new HashSet<>();
			for (ReviewConcept thisConcept : sourceChangesReview.get().getConcepts()) {
				sourceChanges.add(thisConcept.getId());
			}

			List<ConceptConflict> conflictingConcepts = new ArrayList<>();
			//Work through Target Changes to find concepts in common
			for (ReviewConcept thisConcept : targetChangesReview.get().getConcepts()) {
				if (sourceChanges.contains(thisConcept.getId())) {
					conflictingConcepts.add(new ConceptConflict(thisConcept.getId()));
				}
			}

			logger.info("Starting populate conflict report FSNs for {}", targetPath);
			populateFSNs(conflictingConcepts, locales, targetPath);

			//TODO WRP-1032 Refsets are going to be temporarily filtered out at this stage
			removeRefsets(conflictingConcepts);

			logger.info("Starting populate conflict report last update times for {}", targetPath);
			populateLastUpdateTimes(sourcePath, targetPath, conflictingConcepts);

			ConflictReport conflictReport = new ConflictReport();
			conflictReport.setConcepts(conflictingConcepts);
			conflictReport.setTargetReviewId(targetChangesReview.get().getReviewId());
			conflictReport.setSourceReviewId(sourceChangesReview.get().getReviewId());
			logger.info("Completed conflict report for {}", targetPath);

			return conflictReport;
		} catch (ExecutionException | InterruptedException | SQLException e) {
			throw new BusinessServiceException("Unable to retrieve Conflict report for " + targetPath + " due to " + e.getMessage(), e);
		}
	}

	private void populateFSNs(final List<ConceptConflict> conflictingConcepts, List<ExtendedLocale> locales, String targetBranchPath) {
		if (conflictingConcepts == null || conflictingConcepts.isEmpty()) {
			return;
		}

		final DescriptionService descriptionService = new DescriptionService(eventBus, targetBranchPath);

		Set<String> conceptIds = new HashSet<>();
		for (ConceptConflict conflictingConcept : conflictingConcepts) {
			conceptIds.add(conflictingConcept.getId());
		}
		final Map<String, ISnomedDescription> fullySpecifiedNames = descriptionService.getFullySpecifiedNames(conceptIds, locales);
		for (ConceptConflict conflictingConcept : conflictingConcepts) {
			final String conceptId = conflictingConcept.getId();
			final ISnomedDescription description = fullySpecifiedNames.get(conceptId);
			conflictingConcept.setFsn(description != null ? description.getTerm() : conceptId);
		}
	}

	private void removeRefsets(List<ConceptConflict> conflictingConcepts) {
		Iterator<ConceptConflict> i = conflictingConcepts.iterator();
		while (i.hasNext()) {
			ConceptConflict c = i.next(); // must be called before you can call i.remove()
			if (c.getFsn().contains(TEMPORARY_CONCEPT_REMOVAL)) {
				i.remove();
				logger.warn("Removed concept " + c.getFsn() + " from conflicts report.");
			}
		}
	}

	private void populateLastUpdateTimes(String sourcePath, String targetPath,
			List<ConceptConflict> conflictingConcepts) throws SQLException {

		//No conflicts, no work to do!
		if (conflictingConcepts == null || conflictingConcepts.size() == 0) {
			return;
		}

		//We need these last updated times from both the source and the targetPath
		BranchPath sourceBranchPath = new BranchPath(sourcePath);
		BranchPath targetBranchPath = new BranchPath(targetPath);

		Integer sourceBranchId = cdoStore.getBranchId(sourceBranchPath.getParentName(), sourceBranchPath.getChildName());
		Integer targetBranchId = cdoStore.getBranchId(targetBranchPath.getParentName(), targetBranchPath.getChildName());
		List<IComponent> concepts = new ArrayList<IComponent>(conflictingConcepts);

		//Generate a unique identifier for temp table population
		int transactionId = concepts.hashCode() * 1000 + targetPath.hashCode();

		//Populate the joining table the first time, delete it the second
		Map<String, Date> sourceLastUpdatedMap = cdoStore.getLastUpdated(sourceBranchId, concepts, transactionId, true);
		Map<String, Date> targetLastUpdatedMap = cdoStore.getLastUpdated(targetBranchId, concepts, transactionId, false);

		//Now loop through our conflicting concepts and populate those last updated times, if known
		logger.info("Populating " + conflictingConcepts.size() + " conflicts with " + sourceLastUpdatedMap.size() + " source times and "
				+ targetLastUpdatedMap.size() + " target times");
		for (ConceptConflict thisConflict : conflictingConcepts) {
			if (sourceLastUpdatedMap.containsKey(thisConflict.getId())) {
				thisConflict.setSourceLastUpdate(sourceLastUpdatedMap.get(thisConflict.getId()));
			}

			if (targetLastUpdatedMap.containsKey(thisConflict.getId())) {
				thisConflict.setTargetLastUpdate(targetLastUpdatedMap.get(thisConflict.getId()));
			}
		}

	}

	class AuthoringTaskReviewRunner implements Callable<AuthoringTaskReview> {

		final String sourcePath;
		final String targetPath;
		final List<ExtendedLocale> locales;

		AuthoringTaskReviewRunner(String sourcePath, String targetPath, List<ExtendedLocale> locales) {
			this.sourcePath = sourcePath;
			this.targetPath = targetPath;
			this.locales = locales;
		}

		@Override
		public AuthoringTaskReview call() throws Exception {
			return doDiff(sourcePath, targetPath, locales);
		}

	}

	public ConflictReport createConflictReport(String projectKey, List<ExtendedLocale> locales) throws BusinessServiceException {
		return doCreateConflictReport(MAIN, PathHelper.getPath(projectKey), locales);
	}

	public void rebaseProject(String projectKey, MergeRequest mergeRequest,
			String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch(MAIN, PathHelper.getPath(projectKey), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Rebase from MAIN to " + projectKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Rebase, resultMessage));
	}

	public void promoteProject(String projectKey,
			MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch(PathHelper.getPath(projectKey), MAIN, mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		String resultMessage = "Promotion of " + projectKey + " to MAIN completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Promotion, resultMessage));
	}

	private class BranchPath {
		String[] pathElements;

		BranchPath(String path) {
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
