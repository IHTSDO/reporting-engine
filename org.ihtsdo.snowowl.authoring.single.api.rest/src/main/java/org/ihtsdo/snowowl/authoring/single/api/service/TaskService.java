package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.NotFoundException;

import net.rcarz.jiraclient.*;

import org.ihtsdo.otf.im.utility.SecurityService;
import org.ihtsdo.otf.rest.client.OrchestrationRestClient;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import us.monoid.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TaskService {

	public static final String FAILED_TO_RETRIEVE = "Failed-to-retrieve";
	@Autowired
	private BranchService branchService;
	
	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private OrchestrationRestClient orchestrationRestClient;

	@Autowired
	private SecurityService ims;
	
	private final JiraClient jiraClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";

	public TaskService(JiraClient jiraClient) {
		this.jiraClient = jiraClient;
	}

	public List<AuthoringProject> listProjects() throws JiraException, IOException, JSONException, RestClientException {
		List<AuthoringProject> authoringProjects = new ArrayList<>();
		for (Issue issue : jiraClient.searchIssues("type = \"SCA Authoring Project\"").issues) {
			Project project = issue.getProject();
			authoringProjects.add(buildProject(project));
		}
		return authoringProjects;
	}

	private AuthoringProject buildProject(Project project) throws IOException, JSONException, RestClientException {
		final String validationStatus = orchestrationRestClient.retrieveValidationStatuses(Collections.singletonList(PathHelper.getPath(project.getKey()))).get(0);
		final String latestClassificationJson = classificationService.getLatestClassification(PathHelper.getPath(project.getKey()));
		return new AuthoringProject(project.getKey(), project.getName(), validationStatus, latestClassificationJson);
	}

	public Issue getProjectTicket(String projectKey) throws JiraException {
		final List<Issue> issues = jiraClient.searchIssues("project = " + projectKey + " AND type = \"SCA Authoring Project\"").issues;
		if (!issues.isEmpty()) {
			return issues.get(0);
		}
		return null;
	}

	public List<AuthoringTask> listTasks(String projectKey) throws JiraException, RestClientException {
		getProjectOrThrow(projectKey);
		List<Issue> issues = jiraClient.searchIssues(getProjectJQL(projectKey)).issues;
		return convertToAuthoringTasks(issues);
	}

	public AuthoringTask retrieveTask(String projectKey, String taskKey) throws JiraException, RestClientException {
		Issue issue = getIssue (projectKey, taskKey);
		return buildAuthoringTask(issue);
	}
	
	private Issue getIssue(String projectKey, String taskKey) throws JiraException {
		getProjectOrThrow(projectKey);
		List<Issue> issues = jiraClient.searchIssues(getProjectJQL(projectKey) + " AND key = " + taskKey).issues;
		if (!issues.isEmpty()) {
			return issues.get(0);
		} else {
			throw new NotFoundException("Task", taskKey);
		}		
		
	}

	public List<AuthoringTask> listMyTasks(String username) throws JiraException, RestClientException {
		List<Issue> issues = jiraClient.searchIssues("assignee = \"" + username + "\" AND type = \"" + AUTHORING_TASK_TYPE + "\"").issues;
		return convertToAuthoringTasks(issues);
	}

	private String getProjectJQL(String projectKey) {
		return "project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\"";
	}

	public AuthoringTask createTask(String projectKey, AuthoringTaskCreateRequest taskCreateRequest) throws JiraException, ServiceException {
		//The task should be assigned to the currently logged in user
		String currentUser = ims.getCurrentLogin();
		
		Issue jiraIssue = jiraClient.createIssue(projectKey, AUTHORING_TASK_TYPE)
				.field(Field.SUMMARY, taskCreateRequest.getSummary())
				.field(Field.DESCRIPTION, taskCreateRequest.getDescription())
				.field(Field.ASSIGNEE, currentUser)
				.execute();

		AuthoringTask authoringTask = new AuthoringTask(jiraIssue);
		branchService.createTaskBranchAndProjectBranchIfNeeded(authoringTask.getProjectKey(), authoringTask.getKey());
		return authoringTask;
	}

	private List<AuthoringTask> convertToAuthoringTasks(List<Issue> issues) throws RestClientException {
		List<AuthoringTask> tasks = new ArrayList<>();
		List<String> paths = new ArrayList<>();
		for (Issue issue : issues) {
			tasks.add(buildAuthoringTask(issue));
			paths.add(PathHelper.getTaskPath(issue));
		}

		List<String> validationStatuses = getValidationStatuses(paths);
		for (int a = 0; a < tasks.size(); a++) {
			tasks.get(a).setLatestValidationStatus(validationStatuses.get(a));
		}

		return tasks;
	}

	private AuthoringTask buildAuthoringTask(Issue issue) throws RestClientException {
		final String latestClassificationJson = classificationService.getLatestClassification(PathHelper.getPath(issue.getProject().getKey(), issue.getKey()));
		final AuthoringTask authoringTask = new AuthoringTask(issue, latestClassificationJson);
		authoringTask.setLatestValidationStatus(getValidationStatuses(Collections.singletonList(PathHelper.getTaskPath(issue))).get(0));
		return authoringTask;
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
		} return statuses;
	}

	private void getProjectOrThrow(String projectKey) {
		try {
			jiraClient.getProject(projectKey);
		} catch (JiraException e) {
			throw new NotFoundException("Project", projectKey);
		}
	}
	
	public boolean taskIsState(String projectKey, String taskKey, String targetState) throws JiraException {
		Issue issue = getIssue (projectKey, taskKey);
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
			Issue issue = getIssue (projectKey, taskKey);
			String currentState = issue.getStatus().getName();
			
			//If the currentState isn't the expected initial state, then refuse
			if (stateTransition.hasInitialState(currentState)) {
				issue.transition().execute(stateTransition.getTransition());
				issue.refresh(); // Synchronize the issue to pick up the new status.
				stateTransition.transitionSuccessful(true);
			} else {
				StringBuilder sb = getTransitionError(projectKey, taskKey, stateTransition) ;
				sb.append("currently being in state ")
					.append(issue.getStatus().getName());
				stateTransition.transitionSuccessful(false);
				stateTransition.setErrorMessage(sb.toString());;
			}
		} catch (JiraException je) {
			StringBuilder sb = getTransitionError (projectKey, taskKey, stateTransition);
			sb.append (je.getMessage());
			stateTransition.transitionSuccessful(false);
			stateTransition.experiencedException(true);
			stateTransition.setErrorMessage(sb.toString());
		}
		
	}
	
	private StringBuilder getTransitionError (String projectKey, String taskKey, StateTransition stateTransition) {
		StringBuilder sb = new StringBuilder ();
		sb.append("Failed to transition issue ")
		.append (toString(projectKey, taskKey))
		.append (" from status " )
		.append ( stateTransition.getInitialState())
		.append (" via transition " )
		.append ( stateTransition.getTransition())
		.append ( " due to ");
		return sb;
	}
	
	private String toString (String projectKey, String taskKey) {
		StringBuilder sb = new StringBuilder();
		sb.append (projectKey)
			.append ("/")
			.append (taskKey);
		return sb.toString();
	}

	
	
}
