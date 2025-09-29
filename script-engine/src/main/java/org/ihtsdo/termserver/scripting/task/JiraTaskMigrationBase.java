package org.ihtsdo.termserver.scripting.task;

import net.rcarz.jiraclient.Comment;
import net.rcarz.jiraclient.Issue;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.JiraHelper;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;

import java.sql.*;
import java.util.List;

public abstract class JiraTaskMigrationBase extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(JiraTaskMigrationBase.class);

	protected Connection conn;
	protected JiraHelper jiraHelper;
	protected boolean firstTaskProcesssed = false;
	protected String lastTaskProcesssed;

	@Override
	protected void init(JobRun jobRun) throws TermServerScriptException {
		scriptRequiresSnomedData = false;
		summaryTabIdx = PRIMARY_REPORT;
		super.init(jobRun);
	}


	/**
	 * Helper method to get a custom field value as string from an Issue.
	 */
	protected String getCustomFieldValue(Issue issue, String customFieldId) {
		Object fieldObj = issue.getField(customFieldId); // depends on your Jira client API
		if (fieldObj == null || fieldObj instanceof JSONNull) {
			return null; // or handle null case as needed
		}

		if (fieldObj instanceof JSONObject json) {
			return json.optString("value", null);
		}
		return fieldObj.toString();
	}

	protected abstract Long getCommentForeignKeyId(Issue issue);

	protected abstract String getCommentTableName();

	protected abstract String getCommentForeignKeyColumnName();

	protected abstract String getCommentCreatedColumnName();

	protected abstract String getCommentUpdatedColumnName();

	protected String normaliseStatus(Issue issue) {
		return issue.getStatus().getName().toUpperCase().replace(" ", "_");
	}

	protected String normaliseType(Issue issue) {
		return issue.getIssueType().getName().toLowerCase().replace(" ", "-");
	}

	protected void migrateCommentsFromTask(Issue task, int tabIdx) throws TermServerScriptException {
		try {
			LOGGER.info("Processing comments in task {}", task.getKey());
			incrementSummaryInformation("Tasks mined for comments");
			List<Comment> comments = task.getComments();  // get all comments for this issue
			if (comments != null && !comments.isEmpty()) {
				Long foreignKeyId = getCommentForeignKeyId(task);
				if (foreignKeyId == null) {
					LOGGER.warn("No target database row found for Jira task {}", task.getKey());
					report(tabIdx,
							task.getKey(),
							"",
							"No target database row found to match " + task.getKey(),
							Severity.HIGH,
							ReportActionType.VALIDATION_CHECK);
					incrementSummaryInformation("No target request found for Jira task");
					return;
				}

				if (!firstTaskProcesssed) {
					addFinalWords("First task processed: " + task.getKey());
					firstTaskProcesssed = true;
				}
				lastTaskProcesssed = task.getKey();
				saveComments(task, foreignKeyId, comments, conn, tabIdx);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to migrate comments for task {}", task.getKey(), e);
			incrementSummaryInformation("Task migration failures");
			report(tabIdx,
					task.getKey(),
					"",
					"Error while processing " + task.getKey(),
					Severity.HIGH,
					ReportActionType.VALIDATION_CHECK,
					e);
		}
	}

	protected Connection getConnection() throws TermServerScriptException {
		try {
			String[] configItems = this.getJobRun().getAdditionalConfig().split("\\|");
			String user = configItems[0];
			String password = configItems[1];
			String url = "jdbc:mysql://" + configItems[3] + ":3306/" + configItems[2];
			return DriverManager.getConnection(url, user, password);
		} catch (SQLException e) {
			throw new TermServerScriptException(e);
		}
	}

	protected void saveComments(Issue task, Long foreignKeyId, List<Comment> comments, Connection conn, int tabIdx) throws SQLException {
		String insertSql = "INSERT INTO " + getCommentTableName() +
				" (body, user, " + getCommentCreatedColumnName() + ", "+ getCommentUpdatedColumnName()+", " + getCommentForeignKeyColumnName() +
				") VALUES (?, ?, ?, ?, ?)";

		String existsSql = "SELECT 1 FROM  " + getCommentTableName() +
				" WHERE body = ? AND user = ? AND "+getCommentCreatedColumnName()+" = ? AND " + getCommentForeignKeyColumnName() + " = ?";
		int commentsSaved = 0;
		try (PreparedStatement insertPs = conn.prepareStatement(insertSql);
		     PreparedStatement existsPs = conn.prepareStatement(existsSql)) {

			for (Comment comment : comments) {
				String body = comment.getBody();
				String user = comment.getAuthor().getName();
				Timestamp created = new Timestamp(comment.getCreatedDate().getTime());

				// check for existence
				existsPs.setString(1, body);
				existsPs.setString(2, user);
				existsPs.setTimestamp(3, created);
				existsPs.setLong(4, foreignKeyId);

				try (ResultSet rs = existsPs.executeQuery()) {
					if (rs.next()) {
						incrementSummaryInformation("Comment already exists");
						continue; // skip this comment
					}
				}

				// not found → insert
				insertPs.setString(1, body);
				insertPs.setString(2, user);
				insertPs.setTimestamp(3, created);
				insertPs.setTimestamp(4, new Timestamp(comment.getUpdatedDate() != null
						? comment.getUpdatedDate().getTime()
						: comment.getCreatedDate().getTime()));
				insertPs.setLong(5, foreignKeyId);
				insertPs.addBatch();
				commentsSaved++;
				try {
					report(tabIdx,
							task.getKey(),
							foreignKeyId,
							body,
							comment.getCreatedDate().toString(),
							comment.getAuthor().getDisplayName());
					incrementSummaryInformation(task.getProject().getKey() + " Comment migrated");
				} catch (TermServerScriptException e) {
					LOGGER.warn("Failed to report comments for task {}", task.getKey(), e);
				}
			}

			if (!dryRun) {
				insertPs.executeBatch();
				LOGGER.info("Successfully saved {}/{} new comments for task {} / DB id {}",
						commentsSaved, comments.size(), task.getKey(), foreignKeyId);
			}
		}
	}
}
