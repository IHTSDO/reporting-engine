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
import org.ihtsdo.termserver.scripting.dao.ArchiveDataLoader;
import org.ihtsdo.termserver.scripting.dao.BuildLoaderConfig;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * This report executes shell scripts that download and compare the content of the two .zip release packages,
 * stored in S3 bucket snomed-releases, which are specified as input report parameters - a full path to archives
 * except the bucket name is expected.
 * The result of the comparison is uploaded to S3 bucket snomed-compares.
 */
public class PackageComparisonReport extends HistoricDataUser implements ReportClass { // extends TermServerReport
	@Autowired
	private BuildLoaderConfig buildLoaderConfig;

	// Name of the starting script
	private static final String SCRIPT_NAME = "run-compare-packages.sh";

	private String previousReleasePath;
	private String currentReleasePath;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();

		// Estonia release
		params.put(PREV_RELEASE, "ee/estonia_extension_releases/2022-06-28T13:54:09/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20211130T120000Z.zip");
		params.put(THIS_RELEASE, "ee/estonia_extension_releases/2022-11-15T15:50:50/output-files/SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20221130T120000Z.zip");

		// Ireland release
		//params.put(PREV_RELEASE, "ie/snomed_ct_ireland_extension_releases/2022-04-19T14:02:59/output-files/SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20220421T120000Z.zip");
		//params.put(THIS_RELEASE, "ie/snomed_ct_ireland_extension_releases/2022-10-19T16:18:12/output-files/SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20221021T120000Z.zip");

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
		previousReleasePath = run.getParamValue(PREV_RELEASE);
		currentReleasePath = run.getParamValue(THIS_RELEASE);
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
				.add(THIS_RELEASE).withType(JobParameter.Type.STRING)
				.add(PREV_RELEASE).withType(JobParameter.Type.STRING)
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

	// This report does not need to hold a snapshot in memory,
	// so we override the default behaviour by having an empty method here.
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		if (StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE)) || StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
			throw new TermServerScriptException("Previous and current releases must be specified.");
		}
		ArchiveDataLoader archiveDataLoader = ArchiveDataLoader.create(new BuildLoaderConfig());
		archiveDataLoader.download(new File(previousReleasePath), new File("builds"));
		archiveDataLoader.download(new File(currentReleasePath), new File("builds"));
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
			report(SECONDARY_REPORT, line);
		}
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