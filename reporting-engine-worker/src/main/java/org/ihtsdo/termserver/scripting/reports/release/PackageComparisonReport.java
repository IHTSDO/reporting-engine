package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
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
public class PackageComparisonReport extends TermServerReport implements ReportClass {

	// Name of the previous release - free text specified by the user
	public static final String PREVIOUS_RELEASE_NAME = "Previous Release Name";

	// Path to the previous release zip archive excluding S3 bucket name
	public static final String PREVIOUS_RELEASE_PATH = "Previous Release Path";

	// Name of the current release - free text specified by the user
	public static final String CURRENT_RELEASE_NAME = "Current Release Name";

	// Path to the current release zip archive excluding S3 bucket name
	public static final String CURRENT_RELEASE_PATH = "Current Release Path";

	// Name of the starting script
	private static final String SCRIPT_NAME = "run_compare.sh";

	private String previousReleaseName;
	private String previousReleasePath;
	private String currentReleaseName;
	private String currentReleasePath;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();

		// Estonia release
		params.put(PREVIOUS_RELEASE_NAME, "EE20211130");
		params.put(PREVIOUS_RELEASE_PATH, "prod/builds/ee/estonia_extension_releases/2022-06-28T13:54:09/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20211130T120000Z.zip");
		params.put(CURRENT_RELEASE_NAME, "EE20221130");
		params.put(CURRENT_RELEASE_PATH, "prod/builds/ee/estonia_extension_releases/2022-11-15T15:50:50/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20221130T120000Z.zip");

		// Ireland release
		//params.put(PREVIOUS_RELEASE_NAME, "IE_2022-04-21");
		//params.put(PREVIOUS_RELEASE_PATH, "prod/builds/ie/snomed_ct_ireland_extension_releases/2022-04-19T14:02:59/output-files/SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20220421T120000Z.zip");
		//params.put(CURRENT_RELEASE_NAME, "IE_2022-10-21");
		//params.put(CURRENT_RELEASE_PATH, "prod/builds/ie/snomed_ct_ireland_extension_releases/2022-10-19T16:18:12/output-files/SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20221021T120000Z.zip");

		TermServerReport.run(PackageComparisonReport.class, args, params);
	}

	@Override
	protected void preInit() {
		// For running the report locally
		runHeadless(5);
	}

	@Override
	protected void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; // Release Stats

		previousReleaseName = run.getParamValue(PREVIOUS_RELEASE_NAME);
		previousReleasePath = run.getParamValue(PREVIOUS_RELEASE_PATH);
		currentReleaseName = run.getParamValue(CURRENT_RELEASE_NAME);
		currentReleasePath = run.getParamValue(CURRENT_RELEASE_PATH);

		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Filename, Added, Deleted, Updated, Inactivated, All", "Script Output"
		};
		String[] tabNames = new String[] {
				"Comparison Summary", "Log"
		};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(PREVIOUS_RELEASE_NAME).withType(JobParameter.Type.STRING).withMandatory()
				.withDescription("Previous release short name (free text)")
				.add(PREVIOUS_RELEASE_PATH).withType(JobParameter.Type.STRING).withMandatory()
				.withDescription("Path to previous release .zip archive in S3 excluding bucket name")
				.add(CURRENT_RELEASE_NAME).withType(JobParameter.Type.STRING).withMandatory()
				.withDescription("Current release short name (free text)")
				.add(CURRENT_RELEASE_PATH).withType(JobParameter.Type.STRING).withMandatory()
				.withDescription("Path to current release .zip archive in S3 excluding bucket name")
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportOutputType.GOOGLE.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportConfiguration.ReportFormatType.CSV.name())
				.build();
				
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Package Comparison Report")
				.withDescription("This report compares two packages (zip archives) using Unix scripts with output captured into usual Google Sheets")
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withExpectedDuration(40)
				.build();
	}

	// This report does not need to hold a snapshot in memory,
	// so we override the default behaviour by having an empty method here.
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) {
	}
	
	public void runJob() throws TermServerScriptException {
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			throw new TermServerScriptException("Windows operating system detected. This report can only run on Linux or MacOS.");
		}

		String uploadFolder = String.join("_",
				previousReleaseName, extractDate(previousReleasePath),
				currentReleaseName,	extractDate(currentReleasePath),
				LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmm")));

		String[] scriptArguments = new String[]{
				doubleQuote(previousReleaseName),
				doubleQuote(previousReleasePath),
				doubleQuote(currentReleaseName),
				doubleQuote(currentReleasePath),
				doubleQuote(uploadFolder)
		};

		String command = "./" + SCRIPT_NAME + " " + String.join(" ", scriptArguments);

		ProcessBuilder builder = new ProcessBuilder();
		builder.redirectErrorStream(true);
		builder.directory(new File("scripts"));
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
				output.add("Diff files for '" + previousReleasePath + "' and '" + currentReleasePath + "' are uploaded to s3://snomed-compares/" + uploadFolder);
			} else {
				// Process timed out
				throw new TermServerScriptException("Script execution timed out");
			}

			// Process resulting diff files
			String path = "scripts/" + uploadFolder + "/target/c";

			try (Stream<Path> stream = Files.list(Paths.get(path))) {
				stream.filter(file -> !Files.isDirectory(file))
						.map(Path::getFileName)
						.map(Path::toString)
						.filter(filename -> filename.matches("diff_.*(sct2|der2)_.*") && !filename.matches("diff_.*_no_(first|1_7)_col.txt"))
						.forEach(filename -> processFile(path, filename));
			}

		} catch (IOException | InterruptedException | TermServerScriptException e) {
			error("Report execution failed", e);
		}

		for (String line: output) {
			report(SECONDARY_REPORT, line);
		}
	}

	private String doubleQuote(String arg) {
		return "\"" + arg + "\"";
	}

	private String extractDate(String path) {
		Pattern pattern = Pattern.compile(".*_(\\d{8}).*");

		Matcher matcher = pattern.matcher(path);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return path;
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
			report(PRIMARY_REPORT, filename, created.size(), deleted.size(), updated.size(), inactivated.size(), total);
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