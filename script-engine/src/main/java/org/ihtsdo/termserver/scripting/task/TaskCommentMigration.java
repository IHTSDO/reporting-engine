package org.ihtsdo.termserver.scripting.task;

import net.rcarz.jiraclient.Comment;
import net.rcarz.jiraclient.Issue;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.JiraHelper;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.sql.*;
import java.util.*;
import java.util.Date;

public class TaskCommentMigration extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskCommentMigration.class);

	private static final String MIGRATE_PROJECT = "CRT";
	private static final int BATCH_SIZE = 50;

	private static final boolean USE_DATE_CUTOFF = false;

	private Connection conn;
	private boolean firstTaskProcesssed = false;
	private String lastTaskProcesssed;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put(MODULES, SCTID_LOINC_EXTENSION_MODULE);
		TermServerScript.run(TaskCommentMigration.class, args, parameters);
	}

	@Override
	protected void init (JobRun jobRun) throws TermServerScriptException {
		scriptRequiresSnomedData = false;
		summaryTabIdx = PRIMARY_REPORT;
		super.init(jobRun);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[] {
				"Summary Counts",
				"Data transmitted"
		};
		String[] columnHeadings = new String[] {
				"Item, Count",
				"Task, CRS ID, Comment, Date, Author, spare, "
		};
		postInit(GFOLDER_CRS, tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Task Comment Migration Report")
				.withDescription("This report list summary counts for a particular product extension, with cross checks.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(new JobParameters())
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		JiraHelper jiraHelper = new JiraHelper();
		LOGGER.info("Fetching tasks from {} project in batches of 50", MIGRATE_PROJECT);

		int startAt = 0;
		boolean dateLimitReached = false;

		// calculate cutoff date = today minus 2 years
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.YEAR, -2);
		Date cutoff = cal.getTime();


		conn = getConnection();
		try {
			while (!dateLimitReached) {
				List<Issue> tasks = jiraHelper.getIssues(MIGRATE_PROJECT, BATCH_SIZE, startAt);

				if (tasks == null || tasks.isEmpty()) {
					LOGGER.info("No more issues returned by Jira. Stopping.");
					break;
				}

				LOGGER.info("Recovered {} tasks (startAt={})", tasks.size(), startAt);

				for (Issue task : tasks) {
					processTask(task);
				}

				// check the created date of the last issue in this batch
				Issue last = tasks.get(tasks.size() - 1);
				Date lastCreated = last.getCreatedDate();  // Jira API provides creation date
				if (USE_DATE_CUTOFF && lastCreated.before(cutoff)) {
					LOGGER.info("Reached issue created {} which is older than cutoff {}. Stopping.",
							lastCreated, cutoff);
					dateLimitReached = true;
				} else {
					startAt += BATCH_SIZE; // move to next page
				}
			}
		} finally {
			populateSummaryTab(PRIMARY_REPORT);
			addFinalWords("Last task processed: " + lastTaskProcesssed);
		}
	}

	private void processTask(Issue task) throws TermServerScriptException {
		try {
			LOGGER.info("Processing task {}", task.getKey());
			incrementSummaryInformation("Tasks Processed");
			List<Comment> comments = task.getComments();  // get all comments for this issue
			if (comments != null && !comments.isEmpty()) {
				Long requestId = null;
				try {
					requestId = Long.parseLong((String)task.getField("customfield_10401"));
				} catch (Exception e) {
					// We'll deal with this via the
				}
				if (requestId == null) {
					LOGGER.warn("No CRS request found for Jira task {}", task.getKey());
					report(SECONDARY_REPORT,
							task.getKey(),
							"",
							"No CRS request found to match " + task.getKey(),
							Severity.HIGH,
							ReportActionType.VALIDATION_CHECK);
					incrementSummaryInformation("No CRS request found for Jira task");
					return;
				}

				if (requestAlreadyHasComments(requestId, conn)) {
					LOGGER.warn("CRS request {} (Jira {}) already has comments. Skipping.", requestId, task.getKey());
					return;
				}
				if (!firstTaskProcesssed) {
					addFinalWords("First task processed: " +task.getKey());
					firstTaskProcesssed = true;
				}
				lastTaskProcesssed = task.getKey();
				saveComments(task, requestId, comments, conn);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to migrate comments for task {}", task.getKey(), e);
			incrementSummaryInformation("Task migration failures");
			if (e instanceof SQLException) {
				throw new TermServerScriptException("Database error during comment migration", e);
			}
		}
	}

	private Connection getConnection() throws TermServerScriptException {
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

	/*private Long findRequestIdForTask(Issue task, Connection conn) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT id FROM request WHERE jiraTicketId = ?")) {
			ps.setString(1, task.getKey());
			try (ResultSet rs = ps.executeQuery()) {
				if (rs.next()) {
					return rs.getLong("id");
				}
			}
		}
		return null;
	}*/

	private boolean requestAlreadyHasComments(Long requestId, Connection conn) throws SQLException {
		try (PreparedStatement ps = conn.prepareStatement(
				"SELECT 1 FROM request_comment WHERE request = ? LIMIT 1")) {
			ps.setLong(1, requestId);
			try (ResultSet rs = ps.executeQuery()) {
				return rs.next();  // true if any comment exists
			}
		}
	}

	private void saveComments(Issue task, Long requestId, List<Comment> comments, Connection conn) throws SQLException {
		String sql = "INSERT INTO request_comment " +
				"(body, user, internal, createDate, updateDate, request) " +
				"VALUES (?, ?, ?, ?, ?, ?)";

		try (PreparedStatement ps = conn.prepareStatement(sql)) {
			for (Comment comment : comments) {
				ps.setString(1, comment.getBody());
				ps.setString(2, comment.getAuthor().getName());
				ps.setBoolean(3, false);  // Jira doesn’t have "internal"
				ps.setTimestamp(4, new Timestamp(comment.getCreatedDate().getTime()));
				ps.setTimestamp(5, new Timestamp(comment.getUpdatedDate() != null
						? comment.getUpdatedDate().getTime()
						: comment.getCreatedDate().getTime()));
				ps.setLong(6, requestId);
				ps.addBatch();
				try {
					report(SECONDARY_REPORT,
							task.getKey(),
							requestId,
							comment.getBody(),
							comment.getCreatedDate().toString(),
							comment.getAuthor().getDisplayName());
					incrementSummaryInformation("Comment migrated");
				} catch (TermServerScriptException e) {
					LOGGER.warn("Failed to report comments for task {}", task.getKey(), e);
				}
			}
			if (!dryRun) {
				ps.executeBatch();
				LOGGER.info("Successfully saved {} comments for task {} / request {}", comments.size(), task.getKey(), requestId);
			}
		}
	}

}
