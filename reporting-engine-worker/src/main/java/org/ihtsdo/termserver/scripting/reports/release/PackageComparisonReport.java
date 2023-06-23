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
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * This report executes shell scripts that download and compare the content of the two .zip release packages,
 * stored in S3 bucket snomed-releases, which are specified as input report parameters - a full path to archives
 * except the bucket name is expected.
 * The result of the comparison is uploaded to S3 bucket snomed-compares.
 */
public class PackageComparisonReport extends SummaryComponentStats implements ReportClass {
	// Name of the starting script
	private static final String SCRIPT_NAME = "run-compare-packages.sh";
	private String previousReleasePath;
	private String currentReleasePath;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();

		// Estonia release
		//params.put(PREV_RELEASE, "ee/estonia_extension_releases/2022-11-15T15:50:50/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20221130T120000Z.zip");
		//params.put(THIS_RELEASE, "ee/estonia_extension_releases/2023-05-23T08:35:57/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20230530T120000Z.zip");
		//params.put(PREV_DEPENDENCY, "international/international_edition_releases/2022-08-17T08:17:22/output-files/SnomedCT_InternationalRF2_PRODUCTION_20220831T120000Z.zip");
		//params.put(THIS_DEPENDENCY, "international/international_edition_releases/2023-02-16T09:12:41/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230228T120000Z.zip");
		//params.put(MODULES, "11000181102");

		// International on dev
		params.put(THIS_RELEASE, "international/international_edition_releases/2023-06-06T05:13:11/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip");
		params.put(PREV_RELEASE, "international/international_edition_releases/2023-05-10T04:54:06/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230331T120000Z.zip");

		// International on prod
		//params.put(PREV_RELEASE, "international/international_edition_releases/2023-04-19T14:30:06/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230430T120000Z.zip");
		//params.put(THIS_RELEASE, "international/international_edition_releases/2023-05-17T11:48:57/output-files/SnomedCT_InternationalRF2_PRODUCTION_20230531T120000Z.zip");

		TermServerReport.run(PackageComparisonReport.class, args, params);
	}

	@Override
	protected void preInit() {
		// For running the report locally
		//runHeadless(5);
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
				"Filename, Added, Deleted, Updated, Inactivated, All",
				"Script Output"
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
				"Comparison Summary",
				"Log"
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
		TermServerScript.info("In loadProjectSnapshot method, PREV_RELEASE = " + getJobRun().getParamValue(PREV_RELEASE));
		TermServerScript.info("In loadProjectSnapshot method, PREV_DEPENDENCY = " + getJobRun().getParamValue(PREV_DEPENDENCY));

		prevDependency = getJobRun().getParamValue(PREV_DEPENDENCY);
		if (!StringUtils.isEmpty(prevDependency)) {
			setDependencyArchive(prevDependency);

			TermServerScript.info("In loadProjectSnapshot method, setting dependency archive to " + prevDependency);
 		}
		super.loadProjectSnapshot(fsnOnly);
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		TermServerScript.info("In loadCurrentPosition method, THIS_RELEASE = " + getJobRun().getParamValue(THIS_RELEASE));
		TermServerScript.info("In loadCurrentPosition method, THIS_DEPENDENCY = " + getJobRun().getParamValue(THIS_DEPENDENCY));

		thisDependency = getJobRun().getParamValue(THIS_DEPENDENCY);
		if (!StringUtils.isEmpty(thisDependency)) {
			setDependencyArchive(thisDependency);

			TermServerScript.info("In loadCurrentPosition method, setting dependency archive to " + thisDependency);
		}
		super.loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}

	public void runJob() throws TermServerScriptException {
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			throw new TermServerScriptException("Windows operating system detected. This report can only run on Linux or MacOS.");
		}

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

		String command = "scripts/" + SCRIPT_NAME + " " + String.join(" ", scriptArguments);

		ProcessBuilder builder = new ProcessBuilder();
		builder.redirectErrorStream(true);
		builder.command("sh", "-c", command);

		List<String> output = new ArrayList<>();

		try {
			// Execute scripts
			Process process = builder.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				reader.lines().forEach(line -> {
					output.add(line);
					info(line);
				});
			}

			if (process.waitFor(30, TimeUnit.MINUTES)) {
				// Process exited
				int exitValue = process.exitValue();

				if (exitValue != 0) {
					throw new TermServerScriptException("Script execution failed with exit code: " + exitValue);
				}
				info("Script execution finished successfully");
				output.add("Diff files for '" + previousRelease.getPath() + "' and '" + currentRelease.getPath() + "' are uploaded to s3://snomed-compares/" + uploadFolder);
			} else {
				// Process timed out
				throw new TermServerScriptException("Script execution timed out");
			}

			// Process resulting diff files
			Path diffDir = Path.of("builds/" + uploadFolder + "/target/c");

			try (Stream<Path> stream = Files.list(diffDir)) {
				stream.filter(file -> !Files.isDirectory(file))
						.map(Path::getFileName)
						.map(Path::toString)
						.filter(filename -> filename.matches("diff_.*(sct2|der2)_.*(Snapshot).*") && !filename.matches("diff_.*_no_(first|1_7)_col.txt"))
						.sorted(String::compareToIgnoreCase)
						.forEach(filename -> processFile(diffDir.toString(), filename));
			}

			Files.walk(Path.of("builds/" + uploadFolder))
					.sorted(Comparator.reverseOrder())
					.map(Path::toFile)
					.forEach(File::delete);

		} catch (IOException | InterruptedException | TermServerScriptException e) {
			error("Report execution failed", e);
		}

		for (String line: output) {
			report(MAX_REPORT_TABS + SECONDARY_REPORT, line);
		}
		super.runJob();
	}

	private String doubleQuote(String arg) {
		return "\"" + arg + "\"";
	}

	private void processFile(String path, String filename) {
		Map<String, String> deleted = new HashMap<>();
		Map<String, String> created = new HashMap<>();
		Map<String, ValuePair> updated = new HashMap<>();
		Map<String, ValuePair> inactivated = new HashMap<>();

		try (BufferedReader br = new BufferedReader(new FileReader(path + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;

			while ((line = br.readLine()) != null) {
				char ch = line.charAt(0);

				if (!(ch == '<' || ch == '>')) {
					continue;
				}

				// Start from index = 2 to exclude "<" or ">" and the following space and
				// split into an id part (key) and the rest (value)
				String[] cols = line.substring(2).split("\t", 2);

				String key = cols[0];
				String value = cols[1];

				switch (ch) {
					case '<':
						// Previous release entry
						deleted.put(key, value);
						break;

					case '>':
						// Current release entry
						if (deleted.containsKey(key)) {
							String previousValue = deleted.remove(key);
							ValuePair valuePair = new ValuePair(previousValue, value);
							if (valuePair.isInactivated()) {
								inactivated.put(key, valuePair);
							} else {
								updated.put(key, valuePair);
							}
						} else {
							created.put(key, value);
						}
						break;
				}
			}
			int total = created.size() + deleted.size() + updated.size() + inactivated.size();
			report(MAX_REPORT_TABS + PRIMARY_REPORT, filename, created.size(), deleted.size(), updated.size(), inactivated.size(), total);
		} catch (IOException | IndexOutOfBoundsException | TermServerScriptException e) {
			error("Error processing file: " + filename, e);
		}
	}

	static class ValuePair {
		// Previous value in the pair contains a line marked "<" starting from the second column (i.e. excludes id)
		// Current value contains a matching line marked ">", also starting from the second column (i.e. excludes id)
		// Stripped id is the same for the pair of values

		// Index of the "active" indicator column in the value string
		static final int DIFF_IDX_ACTIVE = 1;

		String previousValue;
		String currentValue;

		ValuePair(String previousValue, String currentValue) {
			this.previousValue = previousValue;
			this.currentValue = currentValue;
		}

		boolean isInactivated() {
			// Split previous and current value strings into parts (columns) and
			// compare their second column, i.e. "active" indicator
			return "1".equals(previousValue.split("\t")[DIFF_IDX_ACTIVE]) &&
					"0".equals(currentValue.split("\t")[DIFF_IDX_ACTIVE]);
		}
	}
}