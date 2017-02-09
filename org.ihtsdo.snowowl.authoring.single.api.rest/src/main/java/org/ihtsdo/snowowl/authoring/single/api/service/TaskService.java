package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.Metadata;
import com.b2international.snowowl.core.branch.Branch;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.core.exceptions.ConflictException;
import com.b2international.snowowl.core.exceptions.NotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
import net.rcarz.jiraclient.*;
import net.rcarz.jiraclient.Status;
import net.rcarz.jiraclient.User;
import net.sf.json.JSON;
import org.apache.activemq.command.ActiveMQQueue;
import org.apache.log4j.Level;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.*;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.ihtsdo.snowowl.authoring.single.api.review.service.TaskMessagesDetail;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.ImpersonatingJiraClientFactory;
import org.ihtsdo.snowowl.authoring.single.api.service.jira.JiraHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.util.TimerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.*;
import java.util.concurrent.ExecutionException;
import javax.jms.JMSException;

public class TaskService {

	public static final String FAILED_TO_RETRIEVE = "Failed-to-retrieve";

	private static final String INCLUDE_ALL_FIELDS = "*all";
	private static final String EXCLUDE_STATUSES = " AND (status != \"" + TaskStatus.COMPLETED.getLabel()
			+ "\" AND status != \"" + TaskStatus.DELETED.getLabel() + "\") ";
	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";
	private static final int LIMIT_UNLIMITED = -1;
	private static final String TASK_STATE_CHANGE_QUEUE_NAME = "-authoring.task-state-change";

	@Autowired
	private BranchService branchService;

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private ReviewService reviewService;

	@Autowired
	private ValidationService validationService;

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
	private final String jiraProjectPromotionField;
	private final String jiraProjectMrcmField;
	private final String jiraCrsIdField;
	private final String jiraProjectTemplatesField;

	private LoadingCache<String, ProjectDetails> projectDetailsCache;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public TaskService(ImpersonatingJiraClientFactory jiraClientFactory, String jiraUsername) throws JiraException {
		this.jiraClientFactory = jiraClientFactory;

		logger.info("Fetching Jira custom field names.");
		final JiraClient jiraClientForFieldLookup = jiraClientFactory.getImpersonatingInstance(jiraUsername);
		AuthoringTask.setJiraReviewerField(JiraHelper.fieldIdLookup("Reviewer", jiraClientForFieldLookup));
		jiraExtensionBaseField = JiraHelper.fieldIdLookup("Extension Base", jiraClientForFieldLookup);
		jiraProductCodeField = JiraHelper.fieldIdLookup("Product Code", jiraClientForFieldLookup);
		jiraProjectPromotionField = JiraHelper.fieldIdLookup("SCA Project Promotion", jiraClientForFieldLookup);
		jiraProjectMrcmField = JiraHelper.fieldIdLookup("SCA Project MRCM", jiraClientForFieldLookup);
		jiraCrsIdField = JiraHelper.fieldIdLookup("CRS-ID", jiraClientForFieldLookup);
		jiraProjectTemplatesField = JiraHelper.fieldIdLookup("SCA Project Templates", jiraClientForFieldLookup);

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

	public List<AuthoringProject> listProjects() throws JiraException, BusinessServiceException {
		final TimerUtil timer = new TimerUtil("ProjectsList");
		final JiraClient jiraClient = getJiraClient();
		List<Project> projects = new ArrayList<>();
		for (Issue projectMagicTicket : searchIssues("type = \"SCA Authoring Project\"", -1)) {
			final String productCode = getProjectDetailsPopulatingCache(projectMagicTicket).getProductCode();
			if (instanceConfiguration.isJiraProjectVisible(productCode)) {
				projects.add(jiraClient.getProject(projectMagicTicket.getProject().getKey()));
			}
		}
		timer.checkpoint("Jira searches");
		final List<AuthoringProject> authoringProjects = buildAuthoringProjects(projects);
		timer.checkpoint("validation and classification");
		timer.finish();
		return authoringProjects;
	}

	public AuthoringProject retrieveProject(String projectKey) throws BusinessServiceException {
		return buildAuthoringProjects(Collections.singletonList(getProjectTicketOrThrow(projectKey).getProject()))
				.get(0);
	}

	public String getProjectBranchPathUsingCache(String projectKey) throws BusinessServiceException {
		return PathHelper.getProjectPath(getProjectBaseUsingCache(projectKey), projectKey);
	}

	public String getProjectBaseUsingCache(String projectKey) throws BusinessServiceException {
		try {
			return projectDetailsCache.get(projectKey).getBaseBranchPath();
		} catch (ExecutionException e) {
			throw new BusinessServiceException("Failed to retrieve project path.", e);
		}
	}

	private List<AuthoringProject> buildAuthoringProjects(List<Project> projects) throws BusinessServiceException {
		if (projects.isEmpty()) {
			return new ArrayList<>();
		}
		try {
			List<AuthoringProject> authoringProjects = new ArrayList<>();
			Set<String> projectKeys = new HashSet<>();
			for (Project project : projects) {
				projectKeys.add(project.getKey());
			}
			final Map<String, Issue> projectMagicTickets = getProjectTickets(projectKeys);

			Set<String> branchPaths = new HashSet<>();
			for (Project project : projects) {
				final String key = project.getKey();
				final Issue magicTicket = projectMagicTickets.get(key);
				final String extensionBase = getProjectDetailsPopulatingCache(magicTicket).getBaseBranchPath();
				final String branchPath = PathHelper.getProjectPath(extensionBase, key);
				final String latestClassificationJson = classificationService.getLatestClassification(branchPath);
				final boolean promotionDisabled = "Disabled".equals(JiraHelper.toStringOrNull(magicTicket.getField(jiraProjectPromotionField)));
				final boolean mrcmDisabled = "Disabled".equals(JiraHelper.toStringOrNull(magicTicket.getField(jiraProjectMrcmField)));
				final boolean templatesDisabled = "Disabled".equals(JiraHelper.toStringOrNull(magicTicket.getField(jiraProjectTemplatesField)));

				
				final Branch branchOrNull = branchService.getBranchOrNull(branchPath);
				final Branch parentBranchOrNull = branchService.getBranchOrNull(PathHelper.getParentPath(branchPath));
				Branch.BranchState branchState = null;
				Metadata metadata = null;
				if (branchOrNull != null) {
					branchState = branchOrNull.state();
					if (parentBranchOrNull != null) {
						metadata = parentBranchOrNull.metadata();
					}
				}
				branchPaths.add(branchPath);
				final AuthoringProject authoringProject = new AuthoringProject(project.getKey(), project.getName(),
						getPojoUserOrNull(project.getLead()), branchPath, branchState, latestClassificationJson,
						promotionDisabled, mrcmDisabled, templatesDisabled);
				authoringProject.setMetadata(metadata);
				authoringProjects.add(authoringProject);
			}
			final ImmutableMap<String, String> validationStatuses = validationService
					.getValidationStatuses(branchPaths);
			for (AuthoringProject authoringProject : authoringProjects) {
				authoringProject.setValidationStatus(validationStatuses.get(authoringProject.getBranchPath()));
			}
			return authoringProjects;
		} catch (ExecutionException | RestClientException e) {
			throw new BusinessServiceException("Failed to retrieve Projects", e);
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

	public AuthoringMain retrieveMain() throws BusinessServiceException {
		return buildAuthoringMain();
	}

	private AuthoringMain buildAuthoringMain() throws BusinessServiceException {
		try {
			String path = PathHelper.getProjectPath(null, null);
			Collection<String> paths = Collections.singletonList(path);
			final ImmutableMap<String, String> statuses = validationService.getValidationStatuses(paths);
			final Branch.BranchState branchState = branchService.getBranchStateOrNull(PathHelper.getMainPath());
			final String latestClassificationJson = classificationService
					.getLatestClassification(PathHelper.getMainPath());
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

	public List<Issue> getTaskIssues(String projectKey, TaskStatus taskStatus) throws BusinessServiceException {
		try {
			return getJiraClient().searchIssues(getProjectTaskJQL(projectKey, taskStatus), -1).issues;
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to load tasks in project '" + projectKey + "' from Jira.", e);
		}
	}

	public List<AuthoringTask> listTasks(String projectKey) throws BusinessServiceException {
		getProjectOrThrow(projectKey);
		List<Issue> issues;
		try {
			issues = searchIssues(getProjectTaskJQL(projectKey, null), LIMIT_UNLIMITED);
		} catch (JiraException e) {
			throw new BusinessServiceException("Failed to list tasks.", e);
		}
		return buildAuthoringTasks(issues);
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

	public String getTaskBranchPathUsingCache(String projectKey, String taskKey) throws BusinessServiceException {
		return PathHelper.getTaskPath(getProjectBaseUsingCache(projectKey), projectKey, taskKey);
	}

	public String getBranchPathUsingCache(String projectKey, String taskKey) throws BusinessServiceException {
		if (!Strings.isNullOrEmpty(projectKey)) {
			final String extensionBase = getProjectBaseUsingCache(projectKey);
			if (!Strings.isNullOrEmpty(taskKey)) {
				return PathHelper.getTaskPath(extensionBase, projectKey, taskKey);
			}
			return PathHelper.getProjectPath(extensionBase, projectKey);
		}
		return null;
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

	/**
	 * @param jql
	 * @param limit
	 *            maximum number of issues to return. If -1 the results are
	 *            unlimited.
	 * @return
	 * @throws JiraException
	 */
	private List<Issue> searchIssues(String jql, int limit) throws JiraException {
		List<Issue> issues = new ArrayList<>();
		Issue.SearchResult searchResult;
		do {
			searchResult = getJiraClient().searchIssues(jql, INCLUDE_ALL_FIELDS, limit - issues.size(), issues.size());
			issues.addAll(searchResult.issues);
		} while (searchResult.total > issues.size() && (limit == LIMIT_UNLIMITED || issues.size() < limit));

		return issues;

	}

	public List<AuthoringTask> listMyTasks(String username) throws JiraException, BusinessServiceException {
		List<Issue> issues = searchIssues(
				"assignee = \"" + username + "\" AND type = \"" + AUTHORING_TASK_TYPE + "\" " + EXCLUDE_STATUSES,
				LIMIT_UNLIMITED);
		return buildAuthoringTasks(issues);
	}

	public List<AuthoringTask> listMyOrUnassignedReviewTasks() throws JiraException, BusinessServiceException {
		List<Issue> issues = searchIssues("type = \"" + AUTHORING_TASK_TYPE + "\" " + "AND assignee != currentUser() "
				+ "AND (Reviewer = currentUser() OR (Reviewer = null AND status = \"" + TaskStatus.IN_REVIEW.getLabel()
				+ "\")) " + EXCLUDE_STATUSES, LIMIT_UNLIMITED);
		return buildAuthoringTasks(issues);
	}

	private String getProjectTaskJQL(String projectKey, TaskStatus taskStatus) {
		String jql = "project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\" " + EXCLUDE_STATUSES;
		if (taskStatus != null) {
			jql += " AND status = \"" + taskStatus.getLabel() + "\"";
		}
		return jql;
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
		try {
			branchService.createProjectBranchIfNeeded(PathHelper.getParentPath(authoringTask.getBranchPath()));
		} catch (ServiceException e) {
			throw new BusinessServiceException("Failed to create project branch.", e);
		}
		// Task branch creation is delayed until the user starts work to prevent
		// having to rebase straight away.
		return authoringTask;
	}

	public AuthoringTask createTask(String projectKey, AuthoringTaskCreateRequest taskCreateRequest)
			throws BusinessServiceException {
		return createTask(projectKey, getUsername(), taskCreateRequest);
	}

	private List<AuthoringTask> buildAuthoringTasks(List<Issue> tasks) throws BusinessServiceException {
		final TimerUtil timer = new TimerUtil("BuildTaskList", Level.DEBUG);
		final String username = getUsername();
		List<AuthoringTask> allTasks = new ArrayList<>();
		try {
			// Map of task paths to tasks
			Map<String, AuthoringTask> startedTasks = new HashMap<>();

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
					// We only need to recover classification and validation
					// statuses for task that are not new ie mature
					if (task.getStatus() != TaskStatus.NEW) {
						String latestClassificationJson = classificationService
								.getLatestClassification(task.getBranchPath());
						timer.checkpoint("Recovering classification");
						task.setLatestClassificationJson(latestClassificationJson);
						task.setBranchState(branchService.getBranchStateOrNull(task.getBranchPath()));
						timer.checkpoint("Recovering branch state");
						startedTasks.put(task.getBranchPath(), task);
					}

					// get the review message details and append to task
					TaskMessagesDetail detail = reviewService.getTaskMessagesDetail(task.getProjectKey(), task.getKey(),
							username);
					task.setFeedbackMessagesStatus(detail.getTaskMessagesStatus());
					task.setFeedbackMessageDate(detail.getLastMessageDate());
					task.setViewDate(detail.getViewDate());
					timer.checkpoint("Recovering feedback messages");

				}
			}

			final ImmutableMap<String, String> validationStatuses = validationService
					.getValidationStatuses(startedTasks.keySet());
			timer.checkpoint("Recovering " + (validationStatuses == null ? "null" : validationStatuses.size())
					+ " ValidationStatuses");

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

	public void addComment(String projectKey, String taskKey, String commentString) throws JiraException {
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
	 * Returns the most recent Date/time of when the specified field changed to
	 * the specified value
	 *
	 * @throws BusinessServiceException
	 */
	public Date getDateOfChange(String projectKey, String taskKey, String fieldName, String newValue)
			throws JiraException, BusinessServiceException {

		try {
			// Recover the change log for the issue and work through it to find
			// the change specified
			ChangeLog changeLog = getIssue(projectKey, taskKey, true).getChangeLog();
			if (changeLog != null) {
				// Sort changeLog entries descending to get most recent change
				// first
				Collections.sort(changeLog.getEntries(), CHANGELOG_ID_COMPARATOR_DESC);
				for (ChangeLogEntry entry : changeLog.getEntries()) {
					Date thisChangeDate = entry.getCreated();
					for (ChangeLogItem changeItem : entry.getItems()) {
						if (changeItem.getField().equals(fieldName) && changeItem.getToString().equals(newValue)) {
							return thisChangeDate;
						}
					}
				}
			}
		} catch (JiraException je) {
			throw new BusinessServiceException(
					"Failed to recover change log from task " + toString(projectKey, taskKey), je);
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

	public boolean conditionalStateTransition(String projectKey, String taskKey, TaskStatus requiredState,
			TaskStatus newState) throws JiraException, BusinessServiceException {
		final Issue issue = getIssue(projectKey, taskKey);
		if (TaskStatus.fromLabel(issue.getStatus().getName()) == requiredState) {
			stateTransition(issue, newState);
			return true;
		}
		return false;
	}

	public void stateTransition(String projectKey, String taskKey, TaskStatus newState)
			throws JiraException, BusinessServiceException {
		stateTransition(getIssue(projectKey, taskKey), newState);
	}

	public void stateTransition(List<Issue> issues, TaskStatus newState) {
		for (Issue issue : issues) {
			try {
				stateTransition(issue, newState);
			} catch (JiraException | BusinessServiceException e) {
				logger.error("Failed to transition issue {} to {}", issue.getKey(), newState.getLabel());
			}
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

	public List<TaskAttachment> getTaskAttachments(String projectKey, String taskKey) throws BusinessServiceException {

		List<TaskAttachment> attachments = new ArrayList<>();
		final RestClient restClient = getJiraClient().getRestClient();

		try {
			Issue issue = getIssue(projectKey, taskKey);
		
			for (IssueLink issueLink : issue.getIssueLinks()) {

				Issue linkedIssue = issueLink.getOutwardIssue();

				// need to forcibly retrieve the issue in order to get
				// attachments
				Issue issue1 = this.getIssue(null, linkedIssue.getKey(), true);

				String crsId = issue1.getField(jiraCrsIdField).toString();
				if (crsId == null) {
					crsId = "Unknown";
				}

				boolean attachmentFound = false;

				for (Attachment attachment : issue1.getAttachments()) {

					if (attachment.getFileName().equals("request.json")) {

						// attachments must be retrieved by relative path --
						// absolute path will redirect to login
						try {
							final String contentUrl = attachment.getContentUrl();
							final JSON attachmentJson = restClient
									.get(contentUrl.substring(contentUrl.indexOf("secure")));

							TaskAttachment taskAttachment = new TaskAttachment(issue1.getKey(), crsId, attachmentJson.toString());

							attachments.add(taskAttachment);
							
							attachmentFound = true;

						} catch (Exception e) {
							throw new BusinessServiceException(
									"Failed to retrieve attachment " + attachment.getContentUrl() + ": " + e.getMessage(), e);
						}
					}
				}
				
				// if no attachments, create a blank one to link CRS ticket id
 				if (!attachmentFound) {
 					TaskAttachment taskAttachment = new TaskAttachment(issue.getKey(), crsId, null);
 					attachments.add(taskAttachment);
 				}	
				
			}
		} catch (JiraException e) {
			if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 404) {
				throw new ResourceNotFoundException("Task not found " + toString(projectKey, taskKey), e);
			}
			throw new BusinessServiceException("Failed to retrieve task " + toString(projectKey, taskKey), e);
		}

		return attachments;
	}

	public void leaveCommentForTask(String projectKey, String taskKey, String comment) throws BusinessServiceException {
		try {
			addComment(projectKey, taskKey, comment);
		} catch (JiraException e) {
			if (e.getCause() instanceof RestException && ((RestException) e.getCause()).getHttpStatusCode() == 404) {
				throw new ResourceNotFoundException("Task not found " + toString(projectKey, taskKey), e);
			}
			throw new BusinessServiceException("Failed to leave comment for task " + toString(projectKey, taskKey), e);
		
		}
	}

}
