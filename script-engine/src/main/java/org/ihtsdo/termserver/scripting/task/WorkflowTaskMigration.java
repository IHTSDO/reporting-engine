package org.ihtsdo.termserver.scripting.task;

import net.rcarz.jiraclient.Issue;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.JiraHelper;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;

import java.sql.*;
import java.util.*;

public class WorkflowTaskMigration extends JiraTaskMigrationBase implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(WorkflowTaskMigration.class);

	private static final String MIGRATE_PROJECT = "NO1";
	private static final String PROJECT_OWNER = "pwilliams";
	private static final int BATCH_SIZE = 50;

	private static final String PROJECT_METADATA = "{\"projectMrcm\": true, \"projectLocked\": false, \"projectRebase\": true, \"taskPromotion\": true, \"projectPromotion\": true, \"projectTemplates\": true, \"projectSpellCheck\": true, \"projectScheduledRebase\": true}";

	private static final String[] PERMITTED_GROUP_NAMES = new String[] {
		"ap-int-author",
		"int-sca-author",
		"snowstorm-support"
	};
	private boolean projectConfirmed = false;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		TermServerScript.run(WorkflowTaskMigration.class, args, parameters);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[]{"Summary Counts", "Data transmitted"};
		String[] columnHeadings = new String[]{
				"Item, Count",
				"Task, Project, Assignee, Reporter, Status, Created, Updated"
		};
		postInit(GFOLDER_TECHNICAL_SPECIALIST, tabNames, columnHeadings, false);
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Task Migration Report")
				.withDescription("Migrates Jira tasks into SQL, creating projects as needed.")
				.withProductionStatus(Job.ProductionStatus.HIDEME)
				.withParameters(new JobParameters())
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		JiraHelper jiraHelper = new JiraHelper();

		int startAt = 0;
		conn = getConnection();

		try {
			LOGGER.info("Fetching tasks from {} project in batches of {}", MIGRATE_PROJECT, BATCH_SIZE);
			while (true) {
				List<Issue> tasks = jiraHelper.getIssues(MIGRATE_PROJECT, null, true, BATCH_SIZE, startAt);
				if (tasks == null || tasks.isEmpty()) {
					LOGGER.info("No more issues returned by Jira. Stopping.");
					break;
				}
				LOGGER.info("Recovered {} tasks (startAt={})", tasks.size(), startAt);

				for (Issue task : tasks) {
					processTask(task);
				}
				startAt += BATCH_SIZE;
			}
		} finally {
			populateSummaryTab(PRIMARY_REPORT);
		}
	}

	private void processTask(Issue issue) throws TermServerScriptException {
		try {
			LOGGER.info("Processing Jira task {}", issue.getKey());
			incrementSummaryInformation("Tasks Processed");

			if (!projectConfirmed) {
				ensureProjectExists(issue);
			}

			if (!firstTaskProcesssed) {
				//The first task will be the newest one, so we can set the sequence from that
				setTaskSequence(issue);
			}

			if (taskExists(issue.getKey())) {
				LOGGER.info("Task {} already exists in DB. Skipping.", issue.getKey());
				return;
			}

			if(insertTask(issue)) {
				//Task object needs to exist in DB before it can be referenced by reviewers
				insertReviewersIfPresent(issue);
			}

		} catch (Exception e) {
			LOGGER.error("Failed to migrate task {}", issue.getKey(), e);
			incrementSummaryInformation("Task migration failures");
			if (e instanceof SQLException) {
				throw new TermServerScriptException("Database error during task migration", e);
			}
		}
	}

	private void insertReviewersIfPresent(Issue issue) throws SQLException {
		String taskKey = issue.getKey();
		Object reviewersField = issue.getField(JiraHelper.JIRA_FIELD_REVIEWER);

		if (reviewersField instanceof JSONArray) {
			JSONArray reviewersArray = (JSONArray) reviewersField;

			if (reviewersArray.size() > 0) {
				String insertReviewerSql = "INSERT INTO task_reviewer (username, task_key) VALUES (?, ?)";
				String insertLinkSql = "INSERT INTO task_reviewers (task_task_key, reviewers_id) VALUES (?, ?)";

				for (int i = 0; i < reviewersArray.size(); i++) {
					JSONObject reviewerObj = reviewersArray.getJSONObject(i);
					String username = reviewerObj.optString("name", null);

					if (username == null) {
						LOGGER.warn("Reviewer object missing 'name' field in {}", taskKey);
						continue;
					}

					try (
							PreparedStatement psReviewer = conn.prepareStatement(insertReviewerSql, Statement.RETURN_GENERATED_KEYS);
							PreparedStatement psLink = conn.prepareStatement(insertLinkSql)
					) {
						// Insert into task_reviewer
						psReviewer.setString(1, username);
						psReviewer.setString(2, taskKey);
						psReviewer.executeUpdate();

						// Get generated ID
						try (ResultSet rs = psReviewer.getGeneratedKeys()) {
							if (rs.next()) {
								long reviewerId = rs.getLong(1);

								// Insert into task_reviewers
								psLink.setString(1, taskKey);
								psLink.setLong(2, reviewerId);
								psLink.executeUpdate();

								incrementSummaryInformation("Reviewer row + link created");
							}
						}
					} catch (SQLException e) {
						LOGGER.error("Failed to insert reviewer/link for {} -> {}", taskKey, username, e);
					}
				}
			}
		} else if (reviewersField instanceof JSONNull) {
			//That's fine, no reviewer
		}else if (reviewersField != null) {
			LOGGER.warn("Unexpected reviewer field type {} for {}", reviewersField.getClass(), taskKey);
		}
	}


	private void setTaskSequence(Issue issue) throws SQLException {
		String projectKey = issue.getProject().getKey();
		firstTaskProcesssed = true;
		// Check if project already exists
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT sequence FROM task_sequence WHERE project_key = ?")) {
			ps.setString(1, projectKey);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					String taskSequence = rs.getString(1);
					LOGGER.info("Sequence for Project {} already exists in DB as {}. Skipping.", issue.getProject().getKey(), taskSequence);
					return;
				}
			}
		}


		String sql = "INSERT INTO task_sequence (sequence, project_key) " +
				"VALUES (?, ?)";
		int sequence = Integer.parseInt(issue.getKey().replaceAll("\\D", ""));
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setInt(1, sequence);
			ps.setString(2, projectKey);
			ps.executeUpdate();
			LOGGER.info("updated sequence to {}", sequence);
		}
	}

	private void ensureProjectExists(Issue issue) throws SQLException {
		String projectKey = issue.getProject().getKey();
		String projectName = issue.getProject().getName();

		// Step 1: Check if project already exists
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT project_key FROM project WHERE project_key = ?")) {
			ps.setString(1, projectKey);
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return;
				}
			}
		}

		// Step 2: Insert new project
		String sql = "INSERT INTO project (project_key, project_name, created_timestamp, updated_timestamp, active, branch_path, custom_fields, extension_base, project_lead) " +
				"VALUES (?, ?, NOW(), NOW(), 1, ?, ?, ?, ?)";
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, projectKey);
			ps.setString(2, projectName);
			ps.setString(3, "MAIN/" + projectKey);
			ps.setString(4, PROJECT_METADATA);
			ps.setString(5, "MAIN");
			ps.setString(6, PROJECT_OWNER);
			ps.executeUpdate();
			LOGGER.info("Created project {} ({})", projectKey, projectName);
			incrementSummaryInformation("Projects Created");
		}

		// Step 3: Assign default user groups
		assignUserGroupsToProject(projectKey);

		projectConfirmed = true;
	}

	private void assignUserGroupsToProject(String projectKey) throws SQLException {
		String insertGroupSql = "INSERT INTO user_group (group_name, project_key) VALUES (?, ?)";
		String linkSql = "INSERT INTO project_usergroups (project_project_key, userGroups_id) VALUES (?, ?)";

		for (String groupName : PERMITTED_GROUP_NAMES) {
			try (PreparedStatement psGroup = conn.prepareStatement(insertGroupSql, Statement.RETURN_GENERATED_KEYS)) {
				psGroup.setString(1, groupName);
				psGroup.setString(2, projectKey);
				psGroup.executeUpdate();

				try (ResultSet rs = psGroup.getGeneratedKeys()) {
					if (rs.next()) {
						long groupId = rs.getLong(1);

						try (PreparedStatement psLink = conn.prepareStatement(linkSql)) {
							psLink.setString(1, projectKey);
							psLink.setLong(2, groupId);
							psLink.executeUpdate();
							LOGGER.info("Linked user group '{}' (id={}) to project {}", groupName, groupId, projectKey);
							incrementSummaryInformation("User Groups Linked");
						}
					}
				}
			} catch (SQLException e) {
				LOGGER.error("Failed to create/link user group {} for project {}", groupName, projectKey, e);
			}
		}
	}


	private boolean taskExists(String taskKey) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT task_key FROM task WHERE task_key = ?")) {
			ps.setString(1, taskKey);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();
			}
		}
	}

	private boolean insertTask(Issue issue) throws SQLException, TermServerScriptException {
		// Step 1: Check if task already exists
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT task_key FROM task WHERE task_key = ?")) {
			ps.setString(1, issue.getKey());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					incrementSummaryInformation("Task already exists");
					return false;
				}
			}
		}


		String sql = "INSERT INTO task " +
				"(task_key, created_timestamp, updated_timestamp, assignee, branch_path, description, task_name, reporter, status, project_key, type) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
		String projectKey = issue.getProject().getKey();
		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			ps.setString(1, issue.getKey());
			ps.setTimestamp(2, new Timestamp(issue.getCreatedDate().getTime()));
			ps.setTimestamp(3, issue.getUpdatedDate() != null ?
					new Timestamp(issue.getUpdatedDate().getTime()) :
					new Timestamp(issue.getCreatedDate().getTime()));
			ps.setString(4, issue.getAssignee() != null ? issue.getAssignee().getName() : null);
			ps.setString(5, "MAIN/" + projectKey + "/" + issue.getKey()); // branch_path left null unless derived
			ps.setString(6, issue.getDescription());
			ps.setString(7, issue.getSummary());
			ps.setString(8, issue.getReporter() != null ? issue.getReporter().getName() : null);
			ps.setString(9, normaliseStatus(issue));
			ps.setString(10, projectKey);
			ps.setString(11, "AUTHORING"); // or AUTHORING depending on context
			if (!dryRun) {
				ps.executeUpdate();
				incrementSummaryInformation("Task migrated");
				LOGGER.info("Inserted task {}", issue.getKey());
			}

			report(SECONDARY_REPORT,
					issue.getKey(),
					projectKey,
					issue.getAssignee() != null ? issue.getAssignee().getName() : "",
					issue.getReporter() != null ? issue.getReporter().getName() : "",
					issue.getStatus().getName(),
					issue.getCreatedDate().toString(),
					issue.getUpdatedDate() != null ? issue.getUpdatedDate().toString() : "");
		}
		return true;
	}

	@Override
	protected Long getCommentForeignKeyId(Issue issue) {
		throw new IllegalStateException("Workflow migration does not include comments");
	}

	@Override
	protected String getCommentTableName() {
		throw new IllegalStateException("Workflow migration does not include comments");
	}

	@Override
	protected String getCommentForeignKeyColumnName() {
		throw new IllegalStateException("Workflow migration does not include comments");
	}

	@Override
	protected String getCommentCreatedColumnName() {
		throw new IllegalStateException("Workflow migration does not include comments");
	}

	@Override
	protected String getCommentUpdatedColumnName() {
		throw new IllegalStateException("Workflow migration does not include comments");
	}
}
