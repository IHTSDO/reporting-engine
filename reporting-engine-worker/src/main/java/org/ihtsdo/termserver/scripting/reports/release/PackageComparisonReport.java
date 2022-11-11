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
		/*params.put(PREVIOUS_RELEASE_NAME, "EE_2021-11-30");
		params.put(PREVIOUS_RELEASE_PATH, "SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20211130T120000Z.zip");
		params.put(CURRENT_RELEASE_NAME, "EE_2022-05-30");
		params.put(CURRENT_RELEASE_PATH, "SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20220530T120000Z.zip");*/

		// Ireland release
		params.put(PREVIOUS_RELEASE_NAME, "IE_2022-04-21");
		params.put(PREVIOUS_RELEASE_PATH, "prod/builds/ie/snomed_ct_ireland_extension_releases/2022-10-19T16:18:12/output-files/SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20221021T120000Z.zip");
		params.put(CURRENT_RELEASE_NAME, "IE_2022-10-21");
		params.put(CURRENT_RELEASE_PATH, "prod/builds/ie/snomed_ct_ireland_extension_releases/2022-04-19T14:02:59/output-files/SnomedCT_ManagedServiceIE_PRODUCTION_IE1000220_20220421T120000Z.zip");

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

		previousReleaseName = run.getParamValue(PREVIOUS_RELEASE_NAME);
		previousReleasePath = run.getParamValue(PREVIOUS_RELEASE_PATH);
		currentReleaseName = run.getParamValue(CURRENT_RELEASE_NAME);
		currentReleasePath = run.getParamValue(CURRENT_RELEASE_PATH);

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
				.add(PREVIOUS_RELEASE_NAME).withType(JobParameter.Type.STRING)
				.add(PREVIOUS_RELEASE_PATH).withType(JobParameter.Type.STRING)
				.add(CURRENT_RELEASE_NAME).withType(JobParameter.Type.STRING)
				.add(CURRENT_RELEASE_PATH).withType(JobParameter.Type.STRING)
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
			report(PRIMARY_REPORT, previousReleasePath, currentReleasePath, "s3://snomed-compares/" + uploadFolder);

		} catch (IOException | InterruptedException | TermServerScriptException e) {
			error("Script execution failed", e);
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
}