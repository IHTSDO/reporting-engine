package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.HistoricData;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoricDataUser extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoricDataUser.class);

	private static final Pattern DATE_EXTRACTOR_PATTERN;
	static {
		DATE_EXTRACTOR_PATTERN = Pattern.compile("\\d{8}(?=T)");
	}

	protected static final String RELEASE = "release";
	protected static final String DEPENDENCY = "dependency";

	public static final String PREV_RELEASE = "Previous Release";
	public static final String THIS_RELEASE = "This Release";

	public static final String PREV_DEPENDENCY = "Previous Dependency";
	public static final String THIS_DEPENDENCY = "This Dependency";

	public static final Concept UNKNOWN_CONCEPT = new Concept("54690008", "Unknown");

	public static final boolean DEBUG_TO_FILE = false;

	protected String thisRelease;
	protected String prevRelease;

	protected String thisDependency;
	protected String prevDependency;

	protected String thisEffectiveTime;
	protected String previousEffectiveTime;

	boolean isPublishedReleaseAnalysis = false;

	protected String projectKey;
	protected String origProject;
	protected Map<String, HistoricData> prevData;

	protected boolean previousTransitiveClosureNeeded = true;

	public void doDefaultProjectSnapshotLoad(boolean fsnOnly) throws TermServerScriptException {
		super.loadProjectSnapshot(fsnOnly);
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {

		projectKey = getProject().getKey();
		LOGGER.info("Historic data being imported, wiping Graph Loader for safety.");
		getArchiveManager().reset();

		boolean compareTwoSnapshots = recoverReleaseConfiguration();

		//If we have a task defined, we need to shift that out of the way while we're loading the previous package
		String task = getJobRun().getTask();
		getJobRun().setTask(null);
		try {
			ArchiveManager mgr = getArchiveManager();
			mgr.setLoadEditionArchive(true);
			mgr.loadSnapshot(fsnOnly);

			previousEffectiveTime = gl.getCurrentEffectiveTime();
			LOGGER.info("EffectiveTime of previous release detected to be: {}", previousEffectiveTime);

			HistoricStatsGenerator statsGenerator = new HistoricStatsGenerator(this);
			statsGenerator.setModuleFilter(moduleFilter);
			statsGenerator.runJob();
			mgr.reset();
			getJobRun().setTask(task);
		} catch (Exception e) {
			throw new TermServerScriptException("Historic Data Generation (from previous release) failed due to " + e.getMessage(), e);
		}
		loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}

	private boolean recoverReleaseConfiguration() throws TermServerScriptException {

		boolean compareTwoSnapshots = checkReleasePresence();

		if (!StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
			projectKey = getJobRun().getParamValue(THIS_RELEASE);

			if (projectKey.endsWith(".zip")) {
				isPublishedReleaseAnalysis = true;
			}
		}

		prevRelease = getJobRun().getParamValue(PREV_RELEASE);
		if (StringUtils.isEmpty(prevRelease)) {
			prevRelease = getProject().getMetadata().getPreviousPackage();
		}

		if (isPublishedReleaseAnalysis) {
			ensurePrevIsEarlierThanThis(projectKey, prevRelease, RELEASE, RELEASE);
			if (thisDependency != null) {
				ensurePrevIsEarlierThanThis(projectKey, thisDependency, RELEASE, DEPENDENCY);
			}
		}

		if (prevRelease != null && prevDependency != null) {
			ensurePrevIsEarlierThanThis(prevRelease, prevDependency, RELEASE, DEPENDENCY);
			if (thisDependency != null) {
				ensurePrevIsEarlierThanThis(thisDependency, prevDependency, DEPENDENCY, DEPENDENCY);
			}
		}

		getProject().setKey(prevRelease);
		return compareTwoSnapshots;
	}

	private boolean checkReleasePresence() throws TermServerScriptException {
		if (StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE)) && StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
			return false;
		} else {
			if (!StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE)) && StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
				throw new TermServerScriptException("This release must be specified if previous release is.");
			}

			if (!StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE)) && StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE))) {
				throw new TermServerScriptException("Previous release must be specified if current release is.");
			}

			checkReleaseFileName(getJobRun().getParamValue(PREV_RELEASE));
			checkReleaseFileName(getJobRun().getParamValue(THIS_RELEASE));

			return true;
		}
	}

	private void checkReleaseFileName(String releaseFileName) throws TermServerScriptException {
		//Have we got what looks like a zip file but someone left the .zip off?
		if (releaseFileName.contains("T120000") && !releaseFileName.endsWith(".zip")) {
			throw new TermServerScriptException("Suspect release '" + releaseFileName + "' should end with .zip");
		}
	}

	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		LOGGER.info("Previous Data Generated, now loading 'current' position");
		ArchiveManager mgr = getArchiveManager();
		if (compareTwoSnapshots) {
			mgr.setLoadEditionArchive(true);
			if (!StringUtils.isEmpty(thisDependency)) {
				ensurePrevIsEarlierThanThis(projectKey, thisDependency, RELEASE, DEPENDENCY);
				ensurePrevIsEarlierThanThis(thisDependency, prevDependency, DEPENDENCY, DEPENDENCY);
				mgr.setLoadDependencyPlusExtensionArchive(true);
			}
			setProject(new Project(projectKey));
			mgr.loadSnapshot(false);
			thisEffectiveTime = gl.getCurrentEffectiveTime();
			LOGGER.info("Detected this effective time as {}", thisEffectiveTime);
		} else {
			//We cannot just add in the project delta because it might be that - for an extension
			//the international edition has also been updated.   So recreate the whole snapshot
			mgr.setPopulatePreviousTransitiveClosure(previousTransitiveClosureNeeded );
			mgr.setLoadEditionArchive(false);
			getProject().setKey(projectKey);
			mgr.loadSnapshot(fsnOnly);
		}
	}

	protected Map<String, HistoricData> loadData(String release) throws TermServerScriptException {
		return loadData(release, false);
	}

	protected Map<String, HistoricData> loadData(String release, boolean minimalSet) throws TermServerScriptException {
		File dataFile = null;
		prevData = new HashMap<>();
		try {
			dataFile = new File("historic-data/" + release + ".tsv");
			if (!dataFile.exists() || !dataFile.canRead()) {
				throw new TermServerScriptException("Unable to load historic data: " + dataFile);
			}
			loadDataFile(dataFile, minimalSet);
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load " + dataFile, e);
		}
		return prevData;
	}

	private void loadDataFile(File dataFile, boolean minimalSet) throws TermServerScriptException {
		int lineNumber = 0;
		String line = "";
		try (BufferedReader br = new BufferedReader(new FileReader(dataFile))) {
		    while ((line = br.readLine()) != null) {
		        lineNumber++;
		        HistoricData datum = HistoricData.fromLine(line, minimalSet);
		        if (StringUtils.isEmpty(datum.getHierarchy())){
		            datum.setHierarchy(UNKNOWN_CONCEPT.getConceptId());
		        }
		        prevData.put(Long.toString(datum.getConceptId()), datum);
		    }
		} catch (Exception e) {
		    String err = e.getClass().getSimpleName();
		    throw new TermServerScriptException(err + " at line " + lineNumber + " columnCount: " + line.split(TAB).length + " content: " + line);
		}
	}

	@Override
	public String getReportName() {
		String reportName = super.getReportName();
		if (jobRun != null && jobRun.getParamValue(PREV_RELEASE) != null) {
			reportName += "_" + jobRun.getParamValue(PREV_RELEASE);
		}
		if (jobRun != null && jobRun.getParamValue(MODULES) != null) {
			reportName += "_" + jobRun.getParamValue(MODULES);
		}
		return reportName;
	}

	@Override
	protected boolean inScope(Component c) {
		//Are we filtering for unpromoted changes only?
		if (unpromotedChangesOnly && !unpromotedChangesHelper.hasUnpromotedChange(c)) {
			return false;
		}
		//If we've specified some modules explicitly, then allow those to
		//take precedence
		if (moduleFilter != null && !moduleFilter.isEmpty()) {
			return moduleFilter.contains(c.getModuleId());
		}
		//RP-349 Allow MS customers to run reports against MAIN.
		//In this case all concepts are "in scope" to allow MS customers to see
		//what changes to international concepts might affect them
		if (project.getKey().equals("MAIN")) {
			return true;
		}
		//Do we have a default module id ie for a managed service project?
		if (project.getMetadata() != null && project.getMetadata().getDefaultModuleId() != null) {
			return c.getModuleId().equals(project.getMetadata().getDefaultModuleId());
		}
		return true;
	}


	protected void ensurePrevIsEarlierThanThis(String now, String earlier, String item1, String item2) throws TermServerScriptException {
		try {
			if (getET(earlier).compareTo(getET(now)) > 0) {
				throw new TermServerScriptException("Previous " + item2 + " " + earlier + " is later than " + item1 + " " + now);
			}
		} catch (IllegalArgumentException e) {
			LOGGER.warn("Unable to determine dates for correct ordering of releases between {} and (earlier) {}.  Proceeding anyway.", now, earlier);
		}
	}

	private String getET(String str) {
		Matcher matcher = DATE_EXTRACTOR_PATTERN.matcher(str);
		if (matcher.find()) {
			return matcher.group();
		}
		throw new IllegalArgumentException("Unable to extract date from " + str);
	}
}