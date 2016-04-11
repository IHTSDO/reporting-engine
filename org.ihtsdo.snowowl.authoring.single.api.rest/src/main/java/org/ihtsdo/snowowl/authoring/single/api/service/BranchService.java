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

	private void createBranch(String parentPath, String branchName) {
		SnomedRequests
				.branching()
				.prepareCreate()
				.setParent(parentPath)
				.setName(branchName)
				.build()
				.executeSync(eventBus);
	}

	public void createProjectBranchIfNeeded(String projectKey) throws ServiceException {
		try {
			SnomedRequests
					.branching()
					.prepareGet(PathHelper.getPath(projectKey))
					.executeSync(eventBus);
		} catch (NotFoundException e) {
			createBranch("MAIN", projectKey);
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
		List<Issue> promotedIssues = taskService.getTaskIssues(projectKey, TaskStatus.PROMOTED);
		StopWatch stopwatch = new StopWatch();
		stopwatch.start();
		mergeBranch(PathHelper.getPath(projectKey), MAIN, mergeRequest.getSourceReviewId(), username);
		stopwatch.stop();
		taskService.stateTransition(promotedIssues, TaskStatus.COMPLETED);
		String resultMessage = "Promotion of " + projectKey + " to MAIN completed without conflicts in " + stopwatch;
		notificationService.queueNotification(username, new Notification(projectKey, EntityType.Promotion, resultMessage));
	}

}
