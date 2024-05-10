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
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PackageComparisonReport extends SummaryComponentStats implements ReportClass {
	private static final Logger LOGGER = LoggerFactory.getLogger(PackageComparisonReport.class);
	private static final String SCRIPT_NAME = "run-compare-packages.sh";
	private static final char LINE_DELETED_INDICATOR = '<';
	private static final char LINE_CREATED_INDICATOR = '>';
	private static final int TIMEOUT_MINUTES = 30;
	private static final int FILE_COMPARISON_TAB = MAX_REPORT_TABS;

	public static final String SCTID_SE_REFSETID = "734138000";
	public static final String SCTID_SP_REFSETID = "734139008";

	private String previousReleasePath;
	private String currentReleasePath;
	private Map<String, Map<TotalsIndex, Integer>> fileTotals = new TreeMap<>();
	
	private Map<String, Integer> leftFilesLineCounts = new HashMap<>();
	private Map<String, Integer> rightFilesLineCounts = new HashMap<>();
	private Map<String, Integer> headersDiffCounts = new HashMap<>();
	private static final String LEFT_FILES_LINE_COUNT_FILENAME = "left_files_line_counts.txt";
	private static final String RIGHT_FILES_LINE_COUNT_FILENAME = "right_files_line_counts.txt";
	private static final String HEADERS_DIFF_FILENAME = "diff__headers.txt";
	private static final String FILES_DIFF_FILENAME = "diff__files.txt";
	
	private final String[] columnHeadings = new String[] {
			"Filename, Header, New, Changed, Inactivated, Reactivated, Moved Module, Promoted, New Inactive, Changed Inactive, Deleted, Total"
	};

	private final String[] tabNames = new String[] {
			"File Comparison"
	};

	enum TotalsIndex {
		HEADER,
		NEW,
		CHANGED,
		INACTIVATED,
		REACTIVATED,
		MOVED_MODULE,
		PROMOTED,
		NEW_INACTIVE,
		CHANGED_INACTIVE,
		DELETED,
		TOTAL
	}

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();

		// International
		/*params.put(PREV_RELEASE, "international/international_edition_releases/previous/SnomedCT_InternationalRF2_PRODUCTION_20231201T120000Z.zip");
		params.put(THIS_RELEASE, "international/international_edition_releases/current/SnomedCT_InternationalRF2_PRODUCTION_20240101T120000Z.zip");*/
		params.put(THIS_RELEASE, "international/international_edition_releases/current/xSnomedCT_InternationalRF2_PREPRODUCTION_20240601T120000Z_version3.zip");
		params.put(PREV_RELEASE, "international/international_edition_releases/previous/xSnomedCT_InternationalRF2_PREPRODUCTION_20240601T120000Z_version2.zip");

		// US Edition
		/*params.put(THIS_RELEASE, "us/us_edition_releases/current/xSnomedCT_ManagedServiceUS_PREPRODUCTION_US1000124_20240301T120000Z.zip");
		params.put(PREV_RELEASE, "us/us_edition_releases/previous/SnomedCT_ManagedServiceUS_PRODUCTION_US1000124_20230901T120000Z.zip");
		//params.put(MODULES, "731000124108,5991000124107,449080006");*/

		// NL Edition
		/*params.put(PREV_RELEASE, "nlfix/snomed_ct_netherlands_release_sept_22_only/2022-09-22T14:00:19/output-files/SnomedCT_ManagedServiceNL_PRODUCTION_NL1000146_20220930T120000Z.zip");
		params.put(THIS_RELEASE, "nl/snomed_ct_netherlands_releases/2023-03-07T02:35:31/output-files/xSnomedCT_ManagedServiceNL_PREPRODUCTION_NL1000146_20230331T120000Z.zip");
		params.put(MODULES, "11000146104");*/

		// ES Extension
		/*params.put(PREV_RELEASE, "ee/estonia_extension_releases/2022-11-15T15:50:50/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20221130T120000Z.zip");
		params.put(THIS_RELEASE, "ee/estonia_extension_releases/2023-05-23T08:35:57/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20230530T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220831T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230228T120000Z.zip");
		params.put(MODULES, "11000181102");*/

		// SE Extension
		/*params.put(THIS_RELEASE, "se/snomed_ct_sweden_extension_releases/2023-11-22T17:15:10/output-files/SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20231130T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230731T120000Z.zip");
		params.put(PREV_RELEASE, "se/snomed_ct_sweden_extension_releases/2023-05-19T07:39:38/output-files/SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20230531T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip");
		params.put(MODULES, "45991000052106");*/

		// DK Extension
		/*params.put(PREV_RELEASE, "dk/snomed_ct_denmark_extension_releases/2022-09-23T12:02:14/output-files/SnomedCT_ManagedServiceDK_PRODUCTION_DK1000005_20220930T120000Z.zip");
		params.put(THIS_RELEASE, "dk/snomed_ct_denmark_extension_releases/2023-03-18T21:23:11/output-files/xSnomedCT_ManagedServiceDK_PREPRODUCTION_DK1000005_20230331T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220630T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230228T120000Z.zip");
		params.put(MODULES, "554471000005108");*/

		// BE Extension
		/*params.put(PREV_RELEASE, "be/snomed_ct_belgium_extension_releases/2021-09-10T15:10:46/output-files/SnomedCT_BelgiumExtensionRF2_PRODUCTION_20210915T120000Z.zip");
		params.put(THIS_RELEASE, "be/snomed_ct_belgium_extension_releases/2022-03-01T17:54:15/output-files/xSnomedCT_BelgiumExtensionRF2_PREPRODUCTION_20220315T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20210731T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220131T120000Z.zip");
		params.put(MODULES, "11000172109");*/

		/*params.put(THIS_RELEASE, "be/snomed_ct_belgium_extension_releases/current/xSnomedCT_ManagedServiceBE_PREPRODUCTION_BE1000172_20240515T120000Z_version4.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20240201T120000Z.zip");
		params.put(PREV_RELEASE, "be/snomed_ct_belgium_extension_releases/previous/SnomedCT_ManagedServiceBE_PRODUCTION_BE1000172_20231115T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230901T120000Z.zip");
		params.put(MODULES, "11000172109");*/

		// NZ Extension
		/*params.put(PREV_RELEASE, "nz/snomed_ct_new_zealand_extension_releases/2022-09-28T15:24:25/output-files/SnomedCT_ManagedServiceNZ_PRODUCTION_NZ1000210_20221001T000000Z.zip");
		params.put(THIS_RELEASE, "nz/snomed_ct_new_zealand_extension_releases/2023-04-21T07:24:12/output-files/xSnomedCT_ManagedServiceNZ_PREPRODUCTION_NZ1000210_20230401T000000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220731T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230131T120000Z.zip");
		params.put(MODULES, "11000210104,21000210109");*/

		// IE Extension
		/*params.put(PREV_RELEASE, "ie/snomed_ct_ireland_extension_releases/2022-10-19T16:18:12/output-files/SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20221021T120000Z.zip");
		params.put(THIS_RELEASE, "ie/snomed_ct_ireland_extension_releases/2023-04-12T17:05:21/output-files/xSnomedCT_ManagedServiceIE_PREPRODUCTION_IE1000220_20230421T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20220731T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230228T120000Z.zip");
		params.put(MODULES, "11000220105");*/

		// CH Extension
		/*params.put(THIS_RELEASE, "ch/snomed_ct_switzerland_releases/2023-12-05T08:16:33/output-files/SnomedCT_ManagedServiceCH_PRODUCTION_CH1000195_20231207T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20231101T120000Z.zip");
		params.put(PREV_RELEASE, "ch/snomed_ct_switzerland_releases/2023-05-25T10:27:16/output-files/SnomedCT_ManagedServiceCH_PRODUCTION_CH1000195_20230607T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20230430T120000Z.zip");
		params.put(MODULES, "2011000195101");*/

		TermServerReport.run(PackageComparisonReport.class, args, params);
	}

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		previousReleasePath = run.getParamValue(PREV_RELEASE);
		currentReleasePath = run.getParamValue(THIS_RELEASE);
		super.init(run);
	}

	@Override
	public String[] getTabNames() {
		return concatArrays(super.getTabNames(), tabNames);
	}

	@Override
	public String[] getColumnHeadings() {
		return concatArrays(super.getColumnHeadings(), columnHeadings);
	}

	private String[] concatArrays(String[] array1, String[] array2) {
		String[] result = Arrays.copyOf(array1, array1.length + array2.length);
		System.arraycopy(array2, 0, result, array1.length, array2.length);
		return result;
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.BUILD_ARCHIVE)
				.add(THIS_DEPENDENCY).withType(JobParameter.Type.STRING)
				.add(PREV_RELEASE).withType(JobParameter.Type.BUILD_ARCHIVE)
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

	@Override
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
				reader.lines().forEach(LOGGER::info); //debug
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

	private String getFilename(Path file) {
		return file.getFileName().toString();
	}

	private boolean matches(String filename) {
		return filename.matches("(sct2|der2)_.*(Snapshot).*") && !filename.matches("(sct2|der2)_.*_no_(first|1_7)_col.txt");
	}


	private void processFiles(String uploadFolder) throws TermServerScriptException {
		Path runDir = Path.of("results", uploadFolder);
		Path diffDir = Path.of("results", uploadFolder, "target", "c");

		try {
			// Load the files containing the line counts for the left and right files and header diff count
			loadTabDelimitedFile(diffDir, LEFT_FILES_LINE_COUNT_FILENAME, leftFilesLineCounts);
			loadTabDelimitedFile(diffDir, RIGHT_FILES_LINE_COUNT_FILENAME, rightFilesLineCounts);
			loadTabDelimitedFile(diffDir, HEADERS_DIFF_FILENAME, headersDiffCounts);

			// Process files list diff file
			processFilesList(diffDir, FILES_DIFF_FILENAME);

			report(FILE_COMPARISON_TAB, "Files changed:");

			// Process content diff files (snapshot files only)
			try (Stream<Path> stream = Files.list(diffDir)) { //.filter(file -> !Files.isDirectory(file))) {
				stream
						.map(this::getFilename)
						.filter(this::matches)
						.sorted(String::compareToIgnoreCase)
						.forEach(filename -> process(diffDir, filename));
			}

			// Add header diff count
			for (Map.Entry<String, Integer> entry : headersDiffCounts.entrySet()) {
				fileTotals.computeIfPresent(entry.getKey(), (k, v) -> {
					v.computeIfPresent(TotalsIndex.HEADER, (k1, v1) -> entry.getValue());
					return v;
				});
			}

			// Delete all temporary and diff files after processing
			try (Stream<Path> stream = Files.walk(runDir)) {
				stream.sorted(Comparator.reverseOrder())
						.map(Path::toFile)
						.forEach(File::delete);
			}
		} catch (IOException | RuntimeException e) {
			LOGGER.error("Error processing diff files in " + uploadFolder);
			throw new TermServerScriptException(e);
		}
	}

	private void loadTabDelimitedFile(Path outputDir, String filename, Map<String, Integer> files) {
		try (BufferedReader br = new BufferedReader(new FileReader(outputDir + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				String[] parts = line.split(TAB);
				String name = parts[0];
				if (!matches(name)) {
					continue;
				}
				Integer count = Integer.valueOf(parts[1]);
				files.put(name, count);
			}
		} catch (Exception e) {
			LOGGER.warn("Failed to load " + filename + " file due to " + e);
		}
	}

	private void outputResults() throws TermServerScriptException {
		Map<TotalsIndex, Integer> descriptionTotals = new EnumMap<>(TotalsIndex.class);
		Map<TotalsIndex, Integer> textDefinitionTotals = new EnumMap<>(TotalsIndex.class);
		Map<TotalsIndex, Integer> languageRefsetTotals = new EnumMap<>(TotalsIndex.class);

		for (TotalsIndex index : TotalsIndex.values()) {
			descriptionTotals.put(index, 0);
			textDefinitionTotals.put(index, 0);
			languageRefsetTotals.put(index, 0);
		}

		boolean isDescription = false;
		boolean isTextDefinition = false;
		boolean isLanguageRefset = false;
		boolean isAssociation = false;

		Map<String, Map<TotalsIndex, Integer>> fileTotalsWithoutComparison = new TreeMap<>();

		for (Map.Entry<String, Map<TotalsIndex, Integer>> entry : fileTotals.entrySet()) {
			String filename = entry.getKey();
			Map<TotalsIndex, Integer> fileTotalsEntry = entry.getValue();

			// Output descriptions totals
			if (isDescription && !filename.contains("sct2_Description_")) {
				report("Total Descriptions:", descriptionTotals, totals[TAB_DESCS]);
				isDescription = false;
			}

			// Output text definitions totals
			if (isTextDefinition && !filename.contains("sct2_TextDefinition_")) {
				report("Total Text Definitions:", textDefinitionTotals, totals[TAB_TEXT_DEFN]);
				isTextDefinition = false;
			}

			// Output language refsets totals
			if (isLanguageRefset && !filename.contains("der2_cRefset_Language")) {
				report("Total Language Refsets:", languageRefsetTotals, totals[TAB_LANG]);
				isLanguageRefset = false;
			}

			// Output associations totals
			if (isAssociation && !filename.contains("der2_cRefset_Association")) {
				report(sumOfTabs(TAB_HIST, TAB_DESC_HIST));
				isAssociation = false;
			}

			if (filename.contains("sct2_Description_")) {
				isDescription = true;
				report(filename, fileTotalsEntry, null);
				for (TotalsIndex index : TotalsIndex.values()) {
					descriptionTotals.compute(index, (k, v) -> v + fileTotalsEntry.get(index));
				}
			} else if (filename.contains("sct2_TextDefinition_")) {
				isTextDefinition = true;
				report(filename, fileTotalsEntry, null);
				for (TotalsIndex index : TotalsIndex.values()) {
					textDefinitionTotals.compute(index, (k, v) -> v + fileTotalsEntry.get(index));
				}
			} else if (filename.contains("der2_cRefset_Language")) {
				isLanguageRefset = true;
				report(filename, fileTotalsEntry, null);
				for (TotalsIndex index : TotalsIndex.values()) {
					languageRefsetTotals.compute(index, (k, v) -> v + fileTotalsEntry.get(index));
				}
			} else if (filename.contains("sct2_Concept_")) {
				report(filename, fileTotalsEntry, totals[TAB_CONCEPTS]);
			} else if (filename.contains("sct2_Relationship_")) {
				report(filename, fileTotalsEntry, totals[TAB_RELS]);
			} else if (filename.contains("sct2_RelationshipConcreteValues_")) {
				report(filename, fileTotalsEntry, totals[TAB_CD]);
			} else if (filename.contains("sct2_sRefset_OWLExpression")) {
				report(filename, fileTotalsEntry, totals[TAB_AXIOMS]);
			} else if (filename.contains("der2_cRefset_AttributeValue")) {
				report(filename, fileTotalsEntry, sumOfTabs(TAB_INACT_IND, TAB_DESC_CNC, TAB_DESC_INACT));
			} else if (filename.contains("der2_cRefset_Association")) {
				isAssociation = true;
				report(filename, fileTotalsEntry, null);
			} else {
				fileTotalsWithoutComparison.put(filename, fileTotalsEntry);
			}
		}

		for (Map.Entry<String, Map<TotalsIndex, Integer>> entry : fileTotalsWithoutComparison.entrySet()) {
			report(entry.getKey(), entry.getValue(), null);
		}
	}

	private int[] sumOfTabs(int...tabs) {
		int[] result = new int[DATA_WIDTH];
		for (int i = 0; i < DATA_WIDTH; i++) {
			for (int tab : tabs) {
				result[i] += totals[tab][i];
			}
		}
		return result;
	}

	private void report(String filename, Map<TotalsIndex, Integer> fileTotals, int[] scsTotals) throws TermServerScriptException {
		report(filename, fileTotals);
		report(scsTotals);
	}

	private void report(String filename, Map<TotalsIndex, Integer> fileTotals) throws TermServerScriptException {
		Object[] fileDetails = new Object[TotalsIndex.values().length + 1];

		fileDetails[0] = filename;
		for (TotalsIndex index : TotalsIndex.values()) {
			fileDetails[index.ordinal() + 1] = fileTotals.get(index);
		}
		report(FILE_COMPARISON_TAB, fileDetails);
	}

	private void report(int[] scsTotals) throws TermServerScriptException {
		if (scsTotals != null) {
			Object[] scsDetails = new Object[TotalsIndex.values().length + 1];

			scsDetails[0] = "-- Summary Component Stats --";
			scsDetails[TotalsIndex.NEW.ordinal() + 1] = scsTotals[IDX_NEW];
			scsDetails[TotalsIndex.CHANGED.ordinal() + 1] = scsTotals[IDX_CHANGED];
			scsDetails[TotalsIndex.INACTIVATED.ordinal() + 1] = scsTotals[IDX_INACTIVATED];
			scsDetails[TotalsIndex.REACTIVATED.ordinal() + 1] = scsTotals[IDX_REACTIVATED];
			scsDetails[TotalsIndex.MOVED_MODULE.ordinal() + 1] = scsTotals[IDX_MOVED_MODULE];
			scsDetails[TotalsIndex.PROMOTED.ordinal() + 1] = scsTotals[IDX_PROMOTED];
			scsDetails[TotalsIndex.NEW_INACTIVE.ordinal() + 1] = scsTotals[IDX_NEW_INACTIVE];
			scsDetails[TotalsIndex.CHANGED_INACTIVE.ordinal() + 1] = scsTotals[IDX_CHANGED_INACTIVE];
			scsDetails[TotalsIndex.DELETED.ordinal() + 1] = 0; // missing in SCS

			scsDetails[TotalsIndex.TOTAL.ordinal() + 1] =
					scsTotals[IDX_NEW] +
							scsTotals[IDX_CHANGED] +
							scsTotals[IDX_INACTIVATED] +
							scsTotals[IDX_REACTIVATED] +
							scsTotals[IDX_MOVED_MODULE] +
							scsTotals[IDX_PROMOTED] +
							scsTotals[IDX_NEW_INACTIVE] +
							scsTotals[IDX_CHANGED_INACTIVE];

			report(FILE_COMPARISON_TAB, scsDetails);
			report(FILE_COMPARISON_TAB, "");
		}
	}

	private String doubleQuote(String arg) {
		return "\"" + arg + "\"";
	}

	private void processFilesList(Path path, String filename) throws TermServerScriptException {
		Set<String> created = new TreeSet<>();
		Set<String> deleted = new TreeSet<>();

		try (BufferedReader br = new BufferedReader(new FileReader(path + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;

			while ((line = br.readLine()) != null) {
				char ch = line.charAt(0);

				if (!(ch == LINE_DELETED_INDICATOR || ch == LINE_CREATED_INDICATOR)) {
					continue;
				}

				// Start from index = 2 to exclude "<" or ">" and a space
				String value = line.substring(2);

				if (!matches(value)) {
					continue;
				}

				switch (ch) {
					// For the same component deleted indicator always comes before created indicator in the file
					case LINE_DELETED_INDICATOR:
						// Previous release entry
						if (created.contains(value)) {
							created.remove(value);
						} else {
							deleted.add(value);
						}
						break;
					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.contains(value)) {
							deleted.remove(value);
						} else {
							created.add(value);
						}
						break;
				}
			}

			report(FILE_COMPARISON_TAB, "Files created: " + created.size());
			for (String file : created) {
				if (rightFilesLineCounts.containsKey(file)) {
					Integer lineCount = rightFilesLineCounts.get(file);
					Map<TotalsIndex, Integer> totals = new EnumMap<>(TotalsIndex.class);
					for (TotalsIndex index : TotalsIndex.values()) {
						totals.put(index, 0);
					}
					totals.put(TotalsIndex.NEW, lineCount);
					totals.put(TotalsIndex.TOTAL, lineCount);
					fileTotals.put(file, totals);
				}
				report(FILE_COMPARISON_TAB, file, "* See line count without header line in the NEW column in the section below");
			}
			report(FILE_COMPARISON_TAB, "");

			report(FILE_COMPARISON_TAB, "Files deleted: " + deleted.size());
			for (String file : deleted) {
				if (leftFilesLineCounts.containsKey(file)) {
					Integer lineCount = leftFilesLineCounts.get(file);
					Map<TotalsIndex, Integer> totals = new EnumMap<>(TotalsIndex.class);
					for (TotalsIndex index : TotalsIndex.values()) {
						totals.put(index, 0);
					}
					totals.put(TotalsIndex.DELETED, lineCount);
					totals.put(TotalsIndex.TOTAL, lineCount);
					fileTotals.put(file, totals);
				}
				report(FILE_COMPARISON_TAB, file, "* See line count without header line the DELETED column in the section below");
			}
			report(FILE_COMPARISON_TAB, "");

		} catch (IOException | IndexOutOfBoundsException e) {
			LOGGER.error("Error processing file: " + filename);
			throw new TermServerScriptException(e);
		}
	}

	private void process(Path path, String filename) {
		if (filename.contains("der2_cRefset_Association")) {
			processAssociationFile(path, filename, null);
			processAssociationFile(path, filename, Set.of(SCTID_SE_REFSETID, SCTID_SP_REFSETID));
		} else if (filename.contains("sct2_Concept_")) {
			processConceptFile(path, filename);
 		} else {
			processFile(path, filename);
		}
	}

	private void processFile(Path path, String filename) {
		Map<String, String[]> created = new HashMap<>();
		Map<String, String[]> deleted = new HashMap<>();

		Map<TotalsIndex, Integer> totals = new EnumMap<>(TotalsIndex.class);

		for (TotalsIndex index : TotalsIndex.values()) {
			totals.put(index, 0);
		}

		try (BufferedReader br = new BufferedReader(new FileReader(path + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;

			while ((line = br.readLine()) != null) {
				char ch = line.charAt(0);

				if (!(ch == LINE_DELETED_INDICATOR || ch == LINE_CREATED_INDICATOR)) {
					continue;
				}

				// Start from index = 2 to exclude "<" or ">" and the following space and split into parts:
				// 0 - id
				// 1 - effectiveTime
				// 2 - active
				// 3 - moduleId, etc.
				String[] data = line.substring(2).split(FIELD_DELIMITER);
				String key = data[IDX_ID];
				String moduleId = data[IDX_MODULEID];

				switch (ch) {
					// For the same component, the "deleted" indicator always comes before the "created" indicator in the file
					case LINE_DELETED_INDICATOR:
						if (moduleFilter == null || moduleFilter.contains(moduleId)) {
							// Previous release entry
							deleted.put(key, data);
						}
						break;
					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.containsKey(key)) {
							String[] oldValue = deleted.remove(key);

							if (moduleFilter == null || moduleFilter.contains(moduleId)) {
								ValuePair valuePair = new ValuePair(oldValue, data);
								if (valuePair.isActive()) {
									if (valuePair.isChanged(thisEffectiveTime, previousEffectiveTime)) {
										count(totals, TotalsIndex.CHANGED);
									}
								} else if (valuePair.isInactive()) {
									if (valuePair.isChanged(thisEffectiveTime, previousEffectiveTime)) {
										count(totals, TotalsIndex.CHANGED_INACTIVE);
									}
								} else if (valuePair.isInactivated()) {
									count(totals, TotalsIndex.INACTIVATED);
								} else if (valuePair.isReactivated()) {
									count(totals, TotalsIndex.REACTIVATED);
								}
							} else {
								//count(totals, TotalsIndex.PROMOTED);
							}
						} else if (moduleFilter == null || moduleFilter.contains(moduleId)) {
							created.put(key, data);
						}
						break;
				}
			}

			// Calculate created and deleted totals
			totals.put(TotalsIndex.NEW, created.values().stream().filter(data -> ACTIVE_FLAG.equals(data[IDX_ACTIVE])).toList().size());
			totals.put(TotalsIndex.NEW_INACTIVE, created.values().stream().filter(data -> INACTIVE_FLAG.equals(data[IDX_ACTIVE])).toList().size());
			totals.put(TotalsIndex.DELETED, deleted.size());

			// Calculate total of all changes
			totals.put(TotalsIndex.TOTAL, totals.values().stream().reduce(0, Integer::sum));

			fileTotals.put(filename, totals);

		} catch (IOException | IndexOutOfBoundsException e) {
			LOGGER.error("Error processing file: " + filename);
			throw new RuntimeException(e);
		}
	}

	private void processAssociationFile(Path path, String filename, Set<String> refsetIds) {
		Map<String, String[]> created = new HashMap<>();
		Map<String, String[]> deleted = new HashMap<>();

		Map<TotalsIndex, Integer> totals = new EnumMap<>(TotalsIndex.class);

		for (TotalsIndex index : TotalsIndex.values()) {
			totals.put(index, 0);
		}

		try (BufferedReader br = new BufferedReader(new FileReader(path + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;

			while ((line = br.readLine()) != null) {
				char ch = line.charAt(0);

				if (!(ch == LINE_DELETED_INDICATOR || ch == LINE_CREATED_INDICATOR)) {
					continue;
				}

				// Start from index = 2 to exclude "<" or ">" and the following space and split into parts:
				// 0 - id
				// 1 - effectiveTime
				// 2 - active
				// 3 - moduleId
				// 4 - refsetId, etc
				String[] data = line.substring(2).split(FIELD_DELIMITER);
				String key = data[ASSOC_IDX_ID];
				String moduleId = data[ASSOC_IDX_MODULID];

				if (refsetIds != null && !refsetIds.contains(data[ASSOC_IDX_REFSETID])) {
					continue;
				}

				switch (ch) {
					// For the same component, the "deleted" indicator always comes before the "created" indicator in the file
					case LINE_DELETED_INDICATOR:
						// Previous release entry
						if (moduleFilter == null || moduleFilter.contains(moduleId)) {
							deleted.put(key, data);
						}
						break;
					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.containsKey(key)) {
							String[] oldValue = deleted.remove(key);

							if (moduleFilter == null || moduleFilter.contains(moduleId)) {
								ValuePair valuePair = new ValuePair(oldValue, data);
								if (valuePair.isActive()) {
									// Changed since last release
									if (valuePair.isChanged(thisEffectiveTime, previousEffectiveTime)) {
										count(totals, TotalsIndex.CHANGED);
									}
								} else if (valuePair.isInactive()) {
									// Changed since last release
									if (valuePair.isChanged(thisEffectiveTime, previousEffectiveTime)) {
										count(totals, TotalsIndex.CHANGED_INACTIVE);
									}
								} else if (valuePair.isInactivated()) {
									count(totals, TotalsIndex.INACTIVATED);
								} else if (valuePair.isReactivated()) {
									count(totals, TotalsIndex.REACTIVATED);
								}
							} else {
								//count(totals, TotalsIndex.PROMOTED);
							}
						} else if (moduleFilter == null || moduleFilter.contains(moduleId)) {
							created.put(key, data);
						}
						break;
				}
			}

			// Calculate created and deleted totals
			totals.put(TotalsIndex.NEW, created.values().stream().filter(data -> ACTIVE_FLAG.equals(data[IDX_ACTIVE])).toList().size());
			totals.put(TotalsIndex.NEW_INACTIVE, created.values().stream().filter(data -> INACTIVE_FLAG.equals(data[IDX_ACTIVE])).toList().size());
			totals.put(TotalsIndex.DELETED, deleted.size());

			// Calculate total of all changes
			totals.put(TotalsIndex.TOTAL, totals.values().stream().reduce(0, Integer::sum));

			fileTotals.put(filename + " " + (refsetIds == null ? "[ALL REFSETS]" : "[SEP REFSETS: " + String.join(",", refsetIds) + "]"), totals);

		} catch (IOException | IndexOutOfBoundsException e) {
			LOGGER.error("Error processing file: " + filename);
			throw new RuntimeException(e);
		}
	}

	private void processConceptFile(Path path, String filename) {
		Map<String, String[]> created = new HashMap<>();
		Map<String, String[]> deleted = new HashMap<>();

		Map<TotalsIndex, Integer> totals = new EnumMap<>(TotalsIndex.class);

		for (TotalsIndex index : TotalsIndex.values()) {
			totals.put(index, 0);
		}

		try (BufferedReader br = new BufferedReader(new FileReader(path + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;

			while ((line = br.readLine()) != null) {
				char ch = line.charAt(0);

				if (!(ch == LINE_DELETED_INDICATOR || ch == LINE_CREATED_INDICATOR)) {
					continue;
				}

				// Start from index = 2 to exclude "<" or ">" and the following space and split into parts:
				// 0 - id
				// 1 - effectiveTime
				// 2 - active
				// 3 - moduleId
				// 4 - definitionStatusId
				String[] data = line.substring(2).split(FIELD_DELIMITER);
				String key = data[CON_IDX_ID];
				String moduleId = data[CON_IDX_MODULID];

				switch (ch) {
					// For the same component, the "deleted" indicator always comes before the "created" indicator in the file
					case LINE_DELETED_INDICATOR:
						if (moduleFilter == null || moduleFilter.contains(moduleId)) {
							// Previous release entry
							deleted.put(key, data);
						}
						break;
					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.containsKey(key)) {
							String[] oldValue = deleted.remove(key);

							if (moduleFilter == null || moduleFilter.contains(moduleId)) {
								ValuePair valuePair = new ValuePair(oldValue, data);
								if (valuePair.isActive()) {
									// Remains active and changed since last release
									if (valuePair.isChanged(thisEffectiveTime, previousEffectiveTime)) {
										count(totals, TotalsIndex.CHANGED);
									}
								} else if (valuePair.isInactive()) {
									// Remains inactive and changed since last release
									if (valuePair.isChanged(thisEffectiveTime, previousEffectiveTime)) {
										count(totals, TotalsIndex.CHANGED_INACTIVE);
									}
								} else if (valuePair.isInactivated()) {
									// Inactivated
									count(totals, TotalsIndex.INACTIVATED);
								} else if (valuePair.isReactivated()) {
									// Reactivated
									count(totals, TotalsIndex.REACTIVATED);
								}
								// Active and changed module since last release
								if (valuePair.isActiveMovedModule()) {
									count(totals, TotalsIndex.MOVED_MODULE);
								}
							} else {
								count(totals, TotalsIndex.PROMOTED);
							}
						} else {
							if (moduleFilter == null || moduleFilter.contains(moduleId)) {
								created.put(key, data);
							}
						}
						break;
				}
			}

			// Calculate created and deleted totals
			totals.put(TotalsIndex.NEW, created.values().stream().filter(data -> ACTIVE_FLAG.equals(data[IDX_ACTIVE])).toList().size());
			totals.put(TotalsIndex.NEW_INACTIVE, created.values().stream().filter(data -> INACTIVE_FLAG.equals(data[IDX_ACTIVE])).toList().size());
			totals.put(TotalsIndex.DELETED, deleted.size());

			// Calculate total of all changes
			totals.put(TotalsIndex.TOTAL, totals.values().stream().reduce(0, Integer::sum));

			fileTotals.put(filename, totals);

		} catch (IOException | IndexOutOfBoundsException e) {
			LOGGER.error("Error processing file: " + filename);
			throw new RuntimeException(e);
		}
	}

	private void count(Map<TotalsIndex, Integer> totals, TotalsIndex index) {
		totals.compute(index, (k, v) -> v + 1);
	}

	static class ValuePair {
		// Previous value in the pair contains a line marked "<"
		// Current value contains a matching line marked ">"

		String[] previousValue;
		String[] currentValue;

		ValuePair(String[] previousValue, String[] currentValue) {
			this.previousValue = previousValue;
			this.currentValue = currentValue;
		}

		boolean isChanged(String thisEffectiveTime, String previousEffectiveTime) {
			return thisEffectiveTime.equals(previousEffectiveTime) || currentValue[IDX_EFFECTIVETIME].compareTo(previousEffectiveTime) > 0;
		}

		boolean isActiveMovedModule() {
			return ACTIVE_FLAG.equals(currentValue[IDX_ACTIVE]) && !currentValue[IDX_MODULEID].equals(previousValue[IDX_MODULEID]);
		}

		boolean isActive() {
			return ACTIVE_FLAG.equals(previousValue[IDX_ACTIVE]) && ACTIVE_FLAG.equals(currentValue[IDX_ACTIVE]);
		}

		boolean isInactive() {
			return INACTIVE_FLAG.equals(previousValue[IDX_ACTIVE]) && INACTIVE_FLAG.equals(currentValue[IDX_ACTIVE]);
		}

		boolean isInactivated() {
			return ACTIVE_FLAG.equals(previousValue[IDX_ACTIVE]) && INACTIVE_FLAG.equals(currentValue[IDX_ACTIVE]);
		}

		boolean isReactivated() {
			return INACTIVE_FLAG.equals(previousValue[IDX_ACTIVE]) && ACTIVE_FLAG.equals(currentValue[IDX_ACTIVE]);
		}
	}
}
