package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.core.exceptions.ConflictException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import net.rcarz.jiraclient.*;
import net.sf.json.JSON;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.log4j.Level;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskUpdateRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.TaskTransferRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.JiraHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import javax.jms.JMSException;
import java.util.*;
import java.util.concurrent.ExecutionException;

public class TaskService {

	private static final String INCLUDE_ALL_FIELDS = "*all";
	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";
	private static final String TASK_STATE_CHANGE_QUEUE_NAME = "-authoring.task-state-change";

	@Autowired
	private BranchService branchService;

	@Autowired
	private UiStateService uiService;

	@Autowired
	private InstanceConfiguration instanceConfiguration;

	@Autowired
	private MessagingHelper messagingHelper;

	@Value("+{orchestration.name}")
	private String orchestrationName;

	private final ImpersonatingJiraClientFactory jiraClientFactory;
	private final String jiraExtensionBaseField;
	private final String jiraProductCodeField;

	private LoadingCache<String, ProjectDetails> projectDetailsCache;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TaskService(ImpersonatingJiraClientFactory jiraClientFactory, String jiraUsername) throws JiraException {
		this.jiraClientFactory = jiraClientFactory;

		logger.info("Fetching Jira custom field names.");
		final JiraClient jiraClientForFieldLookup = jiraClientFactory.getImpersonatingInstance(jiraUsername);
		AuthoringTask.setJiraReviewerField(JiraHelper.fieldIdLookup("Reviewer", jiraClientForFieldLookup));
		jiraExtensionBaseField = JiraHelper.fieldIdLookup("Extension Base", jiraClientForFieldLookup);
		jiraProductCodeField = JiraHelper.fieldIdLookup("Product Code", jiraClientForFieldLookup);

		init();
	}

	public void init() {
		projectDetailsCache = CacheBuilder.newBuilder().maximumSize(10000)
				.build(new CacheLoader<String, ProjectDetails>() {
					@Override
					public ProjectDetails load(String projectKey) throws BusinessServiceException {
						final Issue projectTicket = getProjectTicket(projectKey);
						return getProjectDetailsPopulatingCache(projectTicket);
					}

					@Override
					public Map<String, ProjectDetails> loadAll(Iterable<? extends String> keys) throws Exception {
						final Map<String, Issue> projectTickets = getProjectTickets(keys);
						Map<String, ProjectDetails> keyToBaseMap = new HashMap<>();
						for (String projectKey : projectTickets.keySet()) {
							keyToBaseMap.put(projectKey,
									getProjectDetailsPopulatingCache(projectTickets.get(projectKey)));
						}
						return keyToBaseMap;
					}
				});
	}

	public String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException {
		try {
			return projectDetailsCache.get(projectKey).getBaseBranchPath();
		} catch (ExecutionException e) {
			throw new BusinessServiceException("Failed to retrieve project path.", e);
		}
	}

	private ProjectDetails getProjectDetailsPopulatingCache(Issue projectMagicTicket) {
		final String base = Strings
				.nullToEmpty(JiraHelper.toStringOrNull(projectMagicTicket.getField(jiraExtensionBaseField)));
		final String productCode = Strings
				.nullToEmpty(JiraHelper.toStringOrNull(projectMagicTicket.getField(jiraProductCodeField)));
		// Update cache with recently fetched project base value
		final ProjectDetails details = new ProjectDetails(base, productCode);
		projectDetailsCache.put(projectMagicTicket.getProject().getKey(), details);
		return details;
	}

	public Issue getProjectTicket(String projectKey) throws BusinessServiceException {
		return getProjectTickets(Collections.singleton(projectKey)).get(projectKey);
	}

	private Map<String, Issue> getProjectTickets(Iterable<? extends String> projectKeys)
			throws BusinessServiceException {
		StringBuilder magicTicketQuery = new StringBuilder();
		magicTicketQuery.append("(");
		for (String projectKey : projectKeys) {
			if (magicTicketQuery.length() > 1) {
				magicTicketQuery.append(" OR ");
			}
			magicTicketQuery.append("project = ").append(projectKey);
		}
		magicTicketQuery.append(") AND type = \"SCA Authoring Project\"");

		try {
			final List<Issue> issues = getJiraClient().searchIssues(magicTicketQuery.toString()).issues;
			Map<String, Issue> issueMap = new HashMap<>();
			for (Issue issue : issues) {
				issueMap.put(issue.getProject().getKey(), issue);
			}
			return issueMap;
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to load Projects from Jira.", e);
		}
	}

	public AuthoringTask retrieveTask(String projectKey, String taskKey) throws BusinessServiceException {
		try {
			Issue issue = getIssue(projectKey, taskKey);
			final List<AuthoringTask> authoringTasks = buildAuthoringTasks(Collections.singletonList(issue));
			return !authoringTasks.isEmpty() ? authoringTasks.get(0) : null;
		} catch (JiraException e) {
			if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 404) {
				throw new ResourceNotFoundException("Task not found " + toString(projectKey, taskKey), e);
			}
			throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
		}
	}

	private Issue getIssue(String projectKey, String taskKey) throws JiraException {
		return getIssue(projectKey, taskKey, false);
	}

	private Issue getIssue(String projectKey, String taskKey, boolean includeAll) throws JiraException {
		// If we don't need all fields, then the existing implementation is
		// sufficient
		if (includeAll) {
			return getJiraClient().getIssue(taskKey, INCLUDE_ALL_FIELDS, "changelog");
		} else {
			return getJiraClient().getIssue(taskKey, INCLUDE_ALL_FIELDS);
		}
	}

	public AuthoringTask createTask(String projectKey, String username, AuthoringTaskCreateRequest taskCreateRequest)
			throws BusinessServiceException {
		Issue jiraIssue;
		try {
			jiraIssue = getJiraClient().createIssue(projectKey, AUTHORING_TASK_TYPE)
					.field(Field.SUMMARY, taskCreateRequest.getSummary())
					.field(Field.DESCRIPTION, taskCreateRequest.getDescription()).field(Field.ASSIGNEE, username)
					.execute();
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to create Jira task", e);
		}

		AuthoringTask authoringTask = new AuthoringTask(jiraIssue, getProjectBaseUsingCache(projectKey));
		branchService.createProjectBranchIfNeeded(PathHelper.getParentPath(authoringTask.getBranchPath()));
		// Task branch creation is delayed until the user starts work to prevent
		// having to rebase straight away.
		return authoringTask;
	}

	private List<AuthoringTask> buildAuthoringTasks(List<Issue> tasks) throws BusinessServiceException {
		final TimerUtil timer = new TimerUtil("BuildTaskList", Level.DEBUG);
		List<AuthoringTask> allTasks = new ArrayList<>();
		try {
			Set<String> projectKeys = new HashSet<>();
			for (Issue issue : tasks) {
				projectKeys.add(issue.getProject().getKey());
			}

			final Map<String, ProjectDetails> projectKeyToBranchBaseMap = projectDetailsCache.getAll(projectKeys);
			for (Issue issue : tasks) {
				final ProjectDetails projectDetails = projectKeyToBranchBaseMap.get(issue.getProject().getKey());
				if (instanceConfiguration.isJiraProjectVisible(projectDetails.getProductCode())) {
					AuthoringTask task = new AuthoringTask(issue, projectDetails.getBaseBranchPath());

					allTasks.add(task);
				}
			}

			timer.finish();
		} catch (ExecutionException e) {
			throw new BusinessServiceException("Failed to retrieve task list.", e);
		}
		return allTasks;
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

	public AuthoringTask updateTask(String projectKey, String taskKey, AuthoringTaskUpdateRequest taskUpdateRequest)
			throws BusinessServiceException {
		try {
			Issue issue = getIssue(projectKey, taskKey);
			// Act on each field received
			final TaskStatus status = taskUpdateRequest.getStatus();

			if (status != null) {
				Status currentStatus = issue.getStatus();
				// Don't attempt to transition to the same status
				if (!status.getLabel().equalsIgnoreCase(currentStatus.getName())) {
					if (status == TaskStatus.UNKNOWN) {
						throw new BadRequestException("Requested status is unknown.");
					}
					stateTransition(issue, status);
				}
			}

			final Issue.FluentUpdate updateRequest = issue.update();
			boolean fieldUpdates = false;

			final org.ihtsdo.snowowl.authoring.single.api.pojo.User assignee = taskUpdateRequest.getAssignee();
			TaskTransferRequest taskTransferRequest = null;
			if (assignee != null) {
				final String username = assignee.getUsername();
				if (username == null || username.isEmpty()) {
					updateRequest.field(Field.ASSIGNEE, null);
				} else {
					updateRequest.field(Field.ASSIGNEE, getUser(username));
					String currentUser = issue.getAssignee().getName();
					if (currentUser != null && !currentUser.isEmpty() && !currentUser.equalsIgnoreCase(username)) {
						taskTransferRequest = new TaskTransferRequest(currentUser, username);
					}
				}
				fieldUpdates = true;
			}

			final org.ihtsdo.snowowl.authoring.single.api.pojo.User reviewer = taskUpdateRequest.getReviewer();
			if (reviewer != null) {
				final String username = reviewer.getUsername();
				if (username == null || username.isEmpty()) {
					updateRequest.field(AuthoringTask.jiraReviewerField, null);
				} else {
					updateRequest.field(AuthoringTask.jiraReviewerField, getUser(username));
				}
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
				// If the JIRA update goes through, then we can move any
				// UI-State over if required
				if (taskTransferRequest != null) {
					try {
						uiService.transferTask(projectKey, taskKey, taskTransferRequest);
					} catch (BusinessServiceException e) {
						logger.error("Unable to transfer UI State in " + taskKey + " from "
								+ taskTransferRequest.getCurrentUser() + " to " + taskTransferRequest.getNewUser(), e);
					}
				}
			}
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to update task.", e);
		}

		// Pick up those changes in a new Task object
		return retrieveTask(projectKey, taskKey);
	}

	private User getUser(String username) throws BusinessServiceException {
		try {
			return User.get(getJiraClient().getRestClient(), username);
		} catch (JiraException je) {
			throw new BusinessServiceException("Failed to recover user '" + username + "' from Jira instance.", je);
		}
	}

	private void stateTransition(Issue issue, TaskStatus newState) throws JiraException, BusinessServiceException {
		final Transition transition = getTransitionToOrThrow(issue, newState);
		final String key = issue.getKey();
		final String newStateLabel = newState.getLabel();
		logger.info("Transition issue {} to {}", key, newStateLabel);
		issue.transition().execute(transition);
		issue.refresh();

		// Send JMS Task State Notification
		try {
			Map<String, String> properties = new HashMap<>();
			properties.put("key", key);
			properties.put("status", newStateLabel);

			// To comma separated list
			final String labelsString = issue.getLabels().toString();
			properties.put("labels", labelsString.substring(1, labelsString.length() - 1).replace(", ", ","));

			messagingHelper.send(new ActiveMQQueue(orchestrationName + TASK_STATE_CHANGE_QUEUE_NAME), properties);
		} catch (JsonProcessingException | JMSException e) {
			logger.error("Failed to send task state change notification for {} {}.", key, newStateLabel, e);
		}
	}

	private Transition getTransitionToOrThrow(Issue issue, TaskStatus newState)
			throws JiraException, BusinessServiceException {
		for (Transition transition : issue.getTransitions()) {
			if (transition.getToStatus().getName().equals(newState.getLabel())) {
				return transition;
			}
		}
		throw new ConflictException("Could not transition task " + issue.getKey() + " from status '"
				+ issue.getStatus().getName() + "' to '" + newState.name() + "', no such transition is available.");
	}

}
