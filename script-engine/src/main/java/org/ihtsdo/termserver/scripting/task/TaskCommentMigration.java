package org.ihtsdo.termserver.scripting.task;

import net.rcarz.jiraclient.Comment;
import net.rcarz.jiraclient.Issue;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.JiraHelper;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import java.sql.*;
import java.util.*;
import java.util.Date;

public class TaskCommentMigration extends JiraTaskMigrationBase implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskCommentMigration.class);

	private static final String MIGRATE_PROJECT = "CRT";
	private static final int START_AT = 50565;
	private static final int BATCH_SIZE = 50;

	private static final boolean USE_DATE_CUTOFF = false;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		parameters.put(MODULES, SCTID_LOINC_EXTENSION_MODULE);
		TermServerScript.run(TaskCommentMigration.class, args, parameters);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[]{
				"Summary Counts",
				"Data transmitted"
		};
		String[] columnHeadings = new String[]{
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
	protected String getCommentTableName() {
		return "request_comment";
	}

	@Override
	protected String getCommentForeignKeyColumnName() {
		return "request";
	}

	@Override
	protected String getCommentCreatedColumnName() {
		return "createDate";
	}

	@Override
	protected String getCommentUpdatedColumnName() {
		return "updateDate";
	}

	@Override
	public void runJob() throws TermServerScriptException {
		JiraHelper jiraHelper = new JiraHelper();
		LOGGER.info("Fetching tasks from {} project in batches of 50", MIGRATE_PROJECT);

		boolean dateLimitReached = false;

		// calculate cutoff date = today minus 2 years
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.YEAR, -2);
		Date cutoff = cal.getTime();


		conn = getConnection();
		int currentPosition = START_AT;
		try {
			while (!dateLimitReached) {
				List<Issue> tasks = jiraHelper.getIssues(MIGRATE_PROJECT, null, true, BATCH_SIZE, currentPosition);

				if (tasks == null || tasks.isEmpty()) {
					LOGGER.info("No more issues returned by Jira. Stopping.");
					break;
				}

				LOGGER.info("Recovered {} tasks (position={})", tasks.size(), currentPosition);

				for (Issue task : tasks) {
					migrateCommentsFromTask(task, SECONDARY_REPORT);
				}

				// check the created date of the last issue in this batch
				Issue last = tasks.get(tasks.size() - 1);
				Date lastCreated = last.getCreatedDate();  // Jira API provides creation date
				if (USE_DATE_CUTOFF && lastCreated.before(cutoff)) {
					LOGGER.info("Reached issue created {} which is older than cutoff {}. Stopping.",
							lastCreated, cutoff);
					dateLimitReached = true;
				} else {
					currentPosition += BATCH_SIZE; // move to next page
				}
			}
		} finally {
			populateSummaryTab(PRIMARY_REPORT);
			addFinalWords("Last task processed: " + lastTaskProcesssed);
		}
	}

	@Override
	protected Long getCommentForeignKeyId(Issue task) {
		Long requestId = null;
		try {
			requestId = Long.parseLong((String) task.getField("customfield_10401"));
		} catch (Exception e) {
			// We'll deal with this via the
		}
		return requestId;
	}

}
