package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

/**
 * 
 */
public class PackageComparisonReport extends TermServerReport implements ReportClass {

	public static final String PREVIOUS_RELEASE = "Previous Release";
	public static final String PREVIOUS_RELEASE_ZIP = "Previous Release Package";
	public static final String CURRENT_RELEASE = "Current Release";
	public static final String CURRENT_RELEASE_ZIP = "Current Release Package";
	public static final String PUBLISHED_FOLDER = "Published Folder";

	private final String scriptName = "run_compare.sh";
	private String[] scriptArguments;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(PREVIOUS_RELEASE, "EE Release 2021-11-30");
		params.put(PREVIOUS_RELEASE_ZIP, "SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20211130T120000Z.zip");
		params.put(CURRENT_RELEASE, "EE Release 2022-05-30");
		params.put(CURRENT_RELEASE_ZIP, "SnomedCT_ManagedServiceEE_PRODUCTION_EE1000181_20220530T120000Z.zip");
		params.put(PUBLISHED_FOLDER, "ee");
		TermServerReport.run(PackageComparisonReport.class, args, params);
	}

	@Override
	protected void preInit() throws TermServerScriptException {
		runHeadless(5);
	}

	@Override
	protected void init (JobRun run) throws TermServerScriptException {
		scriptArguments = new String[]{
				"\"" + run.getParamValue(PREVIOUS_RELEASE) + "\"",
				"\"" + run.getParamValue(PREVIOUS_RELEASE_ZIP) + "\"",
				"\"" + run.getParamValue(CURRENT_RELEASE) + "\"",
				"\"" + run.getParamValue(CURRENT_RELEASE_ZIP) + "\"",
				"\"" + run.getParamValue(PUBLISHED_FOLDER) + "\""
		};
		//getArchiveManager().setPopulateReleasedFlag(true);
		//ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		suppressOutput = true;
		//super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"RefsetId, Active, isNew, Mapping, UUID",
				"RefsetId, Active, isNew, Concept, UUID"};
		String[] tabNames = new String[] {
				"Release A Summary",
				"Release B Summary"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(PREVIOUS_RELEASE).withType(JobParameter.Type.STRING)
				.add(PREVIOUS_RELEASE_ZIP).withType(JobParameter.Type.STRING)
				.add(CURRENT_RELEASE).withType(JobParameter.Type.STRING)
				.add(CURRENT_RELEASE_ZIP).withType(JobParameter.Type.STRING)
				.add(PUBLISHED_FOLDER).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.build();
				
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Package Comparison Report")
				.withDescription("This report compares two packages (zip archives) using Unix scripts with output captured into usual Google Sheets")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.withExpectedDuration(40)
				.build();
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException, InterruptedException, IOException {
		//getArchiveManager(true).loadSnapshot(fsnOnly);
		//Reset the report name to null here as it will have been set by the Snapshot Generator
		//setReportName(null);
	}
	
	public void runJob() throws TermServerScriptException {
		//Kick off scripts here
		boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");

		ProcessBuilder builder = new ProcessBuilder();
		if (isWindows) {
			builder.command("cmd.exe", "/c", "dir");
		} else {
			String command = "./" + scriptName + " " + String.join(" ", scriptArguments);
			builder.command("sh", "-c", command);
		}
		builder.directory(new File("reporting-engine-worker/scripts"));//System.getProperty("user.home")));

		try {
			Process process = builder.start();

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
				reader.lines().forEach(line -> System.out.println(line));
			}

			int exitVal = process.waitFor();
			System.out.println("Script execution finished with exit code: " + exitVal);

		} catch (IOException | InterruptedException e) {
			e.printStackTrace();
		}
	}
}