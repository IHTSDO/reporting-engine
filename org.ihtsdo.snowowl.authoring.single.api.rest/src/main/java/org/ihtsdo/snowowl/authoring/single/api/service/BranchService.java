package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.datastore.server.request.SnomedRequests;
import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraException;
import org.apache.commons.lang.time.StopWatch;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

public class BranchService {

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
		return SnomedRequests.branching().prepareGet(branchPath).executeSync(eventBus).state();
	}

	public Branch.BranchState getBranchStateNoThrow(String branchPath) {
		try {
			return getBranchState(branchPath);
		} catch (NotFoundException e) {
			return null;
		}
	}

	public Branch rebaseTask(String branchPath, MergeRequest mergeRequest, String username) throws BusinessServiceException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch(PathHelper.getParentPath(branchPath), branchPath, mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		final String taskKey = PathHelper.getName(branchPath);
		String resultMessage = "Rebase from project to " + taskKey + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(PathHelper.getParentName(branchPath), taskKey, EntityType.Rebase, resultMessage));
		return branch;
	}

	public Branch promoteTask(String branchPath, MergeRequest mergeRequest, String username) throws BusinessServiceException, JiraException {
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		Branch branch = mergeBranch(branchPath, PathHelper.getParentPath(branchPath), mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		final String projectKey = PathHelper.getParentName(branchPath);
		final String taskKey = PathHelper.getName(branchPath);
		taskService.stateTransition(projectKey, taskKey, TaskStatus.PROMOTED);
		String resultMessage = "Promotion of " + PathHelper.getName(branchPath) + " completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, taskKey, EntityType.Promotion, resultMessage));
		return branch;
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
			SnomedRequests
					.branching()
					.prepareGet(branchPath)
					.executeSync(eventBus);
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

}
