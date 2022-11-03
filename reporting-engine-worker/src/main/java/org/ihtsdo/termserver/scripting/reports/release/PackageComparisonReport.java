package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * This report executes shell scripts that download and compare the content of the two .zip release packages
 * which are specified as input report parameters.
 * The result of the comparison is uploaded to S3 bucket snomed-compares.
 */
public class PackageComparisonReport extends TermServerReport implements ReportClass {

	// Name of the previous release - free text specified by the user
	public static final String PREVIOUS_RELEASE = "Previous Release";

	// Previous release zip archive name
	public static final String PREVIOUS_RELEASE_PACKAGE = "Previous Release Package";

	// Name of the current release - free text specified by the user
	public static final String CURRENT_RELEASE = "Current Release";

	// Current release zip archive name
	public static final String CURRENT_RELEASE_PACKAGE = "Current Release Package";

	// Published folder corresponding with thw release centre, e.g. ie, us, etc
	public static final String PUBLISHED_FOLDER = "Published Folder";

	// Name of the starting script
	private static final String SCRIPT_NAME = "run_compare.sh";

	private String previousRelease;
	private String previousReleasePackage;
	private String currentRelease;
	private String currentReleasePackage;
	private String publishedFolder;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();

		// Estonia release
		params.put(PREVIOUS_RELEASE, "EE_2021-11-30");
		params.put(PREVIOUS_RELEASE_PACKAGE, "SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20211130T120000Z.zip");
		params.put(CURRENT_RELEASE, "EE_2022-05-30");
		params.put(CURRENT_RELEASE_PACKAGE, "SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20220530T120000Z.zip");
		params.put(PUBLISHED_FOLDER, "ee");

		// Ireland release
		/*
		params.put(PREVIOUS_RELEASE, "IE_2022-04-21");
		params.put(PREVIOUS_RELEASE_PACKAGE, "SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20220421T120000Z.zip");
		params.put(CURRENT_RELEASE, "IE_2022-10-21");
		params.put(CURRENT_RELEASE_PACKAGE, "SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20221021T120000Z.zip");
		params.put(PUBLISHED_FOLDER, "ie");
		*/

		TermServerReport.run(PackageComparisonReport.class, args, params);
	}

	@Override
	protected void preInit() throws TermServerScriptException {
		// For running the report locally
		runHeadless(5);
	}

	@Override
	protected void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; // Release Stats

		previousRelease = run.getParamValue(PREVIOUS_RELEASE);
		previousReleasePackage = run.getParamValue(PREVIOUS_RELEASE_PACKAGE);
		currentRelease = run.getParamValue(CURRENT_RELEASE);
		currentReleasePackage = run.getParamValue(CURRENT_RELEASE_PACKAGE);
		publishedFolder = run.getParamValue(PUBLISHED_FOLDER);

		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Previous Release, Current Release, Comparison Results"
		};
		String[] tabNames = new String[] {
				"Comparison Summary"
		};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(PREVIOUS_RELEASE).withType(JobParameter.Type.STRING)
				.add(PREVIOUS_RELEASE_PACKAGE).withType(JobParameter.Type.STRING)
				.add(CURRENT_RELEASE).withType(JobParameter.Type.STRING)
				.add(CURRENT_RELEASE_PACKAGE).withType(JobParameter.Type.STRING)
				.add(PUBLISHED_FOLDER).withType(JobParameter.Type.STRING)
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

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException, InterruptedException, IOException {
	}
	
	public void runJob() throws TermServerScriptException {
		if (System.getProperty("os.name").toLowerCase().startsWith("windows")) {
			throw new TermServerScriptException("Windows operating system detected. This report can only run on Linux or MacOS.");
		}

		String uploadFolder = String.join("_",
						previousRelease,
						extractDate(previousReleasePackage),
						currentRelease,
						extractDate(currentReleasePackage),
						LocalDateTime.now().format(DateTimeFormatter.ofPattern("uuuu-MM-dd-HHmm")));

		String[] scriptArguments = new String[]{
				doubleQuote(previousRelease),
				doubleQuote(previousReleasePackage),
				doubleQuote(currentRelease),
				doubleQuote(currentReleasePackage),
				doubleQuote(publishedFolder),
				doubleQuote(uploadFolder)
		};

		String command = "./" + SCRIPT_NAME + " " + String.join(" ", scriptArguments);

		ProcessBuilder builder = new ProcessBuilder();
		builder.redirectErrorStream(true);
		builder.directory(new File("scripts"));
		builder.command("sh", "-c", command);

		try {
			Process process = builder.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				reader.lines().forEach(line -> info(line));
			}

			if (process.waitFor(30, TimeUnit.MINUTES)) {
				// Process exited
				int exitValue = process.exitValue();

				if (exitValue != 0) {
					throw new TermServerScriptException("Script execution failed with exit code: " + exitValue);
				}
				info("Script execution finished with exit code: " + exitValue);
			} else {
				// Process timed out
				throw new TermServerScriptException("Script execution timed out");
			}

			// Write results to Google Sheets
			report(PRIMARY_REPORT, previousReleasePackage, currentReleasePackage, "s3://snomed-compares/" + uploadFolder);

		} catch (IOException | InterruptedException | TermServerScriptException e) {
			error("Script execution failed", e);
		}
	}

	private String doubleQuote(String arg) {
		return "\"" + arg + "\"";
	}

	private String extractDate(String packageName) {
		Pattern pattern = Pattern.compile(".*_(\\d{8}).*");

		Matcher matcher = pattern.matcher(packageName);
		if (matcher.find()) {
			return matcher.group(1);
		}
		return packageName;
	}
}