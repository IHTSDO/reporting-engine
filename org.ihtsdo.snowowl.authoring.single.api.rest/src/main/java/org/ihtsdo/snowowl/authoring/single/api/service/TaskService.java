package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.core.exceptions.ConflictException;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import net.rcarz.jiraclient.*;
import org.ihtsdo.otf.rest.client.OrchestrationRestClient;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskUpdateRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
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
	private BranchService branchService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private OrchestrationRestClient orchestrationRestClient;

	@Autowired
	private ReviewService reviewService;

	private final ImpersonatingJiraClientFactory jiraClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";
	
	public TaskService(ImpersonatingJiraClientFactory jiraClientFactory, String jiraReviewerField) {
		this.jiraClientFactory = jiraClientFactory;
		AuthoringTask.setJiraReviewerField(jiraReviewerField);
	}
	
	public List<AuthoringProject> listProjects() throws JiraException, IOException, JSONException, RestClientException {
		List<AuthoringProject> authoringProjects = new ArrayList<>();
		final JiraClient jiraClient = getJiraClient();
		for (Issue issue : jiraClient.searchIssues("type = \"SCA Authoring Project\"").issues) {
			// Get project with all fields
			Project project = jiraClient.getProject(issue.getProject().getKey());
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
		return new AuthoringProject(project.getKey(), project.getName(), getPojoUserOrNull(project.getLead()), validationStatus, latestClassificationJson);
	}

	public Issue getProjectTicketOrThrow(String projectKey) {
		Issue issue = null;
		try {
			issue = getProjectTicket(projectKey);
		} catch (JiraException e) {
			logger.error("Failed to load project ticket {}", projectKey, e);
		}
		if (issue == null) {
			throw new IllegalArgumentException("Authoring project with key " + projectKey + " is not accessible.");
		}
		return issue;
	}

	public Issue getProjectTicket(String projectKey) throws JiraException {
		final List<Issue> issues = getJiraClient().searchIssues("project = " + projectKey + " AND type = \"SCA Authoring Project\"").issues;
		if (!issues.isEmpty()) {
			return issues.get(0);
		}
		return null;
	}

	public List<AuthoringTask> listTasks(String projectKey) throws JiraException, RestClientException, ServiceException {
		getProjectOrThrow(projectKey);
		List<Issue> issues = searchIssues(getProjectTaskJQL(projectKey), 0, 0);  //unlimited recovery for now
		return buildAuthoringTasks(issues);
	}

	public AuthoringTask retrieveTask(String projectKey, String taskKey) throws BusinessServiceException {
		try {
			Issue issue = getIssue(projectKey, taskKey);
			return buildAuthoringTasks(Collections.singletonList(issue)).get(0);
		} catch (JiraException | RestClientException | ServiceException e) {
			throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
		}
	}

	private Issue getIssue(String projectKey, String taskKey) throws JiraException {
		return getIssue(projectKey, taskKey, false);
	}
	
	private Issue getIssue(String projectKey, String taskKey, boolean includeAll) throws JiraException {
		//If we don't need all fields, then the existing implementation is sufficient
		if (includeAll) {
			return getJiraClient().getIssue(taskKey, INCLUDED_FIELDS, "changelog");
		} else {
			return getJiraClient().getIssue(taskKey, INCLUDED_FIELDS);
		}
	}
	
	/**
	 * @param jql
	 * @param maxIssues maximum number of issues to return.  If zero, unlimited.
	 * @param startAt
	 * @return
	 * @throws JiraException 
	 */
	private List<Issue> searchIssues(String jql, int maxIssues, int startAt) throws JiraException {
		
		List<Issue> issues = new ArrayList<>();
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

	public List<AuthoringTask> listMyTasks(String username) throws JiraException, RestClientException, ServiceException {
		List<Issue> issues = getJiraClient().searchIssues("assignee = \"" + username + "\" AND type = \"" + AUTHORING_TASK_TYPE + "\"").issues;
		return buildAuthoringTasks(issues);
	}

	private String getProjectTaskJQL(String projectKey) {
		return "project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\"";
	}

	public AuthoringTask createTask(String projectKey, AuthoringTaskCreateRequest taskCreateRequest) throws JiraException, ServiceException {
		Issue jiraIssue = getJiraClient().createIssue(projectKey, AUTHORING_TASK_TYPE)
				.field(Field.SUMMARY, taskCreateRequest.getSummary())
				.field(Field.DESCRIPTION, taskCreateRequest.getDescription())
				.field(Field.ASSIGNEE, getUsername())
				.execute();

		AuthoringTask authoringTask = new AuthoringTask(jiraIssue);
		branchService.createTaskBranchAndProjectBranchIfNeeded(authoringTask.getProjectKey(), authoringTask.getKey());
		return authoringTask;
	}

	private List<AuthoringTask> buildAuthoringTasks(List<Issue> issues) throws RestClientException, ServiceException {
		final String username = getUsername();
		List<AuthoringTask> allTasks = new ArrayList<>();
		//Map of task paths to tasks
		Map<String, AuthoringTask> startedTasks = new HashMap<>();
		for (Issue issue : issues) {
			AuthoringTask task = new AuthoringTask(issue);
			allTasks.add(task);
			//We only need to recover classification and validation statuses for task that are not new ie mature
			if (task.getStatus() != TaskStatus.NEW) {
				final String projectKey = issue.getProject().getKey();
				final String issueKey = issue.getKey();
				String latestClassificationJson = classificationService.getLatestClassification(PathHelper.getPath(projectKey, issueKey));
				task.setLatestClassificationJson(latestClassificationJson);
				task.setBranchState(branchService.getBranchStateNoThrow(projectKey, issueKey));
				startedTasks.put(PathHelper.getTaskPath(issue), task);
			}
			task.setFeedbackMessagesStatus(reviewService.getTaskMessagesStatus(task.getProjectKey(), task.getKey(), username));
		}

		List<String> startedTaskPaths = new ArrayList<>(startedTasks.keySet());
		List<String> validationStatuses = getValidationStatuses(startedTaskPaths);

		if (validationStatuses == null) {
			// TODO I think we should normally throw an exception here, but logging for the moment as I think I'm having connection
			// issues with the looping REST call while debugging.
			logger.error("Failed to recover validation statuses - check logs for reason");
		} else {
			for (int a = 0; a < startedTaskPaths.size(); a++) {
				startedTasks.get(startedTaskPaths.get(a)).setLatestValidationStatus(validationStatuses.get(a));
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
			for (int i = 0; i < paths.size(); i++) {
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

	public boolean taskIsState(String projectKey, String taskKey, TaskStatus state) throws JiraException {
		Issue issue = getIssue(projectKey, taskKey);
		String currentState = issue.getStatus().getName();
		return currentState.equals(state.getLabel());
	}

	public void addCommentLogErrors(String projectKey, String commentString) {
		final Issue projectTicket = getProjectTicketOrThrow(projectKey);
		addCommentLogErrors(projectKey, projectTicket.getKey(), commentString);
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

	private JiraClient getJiraClient() {
		return jiraClientFactory.getImpersonatingInstance(getUsername());
	}

	private String getUsername() {
		return ControllerHelper.getUsername();
	}

	private String toString(String projectKey, String taskKey) {
		return projectKey + "/" + taskKey;
	}

	public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest updatedTask) throws BusinessServiceException,
			JiraException {

		Issue issue = getIssue(projectKey, taskKey);
		// Act on each field received
		final TaskStatus status = updatedTask.getStatus();
		if (status != null) {
			if (status == TaskStatus.UNKNOWN) {
				throw new BadRequestException("Requested status is unknown.");
			}
			stateTransition(issue, status);
		}

		if (updatedTask.getReviewer() != null) {
			// Copy that pojo user into the jira issue as an rcarz user
			User jiraReviewer = getUser(updatedTask.getReviewer().getUsername());
			//org.ihtsdo.snowowl.authoring.single.api.pojo.User reviewer = new org.ihtsdo.snowowl.authoring.single.api.pojo.User (jiraReviewer);
			issue.update().field(AuthoringTask.JIRA_REVIEWER_FIELD, jiraReviewer).execute();
		}

		// Pick up those changes in a new Task object
		return retrieveTask(projectKey, taskKey);

	}

	private org.ihtsdo.snowowl.authoring.single.api.pojo.User getPojoUserOrNull(User lead) {
		org.ihtsdo.snowowl.authoring.single.api.pojo.User leadUser = null;
		if (lead != null) {
			leadUser = new org.ihtsdo.snowowl.authoring.single.api.pojo.User(lead);
		}
		return leadUser;
	}

	private User getUser(String username) throws BusinessServiceException {
		try {
			return User.get(getJiraClient().getRestClient(), username);
		} catch (JiraException je) {
			throw new BusinessServiceException("Failed to recover user '" + username + "' from Jira instance.", je);
		}
	}
	
	/**
	 * Returns the most recent Date/time of when the specified field changed to the specified value
	 * @throws BusinessServiceException 
	 */
	public Date getDateOfChange(String projectKey, String taskKey, String fieldName, String newValue) throws JiraException, BusinessServiceException {

		try {
			//Recover the change log for the issue and work through it to find the change specified
			ChangeLog changeLog = getIssue(projectKey, taskKey, true).getChangeLog();
			if (changeLog != null) {
				//Sort changeLog entries descending to get most recent change first
				Collections.sort(changeLog.getEntries(),CHANGELOG_ID_COMPARATOR_DESC);
				for (ChangeLogEntry entry : changeLog.getEntries()) {
					Date thisChangeDate = entry.getCreated();
					for (ChangeLogItem changeItem : entry.getItems()) {
						if (changeItem.getField().equals(fieldName) && changeItem.getToString().equals(newValue)){
							return thisChangeDate;
						}
					}
				}
			}
		} catch (JiraException je) {
			throw new BusinessServiceException("Failed to recover change log from task " + toString(projectKey, taskKey), je);
		}
		return null;
	}
	
	public static Comparator<ChangeLogEntry> CHANGELOG_ID_COMPARATOR_DESC = new Comparator<ChangeLogEntry>() {
		public int compare(ChangeLogEntry entry1, ChangeLogEntry entry2) {
			Integer id1 = new Integer(entry1.getId());
			Integer id2 = new Integer(entry2.getId());
			return id2.compareTo(id1);
		}
	};

	public boolean conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState, TaskStatus newState) throws JiraException, BusinessServiceException {
		final Issue issue = getIssue(projectKey, taskKey);
		if (TaskStatus.fromLabel(issue.getStatus().getName()) == requiredState) {
			stateTransition(issue, newState);
			return true;
		}
		return false;
	}

	public void stateTransition(String projectKey, String taskKey, TaskStatus newState) throws JiraException, BusinessServiceException {
		stateTransition(getIssue(projectKey, taskKey), newState);
	}

	private void stateTransition(Issue issue, TaskStatus newState) throws JiraException, BusinessServiceException {
		final Transition transition = getTransitionToOrThrow(issue, newState);
		logger.info("Transition issue {} to {}", issue.getKey(), newState.getLabel());
		issue.transition().execute(transition);
		issue.refresh();
	}

	private Transition getTransitionToOrThrow(Issue issue, TaskStatus newState) throws JiraException, BusinessServiceException {
		for (Transition transition : issue.getTransitions()) {
			if (transition.getToStatus().getName().equals(newState.getLabel())) {
				return transition;
			}
		}
		throw new ConflictException("Could not transition task " + issue.getKey() + " from status '" + issue.getStatus().getName() + "' to '" + newState.name() + "', no such transition is available.");
	}

}
