package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PackageComparisonReport extends SummaryComponentStats implements ReportClass {
	private static final Logger LOGGER = LoggerFactory.getLogger(PackageComparisonReport.class);
	private static final String SCRIPT_NAME = "run-compare-packages.sh";
	private static final char LINE_DELETED_INDICATOR = '<';
	private static final char LINE_CREATED_INDICATOR = '>';
	private static final int TIMEOUT_MINUTES = 30;
	private static final int FILE_COMPARISON_TAB = MAX_REPORT_TABS;
	private static final int NUMBER_OF_COLUMNS = 7;

	private String previousReleasePath;
	private String currentReleasePath;
	private Map<String, int[]> fileTotals = new TreeMap<>();

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();

		// Estonia release
		//params.put(PREV_RELEASE, "ee/estonia_extension_releases/2022-11-15T15:50:50/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20221130T120000Z.zip");
		//params.put(THIS_RELEASE, "ee/estonia_extension_releases/2023-05-23T08:35:57/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20230530T120000Z.zip");
		//params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220831T120000Z.zip");
		//params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230228T120000Z.zip");
		//params.put(MODULES, "11000181102");

		// International on dev
		//params.put(THIS_RELEASE, "international/international_edition_releases/2023-06-06T05:13:11/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip");
		//params.put(PREV_RELEASE, "international/international_edition_releases/2023-05-10T04:54:06/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip");

		// International on prod
		//params.put(PREV_RELEASE, "international/international_edition_releases/2023-05-17T11:48:57/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230531T120000Z.zip");
		//params.put(THIS_RELEASE, "international/international_edition_releases/2023-06-14T08:22:16/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230630T120000Z.zip");

		// US Edition Test 1
		//params.put(PREV_RELEASE, "us/us_edition_releases/2023-08-02T15:53:24/output-files/xSnomedCT_ManagedServiceUS_PREPRODUCTION_US1000124_20230901T120000Z.zip");
		//params.put(THIS_RELEASE, "us/us_edition_releases/2023-08-17T15:59:10/output-files/xSnomedCT_ManagedServiceUS_PREPRODUCTION_US1000124_20230901T120000Z.zip");

		// US Edition Test 2
		params.put(PREV_RELEASE, "us/us_edition_releases/2023-02-20T18:46:58/output-files/SnomedCT_ManagedServiceUS_PRODUCTION_US1000124_20230301T120000Z.zip");
		params.put(THIS_RELEASE, "us/us_edition_releases/2023-08-02T15:53:24/output-files/xSnomedCT_ManagedServiceUS_PREPRODUCTION_US1000124_20230901T120000Z.zip");

		TermServerReport.run(PackageComparisonReport.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; // Release Stats
		previousReleasePath = run.getParamValue(PREV_RELEASE);
		currentReleasePath = run.getParamValue(THIS_RELEASE);
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Sctid, Hierarchy, SemTag, New, Changed DefnStatus, Inactivated, Reactivated, New Inactive, New with New Concept, Moved Module, Changed Inactive, New SD, New P, Total Active, Total, Promoted",
				"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
				"Sctid, Hierarchy, SemTag, New Inferred Rels, Changed Inferred Rels, Inactivated Inferred Rels, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
				"Sctid, Hierarchy, SemTag, New Inferred Rels, Changed Inferred Rels, Inactivated Inferred Rels, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
				"Sctid, Hierarchy, SemTag, New Axioms, Changed Axioms, Inactivated Axioms, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
				"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Concepts Affected, Total Active",
				"Sctid, Hierarchy, SemTag, Inactivations New / Reactivated, New Inactive, Changed, Inactivations Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Ambiguous, Moved Elsewhere, Concept Non Current, Duplicate, Erroneous, Inappropriate, Limited, Outdated, Pending Move, Non Conformance, Not Equivalent, Concepts Affected, Total Active",
				"Sctid, Hierarchy, SemTag, Assoc New, Changed, Assoc Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Concepts Affected, Total Active",
				"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total, Concepts Affected, Total Active",
				"Sctid, Hierarchy, SemTag, In Scope New, Attributes Added, Model Removed, Model Inactivated, Total In Scope",
				"Sctid, Hierarchy, SemTag, New, Inactivated, Reactivated, New Inactive, Total, Total Active",
				"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
				"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
				"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
				" , ,Language, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
				"Filename, New, Changed, Inactivated, Reactivated, Deleted, Total"
		};

		String[] tabNames = new String[] {
				"Concepts",
				"Descriptions",
				"Relationships",
				"Concrete Rels",
				"Axioms",
				"LangRefSet",
				"Inactivations",
				"Hist Assoc",
				"Text Defn",
				"QI Scope",
				"Desc Assoc",
				"Desc CNC",
				"Desc Inact",
				"Refsets",
				"Desc by Lang",
				"File Comparison"
		};

		topLevelHierarchies = new ArrayList<>(ROOT_CONCEPT.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
		topLevelHierarchies.add(UNKNOWN_CONCEPT);
		topLevelHierarchies.add(ROOT_CONCEPT);
		topLevelHierarchies.sort(Comparator.comparing(Concept::getFsn));

		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.STRING)
				.add(THIS_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(PREV_RELEASE).withType(JobParameter.Type.STRING)
				.add(PREV_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportOutputType.GOOGLE.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportFormatType.CSV.name())
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Package Comparison Report")
				.withDescription("This report compares two packages (zip archives) using Unix scripts with output captured into usual Google Sheets")
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withExpectedDuration(40)
				.build();
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
		if (!StringUtils.isEmpty(prevDependency)) {
			LOGGER.info("Setting previous dependency archive to " + prevDependency);
			setDependencyArchive(prevDependency);
 		}
		super.loadProjectSnapshot(fsnOnly);
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		thisDependency = getJobRun().getParamValue(THIS_DEPENDENCY);
		if (!StringUtils.isEmpty(thisDependency)) {
			LOGGER.info("Setting dependency archive to " + thisDependency);
			setDependencyArchive(thisDependency);
		}
		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}

	public void runJob() throws TermServerScriptException {
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			throw new TermServerScriptException("Windows operating system detected. This report can only run on Linux or MacOS.");
		}
		// Run SCS report first
		LOGGER.info("Running SCS report");
		super.runJob();

		// Execute file comparison script
		File previousRelease = new File(previousReleasePath);
		File currentRelease = new File(currentReleasePath);

		String uploadFolder = String.join("_",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmm")),
				previousRelease.getName(),
				currentRelease.getName());

		String[] scriptArguments = new String[]{
				doubleQuote(previousRelease.getName()),
				doubleQuote(previousRelease.getPath()),
				doubleQuote(currentRelease.getName()),
				doubleQuote(currentRelease.getPath()),
				doubleQuote(uploadFolder)
		};

		String command = "scripts/" + SCRIPT_NAME + SPACE + String.join(SPACE, scriptArguments);

		LOGGER.info("Starting script execution. Command: " + command);
		executeScript(command);
		LOGGER.info("Diff files for '" + previousRelease.getPath() + "' and '" + currentRelease.getPath() + "' are uploaded to s3://snomed-compares/" + uploadFolder);

		processFiles(uploadFolder);
		outputResults();
	}

	private void executeScript(String command) throws TermServerScriptException {
		try {
			// Execute scripts
			ProcessBuilder builder = new ProcessBuilder();
			builder.redirectErrorStream(true);
			builder.command("sh", "-c", command);

			Process process = builder.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				reader.lines().forEach(line -> LOGGER.debug(line));
			}

			if (process.waitFor(TIMEOUT_MINUTES, TimeUnit.MINUTES)) {
				// Process exited
				int exitValue = process.exitValue();

				if (exitValue != 0) {
					throw new TermServerScriptException("Script execution failed with exit code: " + exitValue + ", see log for details");
				}
				LOGGER.info("Script execution finished successfully");
			} else {
				// Process timed out
				throw new TermServerScriptException("Script execution timed out, timeout set to " + TIMEOUT_MINUTES + " minutes");
			}
		} catch (IOException | InterruptedException e) {
			LOGGER.error("Script execution failed");
			throw new TermServerScriptException(e);
		}
	}

	private void processFiles(String uploadFolder) throws TermServerScriptException {
		Path diffDir = Path.of("results", uploadFolder, "target", "c");

		try {
			// Process snapshot diff files
			try (Stream<Path> stream = Files.list(diffDir)) {
				stream.filter(file -> !Files.isDirectory(file))
						.map(Path::getFileName)
						.map(Path::toString)
						.filter(filename -> filename.matches("diff_.*(sct2|der2)_.*(Snapshot).*") && !filename.matches("diff_.*_no_(first|1_7)_col.txt"))
						.sorted(String::compareToIgnoreCase)
						.forEach(filename -> processFile(diffDir.toString(), filename));
			}

			// Delete diff files
			try (Stream<Path> stream = Files.walk(Path.of("results", uploadFolder))) {
				stream.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Error processing diff files in " + uploadFolder);
			throw new TermServerScriptException(e);
		}
	}

	private void outputResults() throws TermServerScriptException {
		String previousFilename = null;
		int[] langRefsetTotals = null;

		for (Map.Entry<String, int[]> entry : fileTotals.entrySet()) {

			String filename = entry.getKey();
			int[] fileTotals = entry.getValue();

			if (previousFilename != null && !filename.contains("der2_cRefset_Language")) {
				report("Total:", langRefsetTotals);
				reportSCS(languageSubTotals);
				previousFilename = null;
				langRefsetTotals = null;
			}

			report(filename, fileTotals);

			if (filename.contains("sct2_Concept_")) {
				reportSCS(totals[TAB_CONCEPTS]);

			} else if (filename.contains("sct2_Description_")) {
				reportSCS(descriptionStatsByLanguage.get(DESCRIPTION).get(getLanguage(filename)));

			} else if (filename.contains("sct2_TextDefinition_")) {
				reportSCS(descriptionStatsByLanguage.get(TEXT_DEFINITION).get(getLanguage(filename)));

			} else if (filename.contains("sct2_Relationship_")) {
				reportSCS(totals[TAB_RELS]);

			} else if (filename.contains("sct2_RelationshipConcreteValues_")) {
				reportSCS(totals[TAB_CD]);

			} else if (filename.contains("sct2_sRefset_OWLExpression")) {
				reportSCS(totals[TAB_AXIOMS]);

			} else if (filename.contains("der2_cRefset_AttributeValue")) {
				reportSCS(indicatorSubTotals);

			} else if (filename.contains("der2_cRefset_Association")) {
				reportSCS(associationSubTotals);

			} else if (filename.contains("der2_cRefset_Language")) {
				if (previousFilename == null) {
					previousFilename = filename;
					langRefsetTotals = new int[fileTotals.length];
				}
				for (int i = 0; i < fileTotals.length; i++) {
					langRefsetTotals[i] += fileTotals[i];
				}
			}
		}
	}

	private String getLanguage(String filename) {
		String language = null;
		if (filename.contains(DASH)) {
			language = filename.substring(filename.indexOf(DASH)).substring(1, 3);
		}
		return language;
	}

	private void report(String filename, int[] fileTotals) throws TermServerScriptException {
		Object[] details = new Object[NUMBER_OF_COLUMNS];
		int i = 0;
		details[i] = filename;
		while (i < fileTotals.length) {
			details[i + 1] = fileTotals[i++];
		}
		report(FILE_COMPARISON_TAB, details);
	}

	private void reportSCS(int[] subTotals) throws TermServerScriptException {
		int[] totals = new int[]{0, 0, 0, 0, 0, 0};

		if (subTotals != null) {
			totals[TotalsIndex.NEW.ordinal()] = subTotals[IDX_NEW];
			totals[TotalsIndex.CHANGED.ordinal()] = subTotals[IDX_CHANGED] + subTotals[IDX_MOVED_MODULE] + subTotals[IDX_CHANGED_INACTIVE]; // + more?
			totals[TotalsIndex.INACTIVATED.ordinal()] = subTotals[IDX_INACTIVATED];
			totals[TotalsIndex.REACTIVATED.ordinal()] = subTotals[IDX_REACTIVATED];
			totals[TotalsIndex.DELETED.ordinal()] = 0; // No deleted in SCS report
		}

		totals[TotalsIndex.TOTAL.ordinal()] = Arrays.stream(totals).sum();

		report("-- Summary Component Stats --", totals);
	}

	private String doubleQuote(String arg) {
		return "\"" + arg + "\"";
	}

	private void processFile(String path, String filename) {
		Map<String, String> created = new HashMap<>();
		Map<String, String> deleted = new HashMap<>();
		Map<String, ValuePair> changed = new HashMap<>();
		Map<String, ValuePair> inactivated = new HashMap<>();
		Map<String, ValuePair> reactivated = new HashMap<>();

		try (BufferedReader br = new BufferedReader(new FileReader(path + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;

			while ((line = br.readLine()) != null) {
				char ch = line.charAt(0);

				if (!(ch == LINE_DELETED_INDICATOR || ch == LINE_CREATED_INDICATOR)) {
					continue;
				}

				// Start from index = 2 to exclude "<" or ">" and the following space and
				// split into an id part (key) and the rest (value)
				String[] cols = line.substring(2).split(FIELD_DELIMITER, 2);

				String key = cols[0];
				String value = cols[1];

				switch (ch) {
					case LINE_DELETED_INDICATOR:
						// Previous release entry
						/*if (created.containsKey(key)) {
							String newValue = created.remove(key);
							ValuePair valuePair = new ValuePair(value, newValue);
							if (valuePair.isInactivated()) {
								inactivated.put(key, valuePair);
							} else if (valuePair.isReactivated()) {
								reactivated.put(key, valuePair);
							} else {
								changed.put(key, valuePair);
							}
						} else {*/
							deleted.put(key, value);
						//}
						break;

					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.containsKey(key)) {
							String oldValue = deleted.remove(key);
							ValuePair valuePair = new ValuePair(oldValue, value);
							if (valuePair.isInactivated()) {
								inactivated.put(key, valuePair);
							} else if (valuePair.isReactivated()) {
								reactivated.put(key, valuePair);
							} else {
								changed.put(key, valuePair);
							}
						} else {
							created.put(key, value);
						}
						break;
				}
			}

			//assert deleted.size() == 0;

			int total = created.size() + changed.size() + inactivated.size() + reactivated.size() + deleted.size();

			// Debugging
			/*if (filename.contains("AttributeValue")) {
				reactivated.entrySet().forEach(entry -> {
					try {
						report(MAX_REPORT_TABS + PRIMARY_REPORT, entry.getKey(), entry.getValue().previousValue, entry.getValue().currentValue);
					} catch (TermServerScriptException e) {
					}
				});
			}*/

			fileTotals.put(filename, new int[] {
					created.size(),
					changed.size(),
					inactivated.size(),
					reactivated.size(),
					deleted.size(),
					total
			});

		} catch (IOException | IndexOutOfBoundsException e) {
			LOGGER.error("Error processing file: " + filename);
			throw new RuntimeException(e);
		}
	}

	static class ValuePair {
		// Previous value in the pair contains a line marked "<" starting from the second column (i.e. excludes id)
		// Current value contains a matching line marked ">", also starting from the second column (i.e. excludes id)
		// Stripped id is the same for the pair of values

		// Index of the "active" indicator column in the value string
		static final int DIFF_IDX_ACTIVE = IDX_ACTIVE - 1;

		String previousValue;
		String currentValue;

		ValuePair(String previousValue, String currentValue) {
			this.previousValue = previousValue;
			this.currentValue = currentValue;
		}

		boolean isInactivated() {
			// Split previous and current value strings into parts (columns) and
			// compare their second column, i.e. "active" indicator
			return ACTIVE_FLAG.equals(previousValue.split(FIELD_DELIMITER)[DIFF_IDX_ACTIVE]) &&
					INACTIVE_FLAG.equals(currentValue.split(FIELD_DELIMITER)[DIFF_IDX_ACTIVE]);
		}

		boolean isReactivated() {
			// Split previous and current value strings into parts (columns) and
			// compare their second column, i.e. "active" indicator
			return INACTIVE_FLAG.equals(previousValue.split(FIELD_DELIMITER)[DIFF_IDX_ACTIVE]) &&
					ACTIVE_FLAG.equals(currentValue.split(FIELD_DELIMITER)[DIFF_IDX_ACTIVE]);
		}
	}

	enum TotalsIndex {
		NEW,
		CHANGED,
		INACTIVATED,
		REACTIVATED,
		DELETED,
		TOTAL
	}
}
