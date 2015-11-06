package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.core.exceptions.ConflictException;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.b2international.snowowl.datastore.branch.Branch;
import com.google.common.collect.ImmutableMap;

import net.rcarz.jiraclient.*;

import net.rcarz.jiraclient.User;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.*;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.snowowl.authoring.single.api.service.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.concurrent.ExecutionException;


public class TaskService {

	public static final String FAILED_TO_RETRIEVE = "Failed-to-retrieve";
	
	private static final String INCLUDED_FIELDS = "*all";
	private static final int CHUNK_SIZE = 50;

	@Autowired
	private BranchService branchService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private ValidationService validationService;

	private final ImpersonatingJiraClientFactory jiraClientFactory;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";
	
	public TaskService(ImpersonatingJiraClientFactory jiraClientFactory, String jiraReviewerField) {
		this.jiraClientFactory = jiraClientFactory;
		AuthoringTask.setJiraReviewerField(jiraReviewerField);
	}
	
	public List<AuthoringProject> listProjects() throws JiraException, BusinessServiceException {
		final TimerUtil timer = new TimerUtil("ProjectsList");
		final JiraClient jiraClient = getJiraClient();
		List<Project> projects = new ArrayList<>();
		for (Issue issue : jiraClient.searchIssues("type = \"SCA Authoring Project\"").issues) {
			projects.add(jiraClient.getProject(issue.getProject().getKey()));
		}
		timer.checkpoint("Jira searches");
		final List<AuthoringProject> authoringProjects = buildAuthoringProjects(projects);
		timer.checkpoint("validation and classification");
		timer.finish();
		return authoringProjects;
	}

	public AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException {
		return buildAuthoringProjects(Collections.singletonList(getProjectTicket(projectKey).getProject())).get(0);
	}

	private List<AuthoringProject> buildAuthoringProjects(List<Project> projects) throws BusinessServiceException {
		try {
			List<AuthoringProject> authoringProjects = new ArrayList<>();
			Map<Project, String> paths = new HashMap<>();
			for (Project project : projects) {
				paths.put(project, PathHelper.getPath(project.getKey()));
			}
			final ImmutableMap<String, String> statuses = validationService.getValidationStatuses(paths.values());
			for (Project project : projects) {
				final String latestClassificationJson = classificationService.getLatestClassification(project.getKey(), null);
				Branch.BranchState branchState = branchService.getBranchStateNoThrow(project.getKey(), null);
				authoringProjects.add(new AuthoringProject(project.getKey(), project.getName(), getPojoUserOrNull(project.getLead()), branchState, statuses.get(paths.get(project)), latestClassificationJson));
			}
			return authoringProjects;
		} catch (ExecutionException | RestClientException e) {
			throw new BusinessServiceException("Failed to retrieve Projects", e);
		}
	}

	public AuthoringMain retrieveMain() throws BusinessServiceException {
		return buildAuthoringMain();
	}
	
	private AuthoringMain buildAuthoringMain() throws BusinessServiceException {
		try {
			String path = PathHelper.getPath(null);
			Collection<String> paths = Collections.singletonList(path);
			final ImmutableMap<String, String> statuses = validationService.getValidationStatuses(paths);
			final Branch.BranchState branchState = branchService.getBranchStateNoThrow(null, null);
			final String latestClassificationJson = classificationService.getLatestClassification(null, null);
			return new AuthoringMain(path, branchState, statuses.get(path), latestClassificationJson);
		} catch (ExecutionException | RestClientException e) {
			throw new BusinessServiceException("Failed to retrieve Main", e);
		}
	}

	public Issue getProjectTicketOrThrow(String projectKey) {
		Issue issue = null;
		try {
			issue = getProjectTicket(projectKey);
		} catch (BusinessServiceException e) {
			logger.error("Failed to load project ticket {}", projectKey, e);
		}
		if (issue == null) {
			throw new IllegalArgumentException("Authoring project with key " + projectKey + " is not accessible.");
		}
		return issue;
	}

	public Issue getProjectTicket(String projectKey) throws BusinessServiceException {
		try {
			final List<Issue> issues = getJiraClient().searchIssues("project = " + projectKey + " AND type = \"SCA Authoring Project\"").issues;
			if (!issues.isEmpty()) {
				return issues.get(0);
			}
			return null;
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to load project '" + projectKey + "' from Jira.", e);
		}
	}

	public List<AuthoringTask> listTasks(String projectKey) throws JiraException, BusinessServiceException {
		getProjectOrThrow(projectKey);
		List<Issue> issues = searchIssues(getProjectTaskJQL(projectKey), 0, 0);  //unlimited recovery for now
		return buildAuthoringTasks(issues);
	}

	public AuthoringTask retrieveTask(String projectKey, String taskKey) throws BusinessServiceException {
		try {
			Issue issue = getIssue(projectKey, taskKey);
			return buildAuthoringTasks(Collections.singletonList(issue)).get(0);
		} catch (JiraException | BusinessServiceException e) {
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

	public List<AuthoringTask> listMyTasks(String username) throws JiraException, BusinessServiceException {
		List<Issue> issues = getJiraClient().searchIssues("assignee = \"" + username + "\" AND type = \"" + AUTHORING_TASK_TYPE + "\"").issues;
		return buildAuthoringTasks(issues);
	}

	public List<AuthoringTask> listMyOrUnassignedReviewTasks() throws JiraException, BusinessServiceException {
		return buildAuthoringTasks(getJiraClient().searchIssues("type = \"" + AUTHORING_TASK_TYPE + "\" " +
						"AND assignee != currentUser() " +
						"AND (Reviewer = currentUser() OR (Reviewer = null AND status = \"" + TaskStatus.IN_REVIEW.getLabel() + "\"))").issues);
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

	private List<AuthoringTask> buildAuthoringTasks(List<Issue> issues) throws BusinessServiceException {
		final TimerUtil timer = new TimerUtil("BuildTaskList");
		final String username = getUsername();
		List<AuthoringTask> allTasks = new ArrayList<>();
		try {
			//Map of task paths to tasks
			Map<String, AuthoringTask> startedTasks = new HashMap<>();
			for (Issue issue : issues) {
				AuthoringTask task = new AuthoringTask(issue);
				allTasks.add(task);
				//We only need to recover classification and validation statuses for task that are not new ie mature
				if (task.getStatus() != TaskStatus.NEW) {
					final String projectKey = issue.getProject().getKey();
					final String issueKey = issue.getKey();
					String latestClassificationJson = classificationService.getLatestClassification(projectKey, issueKey);
					timer.checkpoint("Recovering classification");
					task.setLatestClassificationJson(latestClassificationJson);
					task.setBranchState(branchService.getBranchStateNoThrow(projectKey, issueKey));
					timer.checkpoint("Recovering branch state");
					startedTasks.put(PathHelper.getTaskPath(issue), task);
				}
				task.setFeedbackMessagesStatus(reviewService.getTaskMessagesStatus(task.getProjectKey(), task.getKey(), username));
				timer.checkpoint("Recovering feedback messages");
			}

			final ImmutableMap<String, String> validationStatuses = validationService.getValidationStatuses(startedTasks.keySet());
			timer.checkpoint("Recovering " + validationStatuses.size() + " ValidationStatuses");

			if (validationStatuses == null || validationStatuses.size() == 0) {
				logger.error("Failed to recover validation statuses - check logs for reason");
			} else {
				for (final String path : startedTasks.keySet()) {
					startedTasks.get(path).setLatestValidationStatus(validationStatuses.get(path));
				}
			}
			timer.finish();
		} catch (ExecutionException | RestClientException e) {
			throw new BusinessServiceException("Failed to retrieve task list.", e);
		}
		return allTasks;
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

	public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest) throws BusinessServiceException,
			JiraException {

		Issue issue = getIssue(projectKey, taskKey);
		// Act on each field received
		final TaskStatus status = taskUpdateRequest.getStatus();
		if (status != null) {
			if (status == TaskStatus.UNKNOWN) {
				throw new BadRequestException("Requested status is unknown.");
			}
			stateTransition(issue, status);
		}

		final Issue.FluentUpdate updateRequest = issue.update();
		boolean fieldUpdates = false;

		final org.ihtsdo.snowowl.authoring.single.api.pojo.User assignee = taskUpdateRequest.getAssignee();
		if (assignee != null) {
			final String username = assignee.getUsername();
			if (username == null || username.isEmpty()) {
				updateRequest.field(Field.ASSIGNEE, null);
			} else {
				updateRequest.field(Field.ASSIGNEE, getUser(username));
			}
			fieldUpdates = true;
		}

		final org.ihtsdo.snowowl.authoring.single.api.pojo.User reviewer = taskUpdateRequest.getReviewer();
		if (reviewer != null) {
			updateRequest.field(AuthoringTask.JIRA_REVIEWER_FIELD, getUser(reviewer.getUsername()));
			fieldUpdates = true;
		}

		final String summary = taskUpdateRequest.getSummary();
		if (summary != null) {
			updateRequest.field(Field.SUMMARY, summary);
			fieldUpdates = true;
		}

		final String description = taskUpdateRequest.getDescription();
		if (description != null) {
			updateRequest.field(Field.DESCRIPTION, description);
			fieldUpdates = true;
		}

		if (fieldUpdates) {
			updateRequest.execute();
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
