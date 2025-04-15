package org.ihtsdo.termserver.scripting.reports.release;

import org.ihtsdo.otf.exception.TermServerRuntimeException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

public class PackageComparisonReport extends SummaryComponentStats implements ReportClass {
	private static final Logger LOGGER = LoggerFactory.getLogger(PackageComparisonReport.class);
	private static final String SCRIPT_NAME = "run-compare-packages.sh";
	private static final char LINE_DELETED_INDICATOR = '<';
	private static final char LINE_CREATED_INDICATOR = '>';
	private static final int TIMEOUT_MINUTES = 30;
	private static final int FILE_COMPARISON_TAB = MAX_REPORT_TABS;

	private static final String CONCEPT_FILENAME = "sct2_Concept_";
	private static final String DESCRIPTION_FILENAME = "sct2_Description_";
	private static final String TEXT_DEFINITION_FILENAME = "sct2_TextDefinition_";
	private static final String RELATIONSHIP_FILENAME = "sct2_Relationship_";
	private static final String RELATIONSHIP_CONCRETE_VALUES_FILENAME = "sct2_RelationshipConcreteValues_";
	private static final String OWL_EXPRESSION_FILENAME = "sct2_sRefset_OWLExpression";
	private static final String LANGUAGE_REFSET_FILENAME = "der2_cRefset_Language";
	private static final String ASSOCIATION_REFSET_FILENAME = "der2_cRefset_Association";
	private static final String ATTRIBUTE_VALUE_REFSET_FILENAME = "der2_cRefset_AttributeValue";

	public static final String SCTID_SE_REFSETID = "734138000";
	public static final String SCTID_SP_REFSETID = "734139008";

	private String previousReleasePath;
	private String currentReleasePath;
	private Map<String, Map<TotalsIndex, Integer>> fileTotals = new TreeMap<>();
	private Map<String, Integer> headersDiffCounts = new HashMap<>();
	private Set<String> leftModules = new HashSet<>();
	private Set<String> rightModules = new HashSet<>();
	private static final String LEFT_MODULES_FILENAME = "left_modules.txt";
	private static final String RIGHT_MODULES_FILENAME = "right_modules.txt";
	private static final String HEADERS_DIFF_FILENAME = "diff__headers.txt";
	private static final String FILES_DIFF_FILENAME = "diff__files.txt";


	private final String[] tabNames = new String[] {
			"File Comparison"
	};
	
	private final String[] columnHeadings = new String[] {
			"Filename, New, Changed, Inactivated, Reactivated, Moved Module, Promoted, New Inactive, Changed Inactive, Deleted, Header, Total"
	};

	private final String[] columnWidths = new String[] {
			"460, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85, 85"
	};

	enum TotalsIndex {
		NEW,
		CHANGED,
		INACTIVATED,
		REACTIVATED,
		MOVED_MODULE,
		PROMOTED,
		NEW_INACTIVE,
		CHANGED_INACTIVE,
		DELETED,
		HEADER,
		TOTAL
	}

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();

		// SE Extension
		params.put(THIS_RELEASE, "se/snomed_ct_se_releases/2024-11-27T17:45:35/output-files/SnomedCT_ManagedServiceSE_PRODUCTION_SE1000052_20241130T120000Z.zip");
		params.put(THIS_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20240801T120000Z.zip");
		params.put(PREV_RELEASE, "se/snomed_ct_se_releases/2024-05-15T17:18:28/output-files/xSnomedCT_ManagedServiceSE_PREPRODUCTION_SE1000052_20240531T120000Z.zip");
		params.put(PREV_DEPENDENCY, "SnomedCT_InternationalRF2_PRODUCTION_20240201T120000Z.zip");
		params.put(MODULES, "45991000052106");

		TermServerScript.run(PackageComparisonReport.class, args, params);
	}

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		previousReleasePath = run.getParamValue(PREV_RELEASE);
		currentReleasePath = run.getParamValue(THIS_RELEASE);

		if (previousReleasePath.equals(currentReleasePath)) {
			throw new TermServerScriptException("Previous and current release paths must be different");
		}

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

	@Override
	public String[] getColumnWidths() {
		return concatArrays(super.getColumnWidths(), columnWidths);
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
			LOGGER.info("Setting previous dependency archive to {}", prevDependency);
			setDependencyArchive(prevDependency);
 		}
		super.loadProjectSnapshot(fsnOnly);
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		thisDependency = getJobRun().getParamValue(THIS_DEPENDENCY);
		if (!StringUtils.isEmpty(thisDependency)) {
			LOGGER.info("Setting dependency archive to {}", thisDependency);
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

		String previousReleaseName = previousRelease.getName();
		String currentReleaseName = currentRelease.getName();

		if (previousReleaseName.equals(currentReleaseName)) {
			previousReleaseName = "Previous_" + previousReleaseName;
			currentReleaseName = "Current_" + currentReleaseName;
		}

		String uploadFolder = String.join("_",
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmm")),
				previousReleaseName,
				currentReleaseName);

		String[] scriptArguments = new String[]{
				doubleQuote(previousReleaseName),
				doubleQuote(previousRelease.getPath()),
				doubleQuote(currentReleaseName),
				doubleQuote(currentRelease.getPath()),
				doubleQuote(uploadFolder)
		};

		String command = "scripts/" + SCRIPT_NAME + SPACE + String.join(SPACE, scriptArguments);

		LOGGER.info("Starting script execution. Command: {}", command);
		executeScript(command);
		LOGGER.info("Diff files for '{}' and '{}' are uploaded to 's3://snomed-compares/{}'", previousRelease.getPath(), currentRelease.getPath(), uploadFolder);

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
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		} catch (IOException e) {
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
			// Load list of package modules
			loadModulesFile(diffDir, LEFT_MODULES_FILENAME, leftModules);
			loadModulesFile(diffDir, RIGHT_MODULES_FILENAME, rightModules);

			// Load header diff counts
			loadTabDelimitedFile(diffDir, HEADERS_DIFF_FILENAME, headersDiffCounts);

			// Process files diff file
			processFilesList(diffDir);

			report(FILE_COMPARISON_TAB, "Files changed:");

			// Process content diff files (snapshot files only)
			try (Stream<Path> stream = Files.list(diffDir)) {
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
		} catch (IOException | TermServerRuntimeException e) {
			LOGGER.error("Error processing diff files in {}", uploadFolder);
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
			LOGGER.error("Failed to load file '{}'", filename, e);
		}
	}

	private void loadModulesFile(Path outputDir, String filename, Set<String> modules) {
		try (BufferedReader br = new BufferedReader(new FileReader(outputDir + File.separator + filename, StandardCharsets.UTF_8))) {
			String line;
			while ((line = br.readLine()) != null) {
				modules.add(line);
			}
		} catch (Exception e) {
			LOGGER.error("Failed to load file '{}'", filename, e);
		}
	}

	private boolean isExtension() {
		return !leftModules.contains(SCTID_CORE_MODULE) && !rightModules.contains(SCTID_CORE_MODULE);
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
		boolean isAssociationRefset = false;

		Map<String, Map<TotalsIndex, Integer>> fileTotalsWithoutComparison = new TreeMap<>();

		for (Map.Entry<String, Map<TotalsIndex, Integer>> entry : fileTotals.entrySet()) {
			String filename = entry.getKey();
			Map<TotalsIndex, Integer> fileTotalsEntry = entry.getValue();

			// Output descriptions totals
			if (isDescription && !filename.contains(DESCRIPTION_FILENAME)) {
				report("Total Descriptions:", descriptionTotals, "Descriptions", totals[TAB_DESCS]);
				isDescription = false;
			}

			// Output text definitions totals
			if (isTextDefinition && !filename.contains(TEXT_DEFINITION_FILENAME)) {
				report("Total Text Definitions:", textDefinitionTotals, "Text Definitions", totals[TAB_TEXT_DEFN]);
				isTextDefinition = false;
			}

			// Output language refsets totals
			if (isLanguageRefset && !filename.contains(LANGUAGE_REFSET_FILENAME)) {
				report("Total Language Refsets:", languageRefsetTotals, "Language Refsets", totals[TAB_LANG]);
				isLanguageRefset = false;
			}

			// Output associations totals
			if (isAssociationRefset && !filename.contains(ASSOCIATION_REFSET_FILENAME)) {
				report("Associations", sumOfTabs(TAB_HIST, TAB_DESC_HIST));
				isAssociationRefset = false;
			}

			if (filename.contains(DESCRIPTION_FILENAME)) {
				isDescription = true;
				report(filename, fileTotalsEntry, null, null);
				for (TotalsIndex index : TotalsIndex.values()) {
					descriptionTotals.compute(index, (k, v) -> v + fileTotalsEntry.get(index));
				}
			} else if (filename.contains(TEXT_DEFINITION_FILENAME)) {
				isTextDefinition = true;
				report(filename, fileTotalsEntry, null, null);
				for (TotalsIndex index : TotalsIndex.values()) {
					textDefinitionTotals.compute(index, (k, v) -> v + fileTotalsEntry.get(index));
				}
			} else if (filename.contains(LANGUAGE_REFSET_FILENAME)) {
				isLanguageRefset = true;
				report(filename, fileTotalsEntry, null, null);
				for (TotalsIndex index : TotalsIndex.values()) {
					languageRefsetTotals.compute(index, (k, v) -> v + fileTotalsEntry.get(index));
				}
			} else if (filename.contains(ASSOCIATION_REFSET_FILENAME)) {
				isAssociationRefset = true;
				report(filename, fileTotalsEntry, null, null);
			} else if (filename.contains(CONCEPT_FILENAME)) {
				report(filename, fileTotalsEntry, "Concepts", totals[TAB_CONCEPTS]);
			} else if (filename.contains(RELATIONSHIP_FILENAME)) {
				report(filename, fileTotalsEntry, "Relationships", totals[TAB_RELS]);
			} else if (filename.contains(RELATIONSHIP_CONCRETE_VALUES_FILENAME)) {
				report(filename, fileTotalsEntry, "Concrete Values", totals[TAB_CD]);
			} else if (filename.contains(OWL_EXPRESSION_FILENAME)) {
				report(filename, fileTotalsEntry, "Axioms", totals[TAB_AXIOMS]);
			} else if (filename.contains(ATTRIBUTE_VALUE_REFSET_FILENAME)) {
				report(filename, fileTotalsEntry, "Attribute Values", sumOfTabs(TAB_INACT_IND, TAB_DESC_CNC, TAB_DESC_INACT));
			} else {
				fileTotalsWithoutComparison.put(filename, fileTotalsEntry);
			}
		}

		for (Map.Entry<String, Map<TotalsIndex, Integer>> entry : fileTotalsWithoutComparison.entrySet()) {
			report(entry.getKey(), entry.getValue(), null, null);
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

	private void report(String filename, Map<TotalsIndex, Integer> fileTotals, String scsName, int[] scsTotals) throws TermServerScriptException {
		report(filename, fileTotals);
		report(scsName, scsTotals);
	}

	private void report(String filename, Map<TotalsIndex, Integer> fileTotals) throws TermServerScriptException {
		Object[] fileDetails = new Object[TotalsIndex.values().length + 1];

		fileDetails[0] = filename;
		for (TotalsIndex index : TotalsIndex.values()) {
			fileDetails[index.ordinal() + 1] = fileTotals.get(index);
		}

		// For extensions MOVED_MODULE and PROMOTED are not applicable
		if (isExtension()) {
			fileDetails[TotalsIndex.MOVED_MODULE.ordinal() + 1] = fileDetails[TotalsIndex.PROMOTED.ordinal() + 1] = "N/A";
		}

		report(FILE_COMPARISON_TAB, fileDetails);
	}

	private void report(String scsName, int[] scsTotals) throws TermServerScriptException {
		if (scsTotals != null) {
			Object[] scsDetails = new Object[TotalsIndex.values().length + 1];

			scsDetails[0] = "-- Summary Component Stats: " + scsName + " --";
			scsDetails[TotalsIndex.NEW.ordinal() + 1] = scsTotals[IDX_NEW];
			scsDetails[TotalsIndex.CHANGED.ordinal() + 1] = scsTotals[IDX_CHANGED];
			scsDetails[TotalsIndex.INACTIVATED.ordinal() + 1] = scsTotals[IDX_INACTIVATED];
			scsDetails[TotalsIndex.REACTIVATED.ordinal() + 1] = scsTotals[IDX_REACTIVATED];
			scsDetails[TotalsIndex.MOVED_MODULE.ordinal() + 1] = scsTotals[IDX_MOVED_MODULE];
			scsDetails[TotalsIndex.PROMOTED.ordinal() + 1] = scsTotals[IDX_PROMOTED];
			scsDetails[TotalsIndex.NEW_INACTIVE.ordinal() + 1] = scsTotals[IDX_NEW_INACTIVE];
			scsDetails[TotalsIndex.CHANGED_INACTIVE.ordinal() + 1] = scsTotals[IDX_CHANGED_INACTIVE];
			scsDetails[TotalsIndex.DELETED.ordinal() + 1] = 0; // missing in SCS
			scsDetails[TotalsIndex.HEADER.ordinal() + 1] = 0; // missing in SCS

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

	private void processFilesList(Path path) throws TermServerScriptException {
		Set<String> created = new TreeSet<>();
		Set<String> deleted = new TreeSet<>();

		try (BufferedReader br = new BufferedReader(new FileReader(path + File.separator + FILES_DIFF_FILENAME, StandardCharsets.UTF_8))) {
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

			report(FILE_COMPARISON_TAB, "Files created: " + created.size() + ". See file totals in the relevant sections below");
			for (String file : created) {
				report(FILE_COMPARISON_TAB, file);
			}
			report(FILE_COMPARISON_TAB, "");

			report(FILE_COMPARISON_TAB, "Files deleted: " + deleted.size() + ". See file totals in the relevant sections below");
			for (String file : deleted) {
				report(FILE_COMPARISON_TAB, file);
			}
			report(FILE_COMPARISON_TAB, "");

		} catch (IOException | IndexOutOfBoundsException e) {
			LOGGER.error("Error processing list of files: {}", FILES_DIFF_FILENAME);
			throw new TermServerRuntimeException(e.getMessage());
		}
	}

	private void process(Path path, String filename) {
		if (filename.contains(ASSOCIATION_REFSET_FILENAME)) {
			processAssociationFile(path, filename, null);
			processAssociationFile(path, filename, Set.of(SCTID_SE_REFSETID, SCTID_SP_REFSETID));
		} else if (filename.contains(CONCEPT_FILENAME)) {
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
				String effectiveTime = data[IDX_EFFECTIVETIME];

				switch (ch) {
					// For the same component, the "deleted" indicator always comes before the "created" indicator in the file
					case LINE_DELETED_INDICATOR:
						if (inScope(moduleId)) {
							// Previous release entry
							deleted.put(key, data);
						}
						break;
					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.containsKey(key)) {
							String[] oldValue = deleted.remove(key);

							if (inScope(moduleId)) {
								ValuePair valuePair = new ValuePair(oldValue, data);

								if (valuePair.isInactivated()) {
									count(totals, TotalsIndex.INACTIVATED);
								} else if (valuePair.isReactivated()) {
									count(totals, TotalsIndex.REACTIVATED);
								} else if (isChanged(effectiveTime)) {
									if (valuePair.isActive()) {
										count(totals, TotalsIndex.CHANGED);
									} else if (valuePair.isInactive()) {
										count(totals, TotalsIndex.CHANGED_INACTIVE);
									}
								}
							}
						} else if (inScope(moduleId)) {
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
			LOGGER.error("Error processing file: {}", filename);
			throw new TermServerRuntimeException(e.getMessage());
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
				String effectiveTime = data[ASSOC_IDX_EFFECTIVETIME];

				if (refsetIds != null && !refsetIds.contains(data[ASSOC_IDX_REFSETID])) {
					continue;
				}

				switch (ch) {
					// For the same component, the "deleted" indicator always comes before the "created" indicator in the file
					case LINE_DELETED_INDICATOR:
						// Previous release entry
						if (inScope(moduleId)) {
							deleted.put(key, data);
						}
						break;
					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.containsKey(key)) {
							String[] oldValue = deleted.remove(key);

							if (inScope(moduleId)) {
								ValuePair valuePair = new ValuePair(oldValue, data);

								if (valuePair.isInactivated()) {
									count(totals, TotalsIndex.INACTIVATED);
								} else if (valuePair.isReactivated()) {
									count(totals, TotalsIndex.REACTIVATED);
								} else if (isChanged(effectiveTime)) {
									if (valuePair.isActive()) {
										count(totals, TotalsIndex.CHANGED);
									} else if (valuePair.isInactive()) {
										count(totals, TotalsIndex.CHANGED_INACTIVE);
									}
								}
							}
						} else if (inScope(moduleId)) {
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
			LOGGER.error("Error processing association file: {}", filename);
			throw new TermServerRuntimeException(e.getMessage());
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
				String effectiveTime = data[CON_IDX_EFFECTIVETIME];

				switch (ch) {
					// For the same component, the "deleted" indicator always comes before the "created" indicator in the file
					case LINE_DELETED_INDICATOR:
						// Previous release entry
						deleted.put(key, data);
						break;
					case LINE_CREATED_INDICATOR:
						// Current release entry
						if (deleted.containsKey(key)) {
							String[] oldValue = deleted.remove(key);

							if (inScope(data[IDX_MODULEID])) {
								ValuePair valuePair = new ValuePair(oldValue, data);

								if (valuePair.isMovedModule()) {
									// Moved into this module
									count(totals, TotalsIndex.MOVED_MODULE);
								} else {
									if (valuePair.isInactivated()) {
										count(totals, TotalsIndex.INACTIVATED);
									} else if (valuePair.isReactivated()) {
										count(totals, TotalsIndex.REACTIVATED);
									} else if (isChanged(effectiveTime)) {
										if (valuePair.isActive()) {
											count(totals, TotalsIndex.CHANGED);
										} else if (valuePair.isInactive()) {
											count(totals, TotalsIndex.CHANGED_INACTIVE);
										}
									}
								}
							} else if (moduleFilter.contains(oldValue[IDX_MODULEID])) {
								count(totals, TotalsIndex.PROMOTED);
							}
						} else if (inScope(data[IDX_MODULEID])) {
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
			LOGGER.error("Error processing concept file: {}", filename);
			throw new TermServerRuntimeException(e.getMessage());
		}
	}

	private void count(Map<TotalsIndex, Integer> totals, TotalsIndex index) {
		totals.compute(index, (k, v) -> v + 1);
	}

	private boolean isChanged(String effectiveTime) {
		return thisEffectiveTime.equals(previousEffectiveTime) || effectiveTime.compareTo(previousEffectiveTime) > 0;
	}

	private boolean inScope(String moduleId) {
		return moduleFilter == null || moduleFilter.contains(moduleId);
	}

	class ValuePair {
		// Previous value in the pair contains a line marked "<"
		// Current value contains a matching line marked ">"

		String[] previousValue;
		String[] currentValue;

		ValuePair(String[] previousValue, String[] currentValue) {
			this.previousValue = previousValue;
			this.currentValue = currentValue;
		}

		boolean isChanged() {
			return StringUtils.isEmpty(currentValue[IDX_EFFECTIVETIME]) || currentValue[IDX_EFFECTIVETIME].compareTo(previousValue[IDX_EFFECTIVETIME]) > 0;
		}

		boolean isMovedModule() {
			return !currentValue[IDX_MODULEID].equals(previousValue[IDX_MODULEID]);
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
