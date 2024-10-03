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
import org.ihtsdo.termserver.scripting.reports.TermServerReport;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistoricDataUser extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistoricDataUser.class);

	private static final Pattern DATE_EXTRACTOR_PATTERN;
	static {
		DATE_EXTRACTOR_PATTERN = Pattern.compile("\\d{8}(?=T)");
	}

	public static final String PREV_RELEASE = "Previous Release";
	public static final String THIS_RELEASE = "This Release";

	public static final String PREV_DEPENDENCY = "Previous Dependency";
	public static final String THIS_DEPENDENCY = "This Dependency";

	public static final Concept UNKNOWN_CONCEPT = new Concept("54690008", "Unknown");

	public static final boolean debugToFile = false;
	protected List<String> moduleFilter;

	protected String prevRelease;
	protected String prevDependency;
	protected String thisDependency;

	protected String previousEffectiveTime;
	boolean isPublishedReleaseAnalysis = false;

	protected String projectKey;
	protected String origProject;
	protected Map<String, Datum> prevData;
	//2D data structure Concepts, Descriptions, Relationships, Axioms, LangRefset, Inactivation Indicators, Historical Associations
	protected Map<Concept, int[][]> summaryDataMap;
	protected Map<String, int[]> refsetDataMap;
	protected String thisEffectiveTime;
	protected int topLevelHierarchyCount = 0;
	protected String complexName;
	protected boolean previousTransitiveClosureNeeded = true;

	public void doDefaultProjectSnapshotLoad(boolean fsnOnly) throws TermServerScriptException {
		super.loadProjectSnapshot(fsnOnly);
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {

		projectKey = getProject().getKey();
		LOGGER.info("Historic data being imported, wiping Graph Loader for safety.");
		getArchiveManager(true).reset(false);

		boolean compareTwoSnapshots = recoverReleaseConfiguration();

		//If we have a task defined, we need to shift that out of the way while we're loading the previous package
		String task = getJobRun().getTask();
		getJobRun().setTask(null);
		try {
			ArchiveManager mgr = getArchiveManager(true);
			mgr.setLoadEditionArchive(true);
			mgr.loadSnapshot(fsnOnly);

			previousEffectiveTime = gl.getCurrentEffectiveTime();
			LOGGER.info("EffectiveTime of previous release detected to be: " + previousEffectiveTime);

			HistoricStatsGenerator statsGenerator = new HistoricStatsGenerator(this);
			statsGenerator.setModuleFilter(moduleFilter);
			statsGenerator.runJob();
			mgr.reset();
			getJobRun().setTask(task);
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException("Historic Data Generation failed due to " + e.getMessage(), e);
		}
		loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}

	private boolean recoverReleaseConfiguration() throws TermServerScriptException {
		boolean compareTwoSnapshots = false;
		if (!StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE)) &&
				StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
			throw new TermServerScriptException("This release must be specified if previous release is.");
		}

		if (!StringUtils.isEmpty(getJobRun().getParamValue(THIS_RELEASE))) {
			compareTwoSnapshots = true;
			projectKey = getJobRun().getParamValue(THIS_RELEASE);
			//Have we got what looks like a zip file but someone left the .zip off?
			if (projectKey.contains("T120000") && !projectKey.endsWith(".zip")) {
				throw new TermServerScriptException("Suspect release '" + projectKey + "' should end with .zip");
			}
			//If this release has been specified, the previous must also be, explicitly
			if (StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE))) {
				throw new TermServerScriptException("Previous release must be specified if current release is.");
			}

			if (projectKey.endsWith(".zip")) {
				isPublishedReleaseAnalysis = true;
			}
		}

		prevRelease = getJobRun().getParamValue(PREV_RELEASE);
		if (StringUtils.isEmpty(prevRelease)) {
			prevRelease = getProject().getMetadata().getPreviousPackage();
		}

		if (isPublishedReleaseAnalysis) {
			ensurePrevIsEarlierThanThis(projectKey, prevRelease);
		}

		getProject().setKey(prevRelease);
		return compareTwoSnapshots;
	}

	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		LOGGER.info ("Previous Data Generated, now loading 'current' position");
		ArchiveManager mgr = getArchiveManager();
		if (compareTwoSnapshots) {
			mgr.setLoadEditionArchive(true);
			if (!StringUtils.isEmpty(thisDependency)) {
				mgr.setLoadDependencyPlusExtensionArchive(true);
			}
			setProject(new Project(projectKey));
			mgr.loadSnapshot(false);
			thisEffectiveTime = gl.getCurrentEffectiveTime();
			LOGGER.info ("Detected this effective time as " + thisEffectiveTime);
		} else {
			//We cannot just add in the project delta because it might be that - for an extension
			//the international edition has also been updated.   So recreate the whole snapshot
			mgr.setPopulatePreviousTransativeClosure(previousTransitiveClosureNeeded );
			mgr.setLoadEditionArchive(false);
			getProject().setKey(projectKey);
			mgr.loadSnapshot(fsnOnly);
		}
	}

	protected Map<String, Datum> loadData(String release) throws TermServerScriptException {
		return loadData(release, false);
	}

	protected Map<String, Datum> loadData(String release, boolean minimalSet) throws TermServerScriptException {
		File dataFile = null;
		prevData = new HashMap<>();
		//Might need all the memory we can get at this point
		System.gc();
		try {
			dataFile = new File("historic-data/" + release + ".tsv");
			if (!dataFile.exists() || !dataFile.canRead()) {
				throw new TermServerScriptException("Unable to load historic data: " + dataFile);
			}
			LOGGER.info ("Loading " + dataFile);
			BufferedReader br = new BufferedReader(new FileReader(dataFile));
			int lineNumber = 0;
			String line = "";
			try {
				while ((line = br.readLine()) != null) {
					lineNumber++;
					Datum datum = fromLine(line, minimalSet);
					if (StringUtils.isEmpty(datum.hierarchy)){
						datum.hierarchy = UNKNOWN_CONCEPT.getConceptId();
					}
					prevData.put(Long.toString(datum.conceptId), datum);
				}
			} catch (Exception e) {
				String err = e.getClass().getSimpleName();
				throw new TermServerScriptException(err + " at line " + lineNumber + " columnCount: " + line.split(TAB).length + " content: " + line);
			} finally {
				br.close();
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to load " + dataFile, e);
		}
		return prevData;
	}

	protected class Datum {
		public long conceptId;
		public String fsn;
		public boolean isActive;
		public boolean isSD;
		public String hierarchy;
		public boolean isIP;
		public boolean hasSdDescendant;
		public boolean hasSdAncestor;
		public int hashCode;
		public String moduleId;
		public List<String> relIds;
		public List<String> descIds;
		public List<String> axiomIds;
		public List<String> langRefsetIds;
		public List<String> inactivationIds;
		public List<String> histAssocIds;
		public List<String> relIdsInact;
		public List<String> descIdsInact;
		public List<String> axiomIdsInact;
		public List<String> langRefsetIdsInact;
		public List<String> inactivationIdsInact;
		public List<String> histAssocIdsInact;
		public boolean hasAttributes;
		public List<String> descHistAssocIds;
		public List<String> descHistAssocIdsInact;
		public List<String> descInactivationIds;
		public List<String> descInactivationIdsInact;
		public List<String> histAssocTargets;

		@Override
		public int hashCode () {
			return hashCode;
		}

		@Override
		public boolean equals (Object o) {
			if (o instanceof Datum) {
				return this.conceptId == ((Datum)o).conceptId;
			}
			return false;
		}
	}

	Datum fromLine (String line, boolean minimalSet) {
		int idx = 0;
		Datum datum = new Datum();
		String[] lineItems = line.split(TAB, -1);
		datum.conceptId = Long.parseLong(lineItems[idx]);
		datum.hashCode = Long.hashCode(datum.conceptId);
		datum.fsn = lineItems[++idx];
		datum.isActive = lineItems[++idx].equals("Y");
		if (!minimalSet) {
			datum.isSD = lineItems[++idx].equals("SD");
			datum.hierarchy = lineItems[++idx];
			datum.isIP = lineItems[++idx].equals("Y");
			datum.hasSdAncestor = lineItems[++idx].equals("Y");
			datum.hasSdDescendant = lineItems[++idx].equals("Y");
			datum.relIds = Arrays.asList(lineItems[++idx].split(","));
			datum.relIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.descIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.axiomIds = Arrays.asList(lineItems[++idx].split(","));
			datum.axiomIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.langRefsetIds = Arrays.asList(lineItems[++idx].split(","));
			datum.langRefsetIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.inactivationIds = Arrays.asList(lineItems[++idx].split(","));
			datum.inactivationIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocIds = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.moduleId = lineItems[++idx];
			datum.hasAttributes = lineItems[++idx].equals("Y");
			datum.descHistAssocIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descHistAssocIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.descInactivationIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descInactivationIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocTargets = Arrays.asList(lineItems[++idx].split(","));
		} else {
			idx++;
			datum.hierarchy = lineItems[++idx];
			idx += 21;
			datum.histAssocTargets = Arrays.asList(lineItems[++idx].split(","));
			if (datum.histAssocTargets.size() == 1 && datum.histAssocTargets.get(0).equals("")) {
				datum.histAssocTargets = null;  //Save the memory!
			}
		}
		return datum;
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
		if (moduleFilter != null && moduleFilter.size() > 0) {
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


	protected void ensurePrevIsEarlierThanThis(String now, String earlier) {
		try {
			if (getET(earlier).compareTo(getET(now)) > 0) {
				throw new IllegalArgumentException("Previous " + earlier + " is later than this release " + now);
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