package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.dao.ReportConfiguration.ReportFormatType;
import org.ihtsdo.termserver.scripting.dao.ReportConfiguration.ReportOutputType;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

/**
 * RP-288 
 * */
public class SummaryComponentStats extends TermServerReport implements ReportClass {
	
	public static final String PREV_RELEASE = "Previous Release";
	public static final String THIS_RELEASE = "This Release";

	public static final Concept UNKNOWN_CONCEPT = new Concept("54690008", "Unknown");

	public static final boolean debugToFile = false;

	String prevRelease;
	String projectKey;
	Map<String, Datum> prevData;
	//2D data structure Concepts, Descriptions, Relationships, Axioms, LangRefset, Inactivation Indicators, Historical Associations
	Map<Concept, int[][]> summaryDataMap;
	String thisEffectiveTime;
	int topLevelHierarchyCount = 0;
	String complexName;
	static final int TAB_CONCEPTS = 0, TAB_DESCS = 1, TAB_RELS = 2, TAB_AXIOMS = 3,
			TAB_LANG = 4, TAB_INACT_IND = 5, TAB_HIST = 6, TAB_TEXT_DEFN = 7;
	static final int COMPONENT_COUNT = 8;
	static final int DATA_WIDTH = 21;  //New, Changed, Inactivated, Reactivated, New with New Concept, extra1, extra2, Total, next 11 fields are the inactivation reason, concept affected, reactivated
	static final int IDX_NEW = 0, IDX_CHANGED = 1, IDX_INACT = 2, IDX_REACTIVATED = 3, IDX_NEW_NEW = 4, IDX_NEW_P = 5, IDX_NEW_SD = 6,
			IDX_TOTAL = 7, IDX_INACT_AMBIGUOUS = 8,  IDX_INACT_MOVED_ELSEWHERE = 9, IDX_INACT_CONCEPT_NON_CURRENT = 10,
			IDX_INACT_DUPLICATE = 11, IDX_INACT_ERRONEOUS = 12, IDX_INACT_INAPPROPRIATE = 13, IDX_INACT_LIMITED = 14,
			IDX_INACT_OUTDATED = 15, IDX_INACT_PENDING_MOVE = 16, IDX_INACT_NON_CONFORMANCE = 17,
			IDX_INACT_NOT_EQUIVALENT = 18, IDX_CONCEPTS_AFFECTED = 19, IDX_TOTAL_ACTIVE = 20;
	static Map<Integer, List<Integer>> sheetFieldsByIndex = getSheetFieldsMap();
	static List<DescriptionType> TEXT_DEFN;
	static List<DescriptionType> NOT_TEXT_DEFN;
	List<Concept> topLevelHierarchies;
	File debugFile;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(PREV_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20200309T120000Z.zip");
		params.put(THIS_RELEASE, "xSnomedCT_InternationalRF2_ALPHA_20200731T120000Z.zip");
		params.put(REPORT_OUTPUT_TYPES, ReportOutputType.GOOGLE.name());
		params.put(REPORT_FORMAT_TYPE, ReportFormatType.CSV.name());
		//params.put(REPORT_OUTPUT_TYPES, ReportOutputType.S3.name());
		//params.put(REPORT_FORMAT_TYPE, ReportFormatType.JSON.name());
		TermServerReport.run(SummaryComponentStats.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(PREV_RELEASE).withType(JobParameter.Type.STRING)
				.add(THIS_RELEASE).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportOutputType.GOOGLE.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportFormatType.CSV.name())
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Summary Component Stats")
				.withDescription("This report lists component changes per major hierarchy.   You can either specify two releases to compare as archives stored in S3 " + 
				"(eg SnomedCT_InternationalRF2_PRODUCTION_20200131T120000Z.zip) or leave them blank to compare the current delta to the previous release as specified " +
				"by that branch.")
				.withParameters(params)
				.withTag(INT)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.build();
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		prevData = new HashMap<>();
		summaryDataMap = new HashMap<>();
		manyTabWideOutput = true;
		
		TEXT_DEFN = new ArrayList<>();
		TEXT_DEFN.add(DescriptionType.TEXT_DEFINITION);
		
		NOT_TEXT_DEFN = new ArrayList<>();
		NOT_TEXT_DEFN.add(DescriptionType.FSN);
		NOT_TEXT_DEFN.add(DescriptionType.SYNONYM);
		
		super.init(run);
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException, InterruptedException, IOException {
		boolean compareTwoSnapshots = false; 
		projectKey = getProject().getKey();
		prevRelease = getJobRun().getParamValue(PREV_RELEASE);
		if (prevRelease == null) {
			prevRelease = getProject().getMetadata().getPreviousPackage();
		}
		
		if (getJobRun().getParamValue(THIS_RELEASE) != null) {
			compareTwoSnapshots = true;
			projectKey = getJobRun().getParamValue(THIS_RELEASE);
			//If this release has been specified, the previous must also be, explicitly
			if (StringUtils.isEmpty(getJobRun().getParamValue(PREV_RELEASE))) {
				throw new TermServerScriptException("Previous release must be specified if current release is");
			}
		}
		getProject().setKey(prevRelease);
		getArchiveManager().setLoadEditionArchive(true);
		getArchiveManager().loadProjectSnapshot(fsnOnly);
		HistoricStatsGenerator statsGenerator = new HistoricStatsGenerator(this);
		statsGenerator.runJob();
		
		if (compareTwoSnapshots) {
			getArchiveManager().setLoadEditionArchive(true);
			setProject(new Project(projectKey));
			getArchiveManager().loadProjectSnapshot(false);
			//Descriptions for the root concept are a quick way to find the effeciveTime
			thisEffectiveTime = gl.getCurrentEffectiveTime();
			info ("Detected this effective time as " + thisEffectiveTime);
		} else {
			//Now we can carry on an add the delta on top
			getArchiveManager().setLoadEditionArchive(false);
			getProject().setKey(projectKey);
			File delta = getArchiveManager().generateDelta(project);
			loadArchive(delta, false, "Delta", false);
		}
	};
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"Sctid, Hierarchy, SemTag, New, Changed DefnStatus, Inactivated, Reactivated, New with New Concept, New SD, New P, Total Active, Total",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New with New Concept, Total Active, Total, Concepts Affected",
												"Sctid, Hierarchy, SemTag, New Inferred Rels, Changed Inferred Rels, Inactivated Inferred Rels, Reactivated, New with New Concept, Total Active, Total, Concepts Affected",
												"Sctid, Hierarchy, SemTag, New Axioms, Changed Axioms, Inactivated Axioms, Reactivated, New with New Concept, Total Active, Total, Concepts Affected",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New with New Concept, Concepts Affected, Total Active",
												"Sctid, Hierarchy, SemTag, Inactivations New / Reactivated, Changed, Inactivations Inactivated, Reactivated, New with New Concept, Ambiguous, Moved Elsewhere, Concept Non Current, Duplicate, Erroneous, Inappropriate, Limited, Outdated, Pending Move, Non Conformance, Not Equivalent, Concepts Affected, Total Active",
												"Sctid, Hierarchy, SemTag, Assoc New, Changed, Assoc Inactivated, Reactivated, New with New Concept, Concepts Affected, Total Active",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New with New Concept, Total, Concepts Affected, Total Active"
												};
		String[] tabNames = new String[] {	"Concepts",
											"Descriptions",
											"Relationships",
											"Axioms",
											"LangRefSet",
											"Inactivations",
											"Hist Assoc",
											"Text Defn"};
		topLevelHierarchies = new ArrayList<Concept>(ROOT_CONCEPT.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
		topLevelHierarchies.add(UNKNOWN_CONCEPT); // Add this a we might not always be able to get the top level hierarchy
		topLevelHierarchies.sort(Comparator.comparing(Concept::getFsn));
		super.postInit(tabNames, columnHeadings, false);
	}
	public void runJob() throws TermServerScriptException {
		info ("Loading Previous Data");
		loadData(prevRelease);
		info ("Analysing Data");
		analyzeConcepts();
		info ("Outputting Results");
		outputResults();
	}

	@Override
	public String getReportComplexName() {
		if (projectKey != null && prevRelease != null) {
			complexName = projectKey + "---" + prevRelease;
			complexName = complexName.replaceAll("\\.zip", ""); // remove the zip extension
		} else {
			complexName = super.getReportComplexName();
		}
		return complexName;
	}

	private void analyzeConcepts() throws TermServerScriptException {
		TransitiveClosure tc = gl.generateTransativeClosure();
		info ("Analysing concepts");
		Concept topLevel;
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {	
				topLevel = getHierarchy(tc, c);
			} else {
				//Was it active in the previous release?
				if (prevData.containsKey(c.getConceptId())) {
					topLevel = gl.getConcept(prevData.get(c.getConceptId()).hierarchy);
				} else {
					//If not, it's been inactivate for a while, nothing more to say
					warn("Unexpected data state, failure to retrieve top level: " + c);
					break;
				}
			}
			//Have we seen this hierarchy before?
			int[][] summaryData = summaryDataMap.get(topLevel);
			if (summaryData == null) {
				summaryData = new int[COMPONENT_COUNT][DATA_WIDTH];
				summaryDataMap.put(topLevel, summaryData);
			}
			
			Datum datum = prevData.get(c.getConceptId());
			boolean isNewConcept = datum==null;
			Boolean wasSD = datum==null?null:datum.isSD;
			Boolean wasActive = datum==null?null:datum.isActive;
			analyzeConcept(c, wasSD, wasActive, summaryData[TAB_CONCEPTS]);
			//Component changes
			analyzeComponents(isNewConcept, (datum==null?null:datum.descIds), (datum==null?null:datum.descIdsInact), summaryData[TAB_DESCS], c.getDescriptions(ActiveState.BOTH, NOT_TEXT_DEFN));
			analyzeComponents(isNewConcept, (datum==null?null:datum.descIds), (datum==null?null:datum.descIdsInact), summaryData[TAB_TEXT_DEFN], c.getDescriptions(ActiveState.BOTH, TEXT_DEFN));
			analyzeComponents(isNewConcept, (datum==null?null:datum.relIds), (datum==null?null:datum.relIdsInact), summaryData[TAB_RELS], c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH));
			analyzeComponents(isNewConcept, (datum==null?null:datum.axiomIds), (datum==null?null:datum.axiomIdsInact), summaryData[TAB_AXIOMS], c.getAxiomEntries());
			analyzeComponents(isNewConcept, (datum==null?null:datum.inactivationIds), (datum==null?null:datum.inactivationIdsInact), summaryData[TAB_INACT_IND], c.getInactivationIndicatorEntries());
			analyzeComponents(isNewConcept, (datum==null?null:datum.histAssocIds), (datum==null?null:datum.histAssocIdsInact), summaryData[TAB_HIST], c.getAssociations(ActiveState.BOTH, true));
			List<LangRefsetEntry> langRefsetEntries = c.getDescriptions().stream()
					.flatMap(d -> d.getLangRefsetEntries().stream())
					.collect(Collectors.toList());
			analyzeComponents(isNewConcept, (datum==null?null:datum.langRefsetIds), (datum==null?null:datum.langRefsetIdsInact), summaryData[TAB_LANG], langRefsetEntries);
		}
	}
	
	
	private void analyzeConcept(Concept c, Boolean wasSD, Boolean wasActive, int[] counts) throws TermServerScriptException {
		//If we have no previous data, then the concept is new
		boolean conceptIsNew = (wasSD == null);
		if (c.isActive()) {
			counts[IDX_TOTAL_ACTIVE]++;
			boolean isSD = c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED);
			if (conceptIsNew) {
				if (isSD) {
					counts[IDX_NEW_SD]++;
				} else {
					counts[IDX_NEW_P]++;
				}
				counts[IDX_NEW]++;
				counts[IDX_NEW_NEW]++;
			} else { 
				//Were we inactive in the last release?  Reactivated if so
				if (!wasActive) {
					counts[IDX_REACTIVATED]++;
				} else if (isSD != wasSD) {
					//Active now and not new, and previously new, we must have changed Definition Status
					counts[IDX_CHANGED]++;
				}
			}
		} else if (prevData.containsKey(c.getConceptId()) && prevData.get(c.getConceptId()).isActive) {
			//If we had it last time active, then it's been inactivated in this release
			counts[IDX_INACT]++;
		}
		counts[IDX_TOTAL]++;
	}

	private void analyzeComponents(boolean isNewConcept, Collection<String> ids, Collection<String> idsInactive, int[] counts, Collection<? extends Component> components) throws TermServerScriptException {
		//If we have no previous data, then the concept is new
		boolean conceptIsNew = (ids == null && idsInactive == null);
		boolean conceptAffected = false;
		for (Component component : components) {
			//Was the description present in the previous data?
			boolean previouslyExistedActive = false;
			boolean previouslyExistedInactive = false;
			if (!conceptIsNew) {
				previouslyExistedActive = ids.contains(component.getId());
				previouslyExistedInactive = idsInactive.contains(component.getId());
			}
			if (component.isActive()) {
				counts[IDX_TOTAL_ACTIVE]++;
				if (previouslyExistedInactive) {
					counts[IDX_REACTIVATED]++;
					debugToFile(component, "Reactivated");
					conceptAffected = true;
				} else if(!previouslyExistedActive) {
					counts[IDX_NEW]++;
					debugToFile(component, "New");
					conceptAffected = true;
					if (isNewConcept) {
						//This component is new because it was created as part of a new concept
						//so it's not been 'added' as such.  Well, we might want to count additions
						//to existing concepts separately.
						counts[IDX_NEW_NEW]++;
						debugToFile(component, "NewNew");
					}
					// find out the reason
					if (component instanceof InactivationIndicatorEntry) {
						InactivationIndicatorEntry inactivationIndicatorEntry = (InactivationIndicatorEntry) component;
						incrementInactivationReason (counts, inactivationIndicatorEntry.getInactivationReasonId());
					}
				} else if (StringUtils.isEmpty(component.getEffectiveTime()) || component.getEffectiveTime().equals(thisEffectiveTime)) {
					//Did it change in this release?
					counts[IDX_CHANGED]++;
					debugToFile(component, "Changed");
					conceptAffected = true;
				}
			} else if (previouslyExistedActive) {
				//Existed previously active and is now inactive, mark as inactivated
				counts[IDX_INACT]++;
				debugToFile(component, "Inactivated");
				conceptAffected = true;
			}
			counts[IDX_TOTAL]++;
			//debugToFile(component, "Total");
		}
		if (conceptAffected) {
			counts[IDX_CONCEPTS_AFFECTED]++;
		}
	}

	private void debugToFile(Component c, String statType) throws TermServerScriptException {

		// Only debug if we enable it (for testing really).
		if (!debugToFile) {
			return;
		}

		if (!(c instanceof Description)) {
			return;
		} else {
			Description d =  (Description)c;
			if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				return;
			}
		}
		
		try {
			//If this is our first write, do not append.
			Boolean append = true;
			if (debugFile == null) {
				debugFile = new File("summary_component_debug.txt");
				append = false;
			}
			String line = c.getId() + TAB + statType + "\n";
			FileUtils.writeStringToFile(debugFile, line, append);
		} catch (IOException e) {
			throw new TermServerScriptException(e);
		}
	}

	private void incrementInactivationReason(int[] counts, String reasonId) {
		switch (reasonId) {
			case SCTID_INACT_AMBIGUOUS:
				counts[IDX_INACT_AMBIGUOUS]++;
				break;
			case SCTID_INACT_MOVED_ELSEWHERE:
				counts[IDX_INACT_MOVED_ELSEWHERE]++;
				break;
			case SCTID_INACT_CONCEPT_NON_CURRENT:
				counts[IDX_INACT_CONCEPT_NON_CURRENT]++;
				break;
			case SCTID_INACT_DUPLICATE:
				counts[IDX_INACT_DUPLICATE]++;
				break;
			case SCTID_INACT_ERRONEOUS:
				counts[IDX_INACT_ERRONEOUS]++;
				break;
			case SCTID_INACT_INAPPROPRIATE:
				counts[IDX_INACT_INAPPROPRIATE]++;
				break;
			case SCTID_INACT_LIMITED:
				counts[IDX_INACT_LIMITED]++;
				break;
			case SCTID_INACT_OUTDATED:
				counts[IDX_INACT_OUTDATED]++;
				break;
			case SCTID_INACT_PENDING_MOVE:
				counts[IDX_INACT_PENDING_MOVE]++;
				break;
			case SCTID_INACT_NON_CONFORMANCE:
				counts[IDX_INACT_NON_CONFORMANCE]++;
				break;
			case SCTID_INACT_NOT_EQUIVALENT:
				counts[IDX_INACT_NOT_EQUIVALENT]++;
				break;
		}
	}

	private void outputResults() throws TermServerScriptException {
		Concept totalConcept = new Concept("","Total");
		int[][] totals = new int[COMPONENT_COUNT][DATA_WIDTH];
		for (Concept hierarchy : topLevelHierarchies) {
			int[][] summaryData = summaryDataMap.get(hierarchy);
			for (int idxTab = 0; idxTab < COMPONENT_COUNT; idxTab++) {
				report(idxTab, hierarchy, summaryData[idxTab]);
				for (int idxMovement = 0; idxMovement < DATA_WIDTH; idxMovement++) {
					totals[idxTab][idxMovement] += summaryData[idxTab][idxMovement];
				}
			}
		}
		
		for (int idxTab = 0; idxTab < COMPONENT_COUNT; idxTab++) {
			report (idxTab, totalConcept, totals[idxTab]);
		}
	}
	
	protected void report (int idxTab, Concept c, int[] data) throws TermServerScriptException {
		super.report(idxTab, c, getReportData(idxTab, data));
		countIssue(c);
	}

	private static Map<Integer, List<Integer>> getSheetFieldsMap() {
		// set up the report sheets and the fields they contain
		final Map<Integer, List<Integer>> sheetFieldsByIndex = new HashMap<>();

		sheetFieldsByIndex.put(TAB_CONCEPTS, new LinkedList(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_NEW, IDX_NEW_P, IDX_NEW_SD, IDX_TOTAL_ACTIVE, IDX_TOTAL)));

		Arrays.asList(TAB_DESCS, TAB_RELS, TAB_AXIOMS, TAB_TEXT_DEFN).stream().forEach(index -> {
			sheetFieldsByIndex.put(index, new LinkedList(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_NEW, IDX_TOTAL_ACTIVE, IDX_TOTAL, IDX_CONCEPTS_AFFECTED)));
		});

		sheetFieldsByIndex.put(TAB_INACT_IND, new LinkedList(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_NEW, IDX_INACT_AMBIGUOUS,
				IDX_INACT_MOVED_ELSEWHERE, IDX_INACT_CONCEPT_NON_CURRENT, IDX_INACT_DUPLICATE, IDX_INACT_ERRONEOUS,
				IDX_INACT_INAPPROPRIATE, IDX_INACT_LIMITED, IDX_INACT_OUTDATED, IDX_INACT_PENDING_MOVE, IDX_INACT_NON_CONFORMANCE,
				IDX_INACT_NOT_EQUIVALENT, IDX_CONCEPTS_AFFECTED, IDX_TOTAL_ACTIVE)));

		Arrays.asList(TAB_LANG, TAB_HIST).stream().forEach(index -> {
			sheetFieldsByIndex.put(index, new LinkedList((Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_NEW, IDX_CONCEPTS_AFFECTED, IDX_TOTAL_ACTIVE))));
		});

		return sheetFieldsByIndex;
	}

	private int[] getReportData(int idxTab, int[] allData) {
		// we are collected different fields of data so now work out with ones we need
		// You can change the order here
		List<Integer> dataFieldsRequired = sheetFieldsByIndex.get(idxTab);

		// now get the data for the sheet that we need (compact)
		int[] data = new int[dataFieldsRequired.size()];
		int currentIndex = 0;
		for (int dataFieldRequiredIndex: dataFieldsRequired) {
			data[currentIndex] = allData[dataFieldRequiredIndex];
			currentIndex++;
		}
		return data;
	}

	private void loadData(String release) throws TermServerScriptException {
		File dataFile = null;
		try {
			dataFile = new File("historic-data/" + release + ".tsv");
			if (!dataFile.exists() || !dataFile.canRead()) {
				throw new TermServerScriptException("Unable to load historic data: " + dataFile);
			}
			info ("Loading " + dataFile);
			BufferedReader br = new BufferedReader(new FileReader(dataFile));
			int lineNumber = 0;
			String line = "";
			try {
				while ((line = br.readLine()) != null) {
					lineNumber++;
					Datum datum = fromLine(line);
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
	}
	
	private Concept getHierarchy(TransitiveClosure tc, Concept c) throws TermServerScriptException {
		if (c.equals(ROOT_CONCEPT)) {
			return c;
		}

		if (!c.isActive() || c.getDepth() == NOT_SET) {
			return null;  //Hopefully the previous release will know
		} 
		
		if (c.getDepth() == 1) {
			return c;
		} 
		
		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDepth() == 1) {
				return a;
			}
		}
		throw new TermServerScriptException("Unable to determine hierarchy for " + c);
	}
	
	protected class Datum {
		long conceptId;
		boolean isActive;
		boolean isSD;
		String hierarchy;
		boolean isIP;
		boolean hasSdDescendant;
		boolean hasSdAncestor;
		int hashCode;
		List<String> relIds;
		List<String> descIds;
		List<String> axiomIds;
		List<String> langRefsetIds;
		List<String> inactivationIds;
		List<String> histAssocIds;
		List<String> relIdsInact;
		List<String> descIdsInact;
		List<String> axiomIdsInact;
		List<String> langRefsetIdsInact;
		List<String> inactivationIdsInact;
		List<String> histAssocIdsInact;
		
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
	
	Datum fromLine (String line) {
		int idx = 0;
		Datum datum = new Datum();
		String[] lineItems = line.split(TAB, -1);
		datum.conceptId = Long.parseLong(lineItems[idx]);
		datum.isActive = lineItems[++idx].equals("Y");
		datum.isSD = lineItems[++idx].equals("SD");
		datum.hierarchy = lineItems[++idx];
		datum.isIP = lineItems[++idx].equals("Y");
		datum.hasSdAncestor = lineItems[++idx].equals("Y");
		datum.hasSdDescendant = lineItems[++idx].equals("Y");
		datum.hashCode = Long.hashCode(datum.conceptId);
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
		return datum;
	}
	
}
