package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.Metadata;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.b2international.snowowl.core.merge.Merge;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.datastore.request.SnomedRequests;
import com.google.common.collect.Lists;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.lang.time.StopWatch;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class BranchService {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	@Autowired
	private IEventBus eventBus;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private TaskService taskService;

	private static final String MAIN = "MAIN";

	public void createTaskBranchAndProjectBranchIfNeeded(String branchPath) throws ServiceException {
		createProjectBranchIfNeeded(PathHelper.getParentPath(branchPath));
		createBranch(branchPath);
	}

	public Branch.BranchState getBranchState(String branchPath) throws NotFoundException {
		return getBranch(branchPath).state();
	}

	public Branch getBranch(String branchPath) {
		return SnomedRequests.branching().prepareGet(branchPath).executeSync(eventBus);
	}

	public Branch getBranchOrNull(String branchPath) {
		try {
			return getBranch(branchPath);
		} catch (NotFoundException e) {
			return null;
		}
	}

	public Branch.BranchState getBranchStateOrNull(String branchPath) {
		final Branch branchOrNull = getBranchOrNull(branchPath);
		return branchOrNull == null ? null : branchOrNull.state();
	}

	public Merge rebaseTask(String branchPath, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Merge merge = mergeBranch(PathHelper.getParentPath(branchPath), branchPath, mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		final String taskKey = PathHelper.getName(branchPath);
		String resultMessage = "Rebase from project to " + taskKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(PathHelper.getParentName(branchPath), taskKey, EntityType.Rebase, resultMessage));
		return merge;
	}

	public Merge promoteTask(String branchPath, MergeRequest mergeRequest, String username) throws BusinessServiceException, JiraException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Merge merge = mergeBranch(branchPath, PathHelper.getParentPath(branchPath), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		final String projectKey = PathHelper.getParentName(branchPath);
		final String taskKey = PathHelper.getName(branchPath);
		taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
		String resultMessage = "Promotion of " + PathHelper.getName(branchPath) + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Promotion, resultMessage));
		return merge;
	}

	private void createBranch(String branchPath) {
		SnomedRequests
				.branching()
				.prepareCreate()
				.setParent(PathHelper.getParentPath(branchPath))
				.setName(PathHelper.getName(branchPath))
				.build()
				.executeSync(eventBus);
	}

	public void createProjectBranchIfNeeded(String branchPath) throws ServiceException {
		try {
			getBranch(branchPath);
		} catch (NotFoundException e) {
			createBranch(branchPath);
		}
	}

	public void rebaseProject(String branchPath, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch(PathHelper.getParentPath(branchPath), branchPath, mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		final String projectKey = PathHelper.getName(branchPath);
		String resultMessage = "Rebase from " + getProjectParentLabel(branchPath) +" to " + projectKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Rebase, resultMessage));
	}

	public void promoteProject(String branchPath, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		final String projectKey = PathHelper.getName(branchPath);
		List<Issue> promotedIssues = taskService.getTaskIssues(projectKey, TaskStatus.PROMOTED);
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch(branchPath, PathHelper.getParentPath(branchPath), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		taskService.stateTransition(promotedIssues, TaskStatus.COMPLETED);
		String resultMessage = "Promotion of " + projectKey + " to " + getProjectParentLabel(branchPath) + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Promotion, resultMessage));
	}

	private String getProjectParentLabel(String branchPath) {
		return PathHelper.getParentPath(branchPath).equals("MAIN") ? "MAIN" : "Extension";
	}

	private Merge mergeBranch(String sourcePath, String targetPath, String reviewId, String username) throws BusinessServiceException {
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
	
	public Metadata getBranchMetadataIncludeInherited(String path) {
		Metadata mergedMetadata = null;
		List<String> stackPaths = getBranchPathStack(path);
		for (String stackPath : stackPaths) {
			final Branch branch = getBranch(stackPath);
			final Metadata metadata = branch.metadata();
			if (mergedMetadata == null) {
				mergedMetadata = metadata;
			} else {
				// merge metadata
				for (String key : metadata.keySet()) {
					if (!key.equals("lock") || stackPath.equals(path)) { // Only copy lock info from the deepest branch
						mergedMetadata.put(key, metadata.get(key));
					}
				}
			}
		}
		return mergedMetadata;
	}
	
	private List<String> getBranchPathStack(String path) {
		List<String> paths = new ArrayList<>();
		paths.add(path);
		int index;
		while ((index = path.lastIndexOf("/")) != -1) {
			path = path.substring(0, index);
			paths.add(path);
		}
		return Lists.reverse(paths);
	}

}
