package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.ConflictException;
import com.b2international.snowowl.core.exceptions.NotFoundException;

import net.rcarz.jiraclient.*;
import net.sf.json.JSONObject;

import org.ihtsdo.otf.im.utility.SecurityService;
import org.ihtsdo.otf.rest.client.OrchestrationRestClient;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskUpdateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import us.monoid.json.JSONException;

import java.io.IOException;
import java.util.*;


public class TaskService {

	public static final String FAILED_TO_RETRIEVE = "Failed-to-retrieve";
	
	private static final String INCLUDED_FIELDS = "*all";
	private static final int CHUNK_SIZE = 50;

	@Autowired
	private BranchServiceImpl branchService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private OrchestrationRestClient orchestrationRestClient;

	@Autowired
	private SecurityService ims;

	private final JiraClientFactory jiraClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";
	
	public TaskService(JiraClientFactory jiraClientFactory, String jiraReviewerField) {
		this.jiraClientFactory = jiraClientFactory;
		AuthoringTask.setJiraReviewerField(jiraReviewerField);
	}
	
	public List<AuthoringProject> listProjects() throws JiraException, IOException, JSONException, RestClientException {
		List<AuthoringProject> authoringProjects = new ArrayList<>();
		for (Issue issue : getJiraClient().searchIssues("type = \"SCA Authoring Project\"").issues) {
			Project project = issue.getProject();
			authoringProjects.add(buildProject(project));
		}
		return authoringProjects;
	}

	public AuthoringProject retrieveProject(String projectKey) throws JiraException, JSONException, RestClientException, IOException {
		return buildProject(getProjectTicket(projectKey).getProject());
	}

	private AuthoringProject buildProject(Project project) throws IOException, JSONException, RestClientException {
		final String validationStatus = orchestrationRestClient.retrieveValidationStatuses(Collections.singletonList(PathHelper.getPath(project.getKey()))).get(0);
		final String latestClassificationJson = classificationService.getLatestClassification(PathHelper.getPath(project.getKey()));
		return new AuthoringProject(project.getKey(), project.getName(), validationStatus, latestClassificationJson);
	}

	public Issue getProjectTicket(String projectKey) throws JiraException {
		final List<Issue> issues = getJiraClient().searchIssues("project = " + projectKey + " AND type = \"SCA Authoring Project\"").issues;
		if (!issues.isEmpty()) {
			return issues.get(0);
		}
		return null;
	}

	public List<AuthoringTask> listTasks(String projectKey) throws JiraException, RestClientException {
		getProjectOrThrow(projectKey);
		List<Issue> issues = searchIssues(getProjectTaskJQL(projectKey), 0, 0);  //unlimited recovery for now
		return buildAuthoringTasks(issues);
	}

	public AuthoringTask retrieveTask(String projectKey, String taskKey) throws BusinessServiceException {
		try {
			Issue issue = getIssue(projectKey, taskKey);
			return buildAuthoringTasks(Collections.singletonList(issue)).get(0);
		} catch (JiraException | RestClientException e) {
			throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
		}
	}

	private Issue getIssue(String projectKey, String taskKey) throws JiraException {
		getProjectOrThrow(projectKey);
		List<Issue> issues = getJiraClient().searchIssues(getProjectTaskJQL(projectKey) + " AND key = " + taskKey).issues;
		if (!issues.isEmpty()) {
			return issues.get(0);
		} else {
			throw new NotFoundException("Task", taskKey);
		}
	}
	
	/**
	 * @param jql
	 * @param maxIssues maximum number of issues to return.  If zero, unlimited.
	 * @param startAt
	 * @return
	 * @throws JiraException 
	 */
	private List<Issue> searchIssues (String jql, int maxIssues, int startAt) throws JiraException {
		
		List<Issue> issues = new ArrayList<Issue>();
		boolean moreToRecover = true;
		
		while (moreToRecover) {
			Issue.SearchResult searchResult = getJiraClient().searchIssues(jql, INCLUDED_FIELDS, CHUNK_SIZE, startAt);
			issues.addAll(searchResult.issues);
			//Have we captured all the issues that Jira says are available? Have we captured as many as were requested?
			if (searchResult.total > issues.size() && (maxIssues == 0 || issues.size() < maxIssues)) {
				startAt += CHUNK_SIZE;
			} else {
				moreToRecover = false;
			}
		}
		return issues;
		
	}

	public List<AuthoringTask> listMyTasks(String username) throws JiraException, RestClientException {
		List<Issue> issues = getJiraClient().searchIssues("assignee = \"" + username + "\" AND type = \"" + AUTHORING_TASK_TYPE + "\"").issues;
		return buildAuthoringTasks(issues);
	}

	private String getProjectTaskJQL(String projectKey) {
		return "project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\"";
	}

	public AuthoringTask createTask(String projectKey, AuthoringTaskCreateRequest taskCreateRequest) throws JiraException, ServiceException {
		//The task should be assigned to the currently logged in user
		String currentUser = ims.getCurrentLogin();

		Issue jiraIssue = getJiraClient().createIssue(projectKey, AUTHORING_TASK_TYPE)
				.field(Field.SUMMARY, taskCreateRequest.getSummary())
				.field(Field.DESCRIPTION, taskCreateRequest.getDescription())
				.field(Field.ASSIGNEE, currentUser)
				.execute();

		AuthoringTask authoringTask = new AuthoringTask(jiraIssue);
		branchService.createTaskBranchAndProjectBranchIfNeeded(authoringTask.getProjectKey(), authoringTask.getKey());
		return authoringTask;
	}

	private List<AuthoringTask> buildAuthoringTasks(List<Issue> issues) throws RestClientException {
		List<AuthoringTask> allTasks = new ArrayList<>();
		//Map of task paths to tasks
		Map<String, AuthoringTask> matureTasks = new HashMap<String, AuthoringTask>();
		for (Issue issue : issues) {
			AuthoringTask task = new AuthoringTask(issue);
			allTasks.add(task);
			//We only need to recover classification and validation statuses for task that are not new ie mature
			if (!task.getStatus().equals(StateTransition.STATE_NEW)) {
				String latestClassificationJson = classificationService.getLatestClassification(PathHelper.getPath(issue.getProject().getKey(), issue.getKey()));
				task.setLatestClassificationJson(latestClassificationJson);
				matureTasks.put(PathHelper.getTaskPath(issue), task);
			}
		}

		List<String> matureTaskPaths = new ArrayList<String>(matureTasks.keySet());
		List<String> validationStatuses = getValidationStatuses(matureTaskPaths);

		if (validationStatuses == null) {
			// TODO I think we should normally throw an exception here, but logging for the moment as I think I'm having connection
			// issues with the looping REST call while debugging.
			logger.error("Failed to recover validation statuses - check logs for reason");
		} else {
			for (int a = 0; a < matureTaskPaths.size(); a++) {
				matureTasks.get(matureTaskPaths.get(a)).setLatestValidationStatus(validationStatuses.get(a));
			}
		}

		return allTasks;
	}

	private List<String> getValidationStatuses(List<String> paths) {
		List<String> statuses;
		try {
			statuses = orchestrationRestClient.retrieveValidationStatuses(paths);
		} catch (JSONException | IOException e) {
			logger.error("Failed to retrieve validation status of tasks {}", paths, e);
			statuses = new ArrayList<>();
			for (String path : paths) {
				statuses.add(FAILED_TO_RETRIEVE);
			}
		}
		return statuses;
	}

	private void getProjectOrThrow(String projectKey) {
		try {
			getJiraClient().getProject(projectKey);
		} catch (JiraException e) {
			throw new NotFoundException("Project", projectKey);
		}
	}

	public boolean taskIsState(String projectKey, String taskKey, String targetState) throws JiraException {
		Issue issue = getIssue(projectKey, taskKey);
		String currentState = issue.getStatus().getName();
		return currentState.equals(targetState);
	}

	public void addCommentLogErrors(String projectKey, String commentString) {
		try {
			final Issue projectTicket = getProjectTicket(projectKey);
			if (projectTicket != null) {
				projectTicket.addComment(commentString);
				projectTicket.update();
			} else {
				throw new IllegalArgumentException("Authoring project with key " + projectKey + " is not accessible.");
			}
		} catch (JiraException e) {
			logger.error("Failed to set message on jira ticket {}/{}: {}", projectKey, commentString, e);
		}
	}

	public void addCommentLogErrors(String projectKey, String taskKey, String commentString) {
		try {
			addComment(projectKey, taskKey, commentString);
		} catch (JiraException e) {
			logger.error("Failed to set message on jira ticket {}/{}: {}", projectKey, taskKey, commentString, e);
		}
	}

	public void addComment(String projectKey, String taskKey, String commentString)
			throws JiraException {
		Issue issue = getIssue(projectKey, taskKey);
		issue.addComment(commentString);
		issue.update(); // Pick up new comment locally too
	}

	public void doStateTransition(String projectKey, String taskKey,
			StateTransition stateTransition) {

		try {
			Issue issue = getIssue(projectKey, taskKey);
			issue.transition().execute(stateTransition.getTransition());
			issue.refresh(); // Synchronize the issue to pick up the new status.
			stateTransition.transitionSuccessful(true);
		} catch (JiraException je) {
			//Did we fail due to a state conflict which we'll say is not really an exception?
			StringBuilder sb = new StringBuilder();
			sb.append("Failed to transition issue ")
					.append(toString(projectKey, taskKey))
					.append(" via transition '")
					.append(stateTransition.getTransition())
					.append("' due to: ")
					.append(je.getMessage());
			stateTransition.transitionSuccessful(false);
			stateTransition.experiencedException(true);
			stateTransition.setErrorMessage(sb.toString());
		}
	}

	private JiraClient getJiraClient() {
		return jiraClientFactory.getInstance();
	}

	private String toString(String projectKey, String taskKey) {
		StringBuilder sb = new StringBuilder();
		sb.append(projectKey)
				.append("/")
				.append(taskKey);
		return sb.toString();
	}

	public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest updatedTask) throws BusinessServiceException,
			JiraException {

		Issue issue = getIssue(projectKey, taskKey);
		// Act on each field received
		if (updatedTask.getStatus() != null) {
			transitionTaskState(projectKey, taskKey, updatedTask.getStatus());
		}

		if (updatedTask.getReviewer() != null) {
			// Copy that pojo user into the jira issue as an rcarz user
			User jiraReviewer = getUser(updatedTask.getReviewer().getName());
			//org.ihtsdo.snowowl.authoring.single.api.pojo.User reviewer = new org.ihtsdo.snowowl.authoring.single.api.pojo.User (jiraReviewer);
			issue.update().field(AuthoringTask.JIRA_REVIEWER_FIELD, jiraReviewer).execute();
		}

		// Pick up those changes in a new Task object
		return retrieveTask(projectKey, taskKey);

	}

	private void transitionTaskState(String projectKey, String taskKey, String newStatus) throws BusinessServiceException {
		
		StateTransition stateChange;
		switch (newStatus) {
			case StateTransition.STATE_IN_REVIEW:
				stateChange = new StateTransition(StateTransition.TRANSITION_TO_IN_REVIEW);
				break;
			case StateTransition.STATE_IN_PROGRESS:
				stateChange = new StateTransition(StateTransition.TRANSITION_TO_IN_PROGRESS);
				break;
			case StateTransition.STATE_PENDING:
				stateChange = new StateTransition(StateTransition.TRANSITION_TO_PENDING);
				break;
			case StateTransition.STATE_ESCALATION:
				stateChange = new StateTransition(StateTransition.TRANSITION_TO_ESCALATION);
				break;
			case StateTransition.STATE_READY_FOR_PROMOTION:
				stateChange = new StateTransition(StateTransition.TRANSITION_TO_READY_FOR_PROMOTION);
				break;
			default: 
				throw new BusinessServiceException("Unexpected state transition requested to " + newStatus);
		}
		
		doStateTransition(projectKey, taskKey, stateChange);
		if (!stateChange.transitionSuccessful()) {
			String errorMsg = "Failed to put task into state '" + newStatus + "' due to: " + stateChange.getErrorMessage();
			if (stateChange.experiencedException()) {
				throw new BusinessServiceException(errorMsg);
			} else {
				throw new ConflictException (errorMsg);
			}
		}
		
	}

	private User getUser(String username) throws BusinessServiceException {
		try {
			return User.get(getJiraClient().getRestClient(), username);
		} catch (JiraException je) {
			throw new BusinessServiceException("Failed to recover user '" + username + "' from Jira instance.", je);
		}
	}
	
}
