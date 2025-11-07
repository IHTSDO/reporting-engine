package org.ihtsdo.termserver.scripting.task;

import net.rcarz.jiraclient.Issue;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.JiraHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;

import java.sql.*;
import java.util.*;

public class RMPTaskMigration extends JiraTaskMigrationBase implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(RMPTaskMigration.class);

	private static final int BATCH_SIZE = 50;

	private static final String JIRA_FIELD_NEW_FSN = "customfield_14208";
	private static final String JIRA_FIELD_NEW_PREFERRED_TERM = "customfield_14209";
	private static final String JIRA_FIELD_NEW_DESCRIPTION = "customfield_14213";
	private static final String JIRA_FIELD_SYNONYMS = "customfield_14210";
	private static final String JIRA_FIELD_PARENT_CONCEPT = "customfield_14206";
	private static final String JIRA_FIELD_CONCEPT_ID = "customfield_10602";
	private static final String JIRA_FIELD_CONCEPT_NAME = "customfield_10601";
	private static final String JIRA_FIELD_CONTEXT_REFSET = "customfield_15001";
	private static final String JIRA_FIELD_COUNTRY = "customfield_12713";
	private static final String JIRA_FIELD_CHANGE_TYPE = "customfield_12810";
	private static final String JIRA_FIELD_LANGUAGE_REFSET = "customfield_15000";
	private static final String JIRA_FIELD_JUSTIFICATION = "customfield_14205";
	private static final String JIRA_FIELD_REFERENCE = "customfield_14204";
	private static final String JIRA_FIELD_EXISTING_DESCRIPTION = "customfield_14211";
	private static final String JIRA_FIELD_EXISTING_RELATIONSHIP = "customfield_14212";
	private static final String JIRA_FIELD_RELATIONSHIP_TYPE = "customfield_14216";
	private static final String JIRA_FIELD_RELATIONSHIP_TARGET_ID = "customfield_14215";
	private static final String JIRA_FIELD_ECL_QUERY = "customfield_15901";
	private static final String JIRA_FIELD_MEMBER_CONCEPT_IDS = "customfield_15902";

	public static final List<String> ALL_FIELDS = List.of(
			JIRA_FIELD_NEW_FSN,
			JIRA_FIELD_NEW_PREFERRED_TERM,
			JIRA_FIELD_NEW_DESCRIPTION,
			JIRA_FIELD_SYNONYMS,
			JIRA_FIELD_PARENT_CONCEPT,
			JIRA_FIELD_CONCEPT_ID,
			JIRA_FIELD_CONCEPT_NAME,
			JIRA_FIELD_CONTEXT_REFSET,
			JIRA_FIELD_COUNTRY,
			JIRA_FIELD_CHANGE_TYPE,
			JIRA_FIELD_LANGUAGE_REFSET,
			JIRA_FIELD_JUSTIFICATION,
			JIRA_FIELD_REFERENCE,
			JIRA_FIELD_EXISTING_DESCRIPTION,
			JIRA_FIELD_EXISTING_RELATIONSHIP,
			JIRA_FIELD_RELATIONSHIP_TYPE,
			JIRA_FIELD_RELATIONSHIP_TARGET_ID,
			JIRA_FIELD_ECL_QUERY,
			JIRA_FIELD_MEMBER_CONCEPT_IDS
	);

	public static final String ALL_FIELDS_STR = String.join(",", ALL_FIELDS);


	private final List<RDSProject> projects = Arrays.asList(
			new RDSProject("Belgium", "BERMP", "be"),
			new RDSProject("Denmark", "DKRMP", "dk"),
			new RDSProject("Estonia", "EERMP", "ee"),
			new RDSProject("France", "FRRMP", "fr"),
			new RDSProject("Ireland", "IERMP", "ie"),
			new RDSProject("Korea", "KRRMP", "kr"),
			new RDSProject("New Zealand", "NZRMP", "nz"),
			/*new RDSProject("Norway", "NORMP", "no")*/
			new RDSProject("Switzerland", "CHRMP", "ch")
	);

	private static final Map<String, Integer> startAtMap = new HashMap<>();
	static {
		startAtMap.put("BERMP", 0);
	}

	private Long currentRMPTaskId = null;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> parameters = new HashMap<>();
		TermServerScript.run(RMPTaskMigration.class, args, parameters);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		// First tab: stays the same
		List<String> tabNames = new ArrayList<>();
		tabNames.add("Summary Counts");

		List<String> columnHeadings = new ArrayList<>();
		columnHeadings.add("Item, Count");

		// Second tab: repeated for each project
		for (RDSProject project : projects) {
			tabNames.add(project.jiraProjectKey);
			columnHeadings.add("Jira Task, DB Id, Assignee, Reporter, Status, Created, Updated, Summary");

			// Comments tab
			tabNames.add(project.jiraProjectKey + " CMTS");
			columnHeadings.add("Issue Key, DB Id, Comment Body, Created, Comment Author, Details");

		}

		postInit(GFOLDER_TECHNICAL_SPECIALIST,
				tabNames.toArray(new String[0]),
				columnHeadings.toArray(new String[0]),
				false);
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
		jiraHelper = new JiraHelper();
		conn = getConnection();
		int tabIdx = SECONDARY_REPORT;
		try {
			for (RDSProject project : projects) {
				runMigrationForProject(project, tabIdx);
				tabIdx += 2; //Skip a tab for comments
			}
		} finally {
			try {
				conn.close();
			} catch (SQLException e) {
				LOGGER.warn("Error closing connection to database.", e);
			}
			populateSummaryTab(PRIMARY_REPORT);
		}

	}

	@Override
	protected Long getCommentForeignKeyId(Issue issue) {
		return currentRMPTaskId;
	}

	@Override
	protected String getCommentTableName() {
		return "comment";
	}

	@Override
	protected String getCommentForeignKeyColumnName() {
		return "rmp_task";
	}

	@Override
	protected String getCommentCreatedColumnName() {
		return "created_timestamp";
	}

	@Override
	protected String getCommentUpdatedColumnName() {
		return "updated_timestamp";
	}

	private void runMigrationForProject(RDSProject project, int tabIdx) throws TermServerScriptException {
		int startAt = 0;
		if (startAtMap.containsKey(project.jiraProjectKey)) {
			startAt = startAtMap.get(project.jiraProjectKey);
		}
		LOGGER.info("Fetching tasks from {} project in batches of {} starting at {}", project.jiraProjectKey, BATCH_SIZE, startAt);
		while (true) {
			List<Issue> tasks = jiraHelper.getIssues(project.jiraProjectKey, ALL_FIELDS_STR, false, BATCH_SIZE, startAt);
			if (tasks == null || tasks.isEmpty()) {
				LOGGER.info("No more issues returned by Jira. Stopping.");
				break;
			}
			LOGGER.info("Recovered {} tasks (startAt={})", tasks.size(), startAt);

			for (Issue task : tasks) {
				processTask(project, task, tabIdx);
			}
			startAt += BATCH_SIZE;
		}
	}

	private void processTask(RDSProject project, Issue issue, int tabIdx) throws TermServerScriptException {
		try {
			LOGGER.info("Processing Jira task {}", issue.getKey());
			incrementSummaryInformation(issue.getProject().getKey() + " Tasks Processed");

			currentRMPTaskId = insertRmpTask( project,  issue,  tabIdx);
			if(currentRMPTaskId != null) {
				migrateCommentsFromTask(issue,  tabIdx + 1);
			}

		} catch (Exception e) {
			LOGGER.error("Failed to migrate task {}", issue.getKey(), e);
			incrementSummaryInformation(project.countryName + " Task migration failures");
			if (e instanceof SQLException) {
				throw new TermServerScriptException("Database error during task migration", e);
			}
		}
	}

	private Long insertRmpTask(RDSProject project, Issue issue, int tabIdx) throws SQLException, TermServerScriptException {
		try (PreparedStatement ps = prepareRmpTaskStatement(project, issue)) {
			return executeRmpTaskInsert(project, issue, tabIdx, ps);
		}
	}

	private PreparedStatement prepareRmpTaskStatement(RDSProject project, Issue issue) throws SQLException {
		String sql = "INSERT INTO rmp_task " +
				"(created_timestamp, updated_timestamp, assignee, reporter, status, summary, type, " +
				"concept, conceptId, conceptName, contextRefset, country, languageRefset, justification, " +
				"memberConceptIds, newFSN, newPT, newDescription, newSynonyms, parentConcept, reference, " +
				"existingDescription, existingRelationship, relationshipType, relationshipTarget, eclQuery) " +
				"VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

		PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);

		ps.setTimestamp(1, new Timestamp(issue.getCreatedDate().getTime()));
		ps.setTimestamp(2, issue.getUpdatedDate() != null ?
				new Timestamp(issue.getUpdatedDate().getTime()) :
				new Timestamp(issue.getCreatedDate().getTime()));
		ps.setString(3, issue.getAssignee() != null ? issue.getAssignee().getName() : null);
		ps.setString(4, issue.getReporter() != null ? issue.getReporter().getName() : null);
		ps.setString(5, normaliseStatus(issue));
		ps.setString(6, issue.getSummary());
		ps.setString(7, normaliseType(issue));
		ps.setString(8, null);
		ps.setString(9, getCustomFieldValue(issue, JIRA_FIELD_CONCEPT_ID));
		ps.setString(10, getCustomFieldValue(issue, JIRA_FIELD_CONCEPT_NAME));
		ps.setString(11, getCustomFieldValue(issue, JIRA_FIELD_CONTEXT_REFSET));
		ps.setString(12, project.countryCode);
		ps.setString(13, getCustomFieldValue(issue, JIRA_FIELD_LANGUAGE_REFSET));
		ps.setString(14, getCustomFieldValue(issue, JIRA_FIELD_JUSTIFICATION));
		ps.setString(15, getCustomFieldValue(issue, JIRA_FIELD_MEMBER_CONCEPT_IDS));
		ps.setString(16, getCustomFieldValue(issue, JIRA_FIELD_NEW_FSN));
		ps.setString(17, getCustomFieldValue(issue, JIRA_FIELD_NEW_PREFERRED_TERM));
		ps.setString(18, getCustomFieldValue(issue, JIRA_FIELD_NEW_DESCRIPTION));
		ps.setString(19, getCustomFieldValue(issue, JIRA_FIELD_SYNONYMS));
		ps.setString(20, getCustomFieldValue(issue, JIRA_FIELD_PARENT_CONCEPT));
		ps.setString(21, getCustomFieldValue(issue, JIRA_FIELD_REFERENCE));
		ps.setString(22, getCustomFieldValue(issue, JIRA_FIELD_EXISTING_DESCRIPTION));
		ps.setString(23, getCustomFieldValue(issue, JIRA_FIELD_EXISTING_RELATIONSHIP));
		ps.setString(24, getCustomFieldValue(issue, JIRA_FIELD_RELATIONSHIP_TYPE));
		ps.setString(25, getCustomFieldValue(issue, JIRA_FIELD_RELATIONSHIP_TARGET_ID));
		ps.setString(26, getCustomFieldValue(issue, JIRA_FIELD_ECL_QUERY));

		return ps;
	}

	private Long executeRmpTaskInsert(RDSProject project, Issue issue, int tabIdx, PreparedStatement ps) throws SQLException, TermServerScriptException {
		if (!dryRun) {
			try {
				ps.executeUpdate();

				try (ResultSet generatedKeys = ps.getGeneratedKeys()) {
					if (generatedKeys.next()) {
						long rmpTaskId = generatedKeys.getLong(1);
						LOGGER.info("Inserted {} RMP task {} with ID {}", project.countryName, issue.getKey(), rmpTaskId);

						report(tabIdx,
								issue.getKey(),
								rmpTaskId,
								issue.getAssignee() != null ? issue.getAssignee().getName() : "",
								issue.getReporter() != null ? issue.getReporter().getName() : "",
								issue.getStatus().getName(),
								issue.getCreatedDate().toString(),
								issue.getUpdatedDate() != null ? issue.getUpdatedDate().toString() : "",
								issue.getSummary());

						return rmpTaskId; // return for FKey use
					} else {
						throw new SQLException("Inserting RMP task failed, no ID obtained.");
					}
				}
			} finally {
				ps.close();
			}
		} else {
			return null; // dryRun: no ID generated
		}
	}


	static class RDSProject {
		String countryName;
		String jiraProjectKey;
		String countryCode;

		RDSProject(String countryName, String jiraProjectKey, String countryCode) {
			this.countryName = countryName;
			this.jiraProjectKey = jiraProjectKey;
			this.countryCode = countryCode;
		}
	}
}
