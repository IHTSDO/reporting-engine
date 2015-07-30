package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.NotFoundException;
import net.rcarz.jiraclient.*;
import org.ihtsdo.otf.im.utility.SecurityService;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;

public class TaskService {

	@Autowired
	private BranchService branchService;
	
	@Autowired
	private ClassificationService classificationService;

	@Autowired
	SecurityService ims;
	
	private final JiraClient jiraClient;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";

	public TaskService(JiraClient jiraClient) {
		this.jiraClient = jiraClient;
	}

	public List<AuthoringProject> listProjects() throws JiraException {
		List<AuthoringProject> authoringProjects = new ArrayList<>();
		for (Issue issue : jiraClient.searchIssues("type = \"SCA Authoring Project\"").issues) {
			Project project = issue.getProject();
			authoringProjects.add(new AuthoringProject(project.getKey(), project.getName()));
		}
		return authoringProjects;
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
		return getAuthoringTask(issue);
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
		for (Issue issue : issues) {
			tasks.add(getAuthoringTask(issue));
		}
		return tasks;
	}

	private AuthoringTask getAuthoringTask(Issue issue) throws RestClientException {
		final String latestClassificationJson = classificationService.getLatestClassification(issue.getProject().getKey(), issue.getKey());
		return new AuthoringTask(issue, latestClassificationJson);
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
