package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.snomed.otf.script.dao.ReportConfiguration.ReportFormatType;
import org.snomed.otf.script.dao.ReportConfiguration.ReportOutputType;

/**
 * RP-288 
 * */
public class SummaryComponentStats extends HistoricDataUser implements ReportClass {
	
	static final int TAB_CONCEPTS = 0, TAB_DESCS = 1, TAB_RELS = 2, TAB_CD = 3, TAB_AXIOMS = 4,
			TAB_LANG = 5, TAB_INACT_IND = 6, TAB_HIST = 7, TAB_TEXT_DEFN = 8, TAB_QI = 9,
			TAB_DESC_HIST = 10, TAB_DESC_CNC = 11, TAB_DESC_INACT = 12, TAB_REFSET = 13;  //Ensure refset tab is the last one as it's written at the end.
	static final int MAX_REPORT_TABS = 14;
	static final int DATA_WIDTH = 28;  //New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Moved Module, Changed Inactive, extra1, extra2, Total, next 11 fields are the inactivation reason, concept affected, reactivated
	static final int IDX_NEW = 0, IDX_CHANGED = 1, IDX_INACT = 2, IDX_REACTIVATED = 3, IDX_NEW_INACTIVE = 4, IDX_NEW_NEW = 5, 
			IDX_MOVED_MODULE = 6, IDX_CHANGED_INACTIVE = 7, IDX_NEW_P = 8, IDX_NEW_SD = 9,
			IDX_TOTAL = 10, IDX_INACT_AMBIGUOUS = 11,  IDX_INACT_MOVED_ELSEWHERE = 12, IDX_INACT_CONCEPT_NON_CURRENT = 13,
			IDX_INACT_DUPLICATE = 14, IDX_INACT_ERRONEOUS = 15, IDX_INACT_INAPPROPRIATE = 16, IDX_INACT_LIMITED = 17,
			IDX_INACT_OUTDATED = 18, IDX_INACT_PENDING_MOVE = 19, IDX_INACT_NON_CONFORMANCE = 20,
			IDX_INACT_NOT_EQUIVALENT = 21, IDX_CONCEPTS_AFFECTED = 22, IDX_TOTAL_ACTIVE = 23, IDX_PROMOTED=24,
			IDX_NEW_IN_QI_SCOPE = 25, IDX_GAINED_ATTRIBUTES = 26, IDX_LOST_ATTRIBUTES = 27; 
	static Map<Integer, List<Integer>> sheetFieldsByIndex = getSheetFieldsMap();
	static List<DescriptionType> TEXT_DEFN;
	static List<DescriptionType> NOT_TEXT_DEFN;
	List<Concept> topLevelHierarchies;
	File debugFile;
	
	private int[][] totals;
	
	Concept[] QIScope = new Concept[] { BODY_STRUCTURE, CLINICAL_FINDING,
			PHARM_BIO_PRODUCT, PROCEDURE,
			SITN_WITH_EXP_CONTXT, SPECIMEN,
			OBSERVABLE_ENTITY, EVENT, 
			PHARM_DOSE_FORM};
	
	public static final EnumSet<ComponentType> typesToDebugToFile = EnumSet.of(ComponentType.ATTRIBUTE_VALUE);
	
	public static Set<String> refsetsToDebugToFile = new HashSet<>();
	/*static {
		refsetsToDebugToFile.add(SCTID_OWL_AXIOM_REFSET);
	}*/
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(THIS_RELEASE, "SnomedCT_USEditionRF2_PRODUCTION_20220301T120000Z.zip");
		//params.put(PREV_RELEASE, "SnomedCT_USEditionRF2_PRODUCTION_20210901T120000Z.zip");
		params.put(PREV_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20220930T120000Z.zip");
		params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_202201031T120000Z.zip");
		//params.put(REPORT_OUTPUT_TYPES, "S3");
		//params.put(REPORT_FORMAT_TYPE, "JSON");
		//params.put(MODULES, "731000124108");
		TermServerReport.run(SummaryComponentStats.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.STRING)
				.add(PREV_RELEASE).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.add(REPORT_OUTPUT_TYPES).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportOutputType.GOOGLE.name())
				.add(REPORT_FORMAT_TYPE).withType(JobParameter.Type.HIDDEN).withDefaultValue(ReportFormatType.CSV.name())
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Summary Component Stats for Editions")
				.withDescription("This report lists component changes per major hierarchy, optionally filtered by moduleId (comma separate if multiple).   You can either specify two releases to compare as archives stored in S3 " + 
				"(eg SnomedCT_InternationalRF2_PRODUCTION_20210131T120000Z.zip) or leave them blank to compare the current delta to the previous release as specified " +
				"by that branch.  Note that when running against the US edition, a module filter of '731000124108' should be applied.")
				.withParameters(params)
				.withTag(INT).withTag(MS)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withExpectedDuration(30)
				.build();
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		//Reset this flag for Editions as we might run against the same project so not reset as expected.
		getArchiveManager().setLoadDependencyPlusExtensionArchive(false);
		summaryDataMap = new HashMap<>();
		refsetDataMap = new HashMap<>();
		
		TEXT_DEFN = new ArrayList<>();
		TEXT_DEFN.add(DescriptionType.TEXT_DEFINITION);
		
		NOT_TEXT_DEFN = new ArrayList<>();
		NOT_TEXT_DEFN.add(DescriptionType.FSN);
		NOT_TEXT_DEFN.add(DescriptionType.SYNONYM);
		
		if (!StringUtils.isEmpty(run.getParamValue(MODULES))) {
			moduleFilter = Stream.of(run.getParamValue(MODULES).split(",", -1))
					.collect(Collectors.toList());
		}

		boolean runIntegrityChecks = Boolean.parseBoolean(run.getParamValue("runIntegrityChecks", "true"));
		info(String.format("Running report with runIntegrityChecks set to %b", runIntegrityChecks));
		getArchiveManager().setRunIntegrityChecks(runIntegrityChecks);
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"Sctid, Hierarchy, SemTag, New, Changed DefnStatus, Inactivated, Reactivated, New Inactive, New with New Concept, Moved Module, Changed Inactive, New SD, New P, Total Active, Total, Promoted",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
												"Sctid, Hierarchy, SemTag, New Inferred Rels, Changed Inferred Rels, Inactivated Inferred Rels, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
												"Sctid, Hierarchy, SemTag, New Inferred Rels, Changed Inferred Rels, Inactivated Inferred Rels, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
												"Sctid, Hierarchy, SemTag, New Axioms, Changed Axioms, Inactivated Axioms, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Concepts Affected, Total Active",
												"Sctid, Hierarchy, SemTag, Inactivations New / Reactivated, New Inactive, Changed, Inactivations Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Ambiguous, Moved Elsewhere, Concept Non Current, Duplicate, Erroneous, Inappropriate, Limited, Outdated, Pending Move, Non Conformance, Not Equivalent, Concepts Affected, Total Active",
												"Sctid, Hierarchy, SemTag, Assoc New, Changed, Assoc Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Concepts Affected, Total Active",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total, Concepts Affected, Total Active",
												"Sctid, Hierarchy, SemTag, In Scope New, Attributes Added, Model Removed, Model Inactivated, Total In Scope",
												"Sctid, Hierarchy, SemTag, New, Inactivated, Reactivated, New Inactive, Total, Total Active",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
												"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total"
												};
		String[] tabNames = new String[] {	"Concepts",
											"Descriptions",
											"Relationships",
											"Concrete Rels",
											"Axioms",
											"LangRefSet",
											"Inactivations",
											"Hist Assoc",
											"Text Defn",
											"QI Scope",
											"Desc Assoc",
											"Desc CNC",
											"Desc Inact",
											"Refsets"};
		topLevelHierarchies = new ArrayList<Concept>(ROOT_CONCEPT.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
		topLevelHierarchies.add(UNKNOWN_CONCEPT); // Add this a we might not always be able to get the top level hierarchy
		topLevelHierarchies.add(ROOT_CONCEPT);
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
		
		//Do we need to also produce a release summary file in S3?
		if (getReportManager().isWriteToS3() && isPublishedReleaseAnalysis) {
			try {
				generateReleaseSummaryFile();
			} catch (Exception e) {
				error("Failed to generate Release Summary File", e);
			}
		}
		info ("Cleaning Up");
		prevData = null;
		summaryDataMap = null;
		refsetDataMap = null;
		topLevelHierarchies = null;
		System.gc();
		info ("Complete");
	}

	@Override
	public String getReportComplexName() {
		if (projectKey != null && prevRelease != null) {
			complexName = removeTimeCode(removeZipExtension(projectKey)) + "---" + removeTimeCode(removeZipExtension(prevRelease));
		} else {
			complexName = super.getReportComplexName();
		}
		return complexName;
	}

	private void analyzeConcepts() throws TermServerScriptException {
		info ("Analysing concepts");
		Concept topLevel;
		for (Concept c : gl.getAllConcepts()) {
			/*if (c.getId().equals("322236009")) {
				debug("here");
			}*/
			Datum datum = prevData.get(c.getConceptId());
			//Is this concept in scope?  Even if its not, some of its components might be.
			if (c.isActive()) {	
				topLevel = SnomedUtils.getHierarchy(gl, c);
			} else {
				//Was it active in the previous release?
				if (datum != null) {
					topLevel = gl.getConcept(datum.hierarchy);
				} else {
					//If not, it's been created and made inactive since the previous data was created.
					//This is a separate category of concept.
					topLevel = UNKNOWN_CONCEPT;
				}
			}
			
			//Have we seen this hierarchy before?
			int[][] summaryData = summaryDataMap.get(topLevel);
			if (summaryData == null) {
				summaryData = new int[MAX_REPORT_TABS][DATA_WIDTH];
				summaryDataMap.put(topLevel, summaryData);
			}
			
			boolean isNewConcept = datum==null;
			Boolean wasSD = datum==null?null:datum.isSD;
			Boolean wasActive = datum==null?null:datum.isActive;
			
			//If the concept is no longer in the target module, we'll count that and ignore the rest
			if (moduleFilter != null && !moduleFilter.contains(c.getModuleId())) {
				//Was it in the target module last time?
				if (datum != null && moduleFilter.contains(datum.moduleId)) {
					summaryData[TAB_CONCEPTS][IDX_PROMOTED]++;
				}
			} else {
				analyzeConcept(c, topLevel, datum, wasSD, wasActive, summaryData[TAB_CONCEPTS], summaryData[TAB_QI]);
			}
			
			analyzeDescriptions(c, topLevel, datum, wasActive, summaryData[TAB_DESC_HIST], summaryData[TAB_DESC_INACT], summaryData[TAB_DESC_CNC]);
			
			List<Relationship> normalRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)
					.stream()
					.filter(r -> !r.isConcrete())
					.collect(Collectors.toList());
			
			List<Relationship> concreteRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)
					.stream()
					.filter(r -> r.isConcrete())
					.collect(Collectors.toList());
			
			//Component changes
			analyzeComponents(isNewConcept, (datum==null?null:datum.descIds), (datum==null?null:datum.descIdsInact), summaryData[TAB_DESCS], c.getDescriptions(ActiveState.BOTH, NOT_TEXT_DEFN));
			analyzeComponents(isNewConcept, (datum==null?null:datum.descIds), (datum==null?null:datum.descIdsInact), summaryData[TAB_TEXT_DEFN], c.getDescriptions(ActiveState.BOTH, TEXT_DEFN));
			analyzeComponents(isNewConcept, (datum==null?null:datum.relIds), (datum==null?null:datum.relIdsInact), summaryData[TAB_RELS], normalRels);
			analyzeComponents(isNewConcept, (datum==null?null:datum.relIds), (datum==null?null:datum.relIdsInact), summaryData[TAB_CD], concreteRels);
			analyzeComponents(isNewConcept, (datum==null?null:datum.axiomIds), (datum==null?null:datum.axiomIdsInact), summaryData[TAB_AXIOMS], c.getAxiomEntries());
			analyzeComponents(isNewConcept, (datum==null?null:datum.inactivationIds), (datum==null?null:datum.inactivationIdsInact), summaryData[TAB_INACT_IND], c.getInactivationIndicatorEntries());
			analyzeComponents(isNewConcept, (datum==null?null:datum.histAssocIds), (datum==null?null:datum.histAssocIdsInact), summaryData[TAB_HIST], c.getAssociations(ActiveState.BOTH, true));
			List<LangRefsetEntry> langRefsetEntries = c.getDescriptions().stream()
					.flatMap(d -> d.getLangRefsetEntries().stream())
					.collect(Collectors.toList());
			analyzeComponents(isNewConcept, (datum==null?null:datum.langRefsetIds), (datum==null?null:datum.langRefsetIdsInact), summaryData[TAB_LANG], langRefsetEntries);
		}
	}
	
	private void analyzeConcept(Concept c, Concept topLevel, Datum datum, Boolean wasSD, Boolean wasActive, int[] counts, int[] qiCounts) throws TermServerScriptException {
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
				
				//Has the concept remained active, but moved into this module?
				if (datum != null && !datum.moduleId.equals(c.getModuleId())) {
					counts[IDX_MOVED_MODULE]++;
				}
			}
		} else if (prevData.containsKey(c.getConceptId())) {
			if (prevData.get(c.getConceptId()).isActive) {
				//If we had it last time active, then it's been inactivated in this release
				counts[IDX_INACT]++;
			} else if (isChangedSinceLastRelease(c)){
				//If it's inactive, was inactive last time and yet has still changed, then it's changed inactive
				counts[IDX_CHANGED_INACTIVE]++;
			}
		} else if (!prevData.containsKey(c.getConceptId())) {
			//If it's inactive and we DIDN'T see it before, then we've got a born inactive or "New Inactive" concept
			counts[IDX_NEW_INACTIVE]++;
		}
		counts[IDX_TOTAL]++;
		
		//Check state change for QI tab
		//Are we in scope for QI?
		if (inQIScope(topLevel)) {
			//Does it have a model?
			boolean hasModel = SnomedUtils.countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) > 0;
			boolean hadModel = prevData.containsKey(c.getConceptId()) && prevData.get(c.getConceptId()).hasAttributes;
			//Is it new with a model ?
			//Or Has it gained a model since we last saw it?
			if (conceptIsNew && hasModel) {
				qiCounts[IDX_NEW_IN_QI_SCOPE]++;
			} else if (!conceptIsNew && !hadModel && hasModel) {
				qiCounts[IDX_GAINED_ATTRIBUTES]++;
			} else if (c.isActive() && !conceptIsNew && hadModel && !hasModel) {
				qiCounts[IDX_LOST_ATTRIBUTES]++;
			} else if (!c.isActive() && hadModel) {
				qiCounts[IDX_INACT]++;
			}
			
			if (hasModel) {
				qiCounts[IDX_TOTAL_ACTIVE]++;
			}
		}
		
	}
	
	private int[] getRefsetData (String refsetId) {
		int[] refsetCounts = refsetDataMap.get(refsetId);
		if (refsetCounts == null) {
			refsetCounts = new int[DATA_WIDTH];
			refsetDataMap.put(refsetId, refsetCounts);
		}
		return refsetCounts;
	}
	
	private void analyzeDescriptions(Concept c, Concept topLevel, Datum datum, Boolean wasActive, int[] counts, int[] inactCounts, int[] cncCounts) throws TermServerScriptException {
		for (Description d : c.getDescriptions()) {
			/*if (d.getId().equals("3770564011")) {
				debug("here");
			}*/
			//If the description is not in the target module, skip it.
			//TODO We can count promoted descriptions also
			if (moduleFilter != null && !moduleFilter.contains(d.getModuleId())) {
				continue;
			}
			
			//Descriptions have historic associations if the refer to other concepts
			for (AssociationEntry a : d.getAssociationEntries()) {
				incrementCounts(a, counts, IDX_TOTAL);
				if (a.isActive()) {
					incrementCounts(a, counts, IDX_TOTAL_ACTIVE);
					//Have we see this Id before?  If not, it's new
					if (datum != null && !datum.descHistAssocIds.contains(a.getId())) {
						incrementCounts(a, counts, IDX_NEW);
					} else if (datum != null && datum.descHistAssocIdsInact.contains(a.getId())) {
						//If previously inactive and now active, then it's reactivated
						incrementCounts(a, counts, IDX_REACTIVATED);
					} else if (isChangedSinceLastRelease(a)) {
						//Did it change in this release?
						incrementCounts(a, counts, IDX_CHANGED);
						debugToFile(a, "Changed");
					}
				} else {
					//If we saw this previously active, then it's been inactivated
					if (datum != null && datum.descHistAssocIds.contains(a.getId())) {
						incrementCounts(a, counts, IDX_INACT);
					} else if (datum != null && datum.descHistAssocIdsInact.contains(a.getId())) {
						//If it was previous inactive and continues to be so, yet has changed, then it's changed inactive
						if (isChangedSinceLastRelease(a)) {
							incrementCounts(a, counts, IDX_CHANGED_INACTIVE);
						}
					} else if (datum == null) {
						incrementCounts(a, counts, IDX_NEW_INACTIVE);
					}
				}
			}
			
			//Descriptions can also have inactivation indicators if their concept is inactive
			for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
				//Are we writing our results to the default tab, or specific to CNC?
				int[] thisInactTab = i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT) ? cncCounts: inactCounts;
				incrementCounts(i, thisInactTab, IDX_TOTAL);
				if (i.isActive()) {
					incrementCounts(i, thisInactTab, IDX_TOTAL_ACTIVE);
					//Have we see this Id before?  If not, it's new
					if (datum != null && !datum.descInactivationIds.contains(i.getId())) {
						incrementCounts(i, thisInactTab, IDX_NEW);
						debugToFile(i, "New");
					} else if (datum != null && datum.descInactivationIdsInact.contains(i.getId())) {
						//If previously inactive and now active, then it's reactivated
						incrementCounts(i, thisInactTab, IDX_REACTIVATED);
						debugToFile(i, "Reactivated");
					} else if (isChangedSinceLastRelease(i)) {
						//Did it change in this release?
						incrementCounts(i, thisInactTab, IDX_CHANGED);
						debugToFile(i, "Changed");
					} 
				} else {
					//If we saw this previously active, then it's been inactivated
					if (datum != null && datum.descInactivationIds.contains(i.getId())) {
						incrementCounts(i, thisInactTab, IDX_INACT);
					} else if (datum != null && datum.descInactivationIdsInact.contains(i.getId())) {
						//If it was previous inactive and continues to be so, yet has changed, then it's changed inactive
						if (isChangedSinceLastRelease(i)) {
							incrementCounts(i, counts, IDX_CHANGED_INACTIVE);
						}
					} else if (datum == null) {
						incrementCounts(i, thisInactTab, IDX_NEW_INACTIVE);
					}
				}
			}
		} 
	}

	private boolean inQIScope(Concept topLevel) {
		for (Concept scope : QIScope) {
			if (topLevel.equals(scope)) {
				return true;
			}
		}
		return false;
	}

	private void analyzeComponents(boolean isNewConcept, Collection<String> ids, Collection<String> idsInactive, int[] counts, Collection<? extends Component> components) throws TermServerScriptException {
		//If we have no previous data, then the concept is new
		boolean conceptIsNew = (ids == null && idsInactive == null);
		boolean conceptAffected = false;
		for (Component component : components) {
			/*if (component.getId().equals("8f7a882f-b4e7-43d1-81e8-d2cfae503000") ||
					component.getId().equals("b6507605-25fa-4a0b-88d1-26327247da86")) {
				debug("here");
			}*/
			if (moduleFilter != null && !moduleFilter.contains(component.getModuleId())) {
				continue;
			}
			//Was the description present in the previous data?
			boolean previouslyExistedActive = false;
			boolean previouslyExistedInactive = false;
			if (!conceptIsNew) {
				previouslyExistedActive = ids.contains(component.getId());
				previouslyExistedInactive = idsInactive.contains(component.getId());
			}
			if (component.isActive()) {
				incrementCounts(component, counts, IDX_TOTAL_ACTIVE);
				if (previouslyExistedInactive) {
					incrementCounts(component, counts, IDX_REACTIVATED);
					debugToFile(component, "Reactivated");
					conceptAffected = true;
				} else if(!previouslyExistedActive) {
					
					/*if (component instanceof RefsetMember) {
						RefsetMember refsetMember = (RefsetMember) component;
						if (refsetMember.getRefsetId().equals("900000000000509007")) {
							debug("here");
						}
					}*/
					incrementCounts(component, counts, IDX_NEW);
					debugToFile(component, "New");
					conceptAffected = true;
					if (isNewConcept) {
						//This component is new because it was created as part of a new concept
						//so it's not been 'added' as such.  Well, we might want to count additions
						//to existing concepts separately.
						incrementCounts(component, counts, IDX_NEW_NEW);;
						debugToFile(component, "NewNew");
					}
					// find out the reason
					if (component instanceof InactivationIndicatorEntry) {
						InactivationIndicatorEntry inactivationIndicatorEntry = (InactivationIndicatorEntry) component;
						incrementInactivationReason (counts, inactivationIndicatorEntry.getInactivationReasonId());
					}
				} else if (isChangedSinceLastRelease(component)) {
					//Did it change in this release?
					incrementCounts(component, counts, IDX_CHANGED);
					debugToFile(component, "Changed");
					conceptAffected = true;
				}
			} else if (previouslyExistedActive) {
				//Existed previously active and is now inactive, mark as inactivated
				incrementCounts(component, counts, IDX_INACT);
				debugToFile(component, "Inactivated");
				conceptAffected = true;
			} else if (!previouslyExistedActive && !previouslyExistedInactive) {
				incrementCounts(component, counts, IDX_NEW_INACTIVE);
			} else if (previouslyExistedInactive && isChangedSinceLastRelease(component)) {
				incrementCounts(component, counts, IDX_CHANGED_INACTIVE);
			}
			incrementCounts(component, counts, IDX_TOTAL);
			debugToFile(component, "Total");
		}
		if (conceptAffected) {
			counts[IDX_CONCEPTS_AFFECTED]++;
		}
	}

	private boolean isChangedSinceLastRelease(Component component) {
		return StringUtils.isEmpty(component.getEffectiveTime()) 
				|| component.getEffectiveTime().compareTo(previousEffectiveTime) > 0; 
	}

	private void incrementCounts(Component component, int[] counts, int idx) {
		counts[idx]++;
		//If this component is a refset member, then also increment our refset data
		if (component instanceof RefsetMember) {
			RefsetMember refsetMember = (RefsetMember) component;
			getRefsetData(refsetMember.getRefsetId())[idx]++;
		}
	}

	private void debugToFile(Component c, String statType) throws TermServerScriptException {
		// Only debug if we enable it (for testing really).
		if (!debugToFile || !typesToDebugToFile.contains(c.getComponentType())) {
			return;
		}
		
		if (refsetsToDebugToFile.size() > 0 && c instanceof RefsetMember) {
			RefsetMember rm = (RefsetMember)c;
			if (!refsetsToDebugToFile.contains(rm.getRefsetId())) {
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
			case SCTID_INACT_NOT_SEMANTICALLY_EQUIVALENT:
				counts[IDX_INACT_NOT_EQUIVALENT]++;
				break;
		}
	}

	private void outputResults() throws TermServerScriptException {
		Concept totalConcept = new Concept("","Total");
		totals = new int[MAX_REPORT_TABS][DATA_WIDTH];
		for (Concept hierarchy : topLevelHierarchies) {
			int[][] summaryData = summaryDataMap.get(hierarchy);
			if (summaryData != null) {
				for (int idxTab = 0; idxTab < MAX_REPORT_TABS - 1; idxTab++) {
					report(idxTab, hierarchy, summaryData[idxTab]);
					for (int idxMovement = 0; idxMovement < DATA_WIDTH; idxMovement++) {
						totals[idxTab][idxMovement] += summaryData[idxTab][idxMovement];
					}
				}
			}
		}
		
		//Refset data is not broken down by major hierarchy
		//Split into each type of refset with sub totals
		outputRefsetData("association", totals);
		outputRefsetData("language", totals);
		outputRefsetData("indicator", totals);
		
		for (int idxTab = 0; idxTab < MAX_REPORT_TABS; idxTab++) {
			report (idxTab, totalConcept, totals[idxTab]);
		}
	}
	
	private void outputRefsetData(String filter, int[][] totals) throws TermServerScriptException {
		int[] subTotals = new int[DATA_WIDTH];
		Concept subTotalConcept = new Concept("","  SubTotal");
		for (Map.Entry<String, int[]> entry : refsetDataMap.entrySet()) {
			Concept refset = gl.getConcept(entry.getKey());
			if (refset.getFsn().contains(filter)) {
				report(MAX_REPORT_TABS -1, refset, entry.getValue());
				for (int idxMovement = 0; idxMovement < DATA_WIDTH; idxMovement++) {
					subTotals[idxMovement] += entry.getValue()[idxMovement];
					totals[MAX_REPORT_TABS -1][idxMovement] +=  entry.getValue()[idxMovement];
				}
			}
		}
		report(MAX_REPORT_TABS -1, subTotalConcept, subTotals);
		report(MAX_REPORT_TABS -1, "");
	}

	protected void report (int idxTab, Concept c, int[] data) throws TermServerScriptException {
		super.report(idxTab, c, getReportData(idxTab, data));
		countIssue(c);
	}

	private static Map<Integer, List<Integer>> getSheetFieldsMap() {
		// set up the report sheets and the fields they contain
		final Map<Integer, List<Integer>> sheetFieldsByIndex = new HashMap<>();

		sheetFieldsByIndex.put(TAB_CONCEPTS, new LinkedList<Integer>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_MOVED_MODULE, IDX_CHANGED_INACTIVE, IDX_NEW_SD, IDX_NEW_P, IDX_TOTAL_ACTIVE, IDX_TOTAL, IDX_PROMOTED)));

		Arrays.asList(TAB_DESCS, TAB_RELS, TAB_CD, TAB_AXIOMS, TAB_TEXT_DEFN).stream().forEach(index -> {
			sheetFieldsByIndex.put(index, new LinkedList<Integer>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE, IDX_TOTAL_ACTIVE, IDX_TOTAL, IDX_CONCEPTS_AFFECTED)));
		});
		
		Arrays.asList(TAB_DESC_CNC, TAB_DESC_INACT, TAB_REFSET).stream().forEach(index -> {
			sheetFieldsByIndex.put(index, new LinkedList<Integer>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE, IDX_TOTAL_ACTIVE, IDX_TOTAL)));
		});

		sheetFieldsByIndex.put(TAB_INACT_IND, new LinkedList<Integer>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE, IDX_INACT_AMBIGUOUS,
				IDX_INACT_MOVED_ELSEWHERE, IDX_INACT_CONCEPT_NON_CURRENT, IDX_INACT_DUPLICATE, IDX_INACT_ERRONEOUS,
				IDX_INACT_INAPPROPRIATE, IDX_INACT_LIMITED, IDX_INACT_OUTDATED, IDX_INACT_PENDING_MOVE, IDX_INACT_NON_CONFORMANCE,
				IDX_INACT_NOT_EQUIVALENT, IDX_CONCEPTS_AFFECTED, IDX_TOTAL_ACTIVE)));

		Arrays.asList(TAB_LANG, TAB_HIST).stream().forEach(index -> {
			sheetFieldsByIndex.put(index, new LinkedList<Integer>((Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE, IDX_CONCEPTS_AFFECTED, IDX_TOTAL_ACTIVE))));
		});
		
		sheetFieldsByIndex.put(TAB_QI, new LinkedList<Integer>(Arrays.asList(IDX_NEW_IN_QI_SCOPE, IDX_GAINED_ATTRIBUTES, IDX_LOST_ATTRIBUTES, IDX_INACT, IDX_TOTAL_ACTIVE)));

		sheetFieldsByIndex.put(TAB_DESC_HIST, new LinkedList<Integer>(Arrays.asList(IDX_NEW, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_TOTAL, IDX_TOTAL_ACTIVE)));

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
	

	private void generateReleaseSummaryFile() throws FileNotFoundException, IOException, TermServerScriptException {
		//What package are we reporting on here? eg SnomedCT_InternationalRF2_PRODUCTION_20220331T120000Z.zip
		//Pull out "InternationalRF2"
		String packageName = projectKey.split("_")[1];
		
		//Can we find this file in S3?
		String classShortName = this.getClass().getSimpleName();
		String releaseSummaryFilePath =  "jobs/" + classShortName + "/ReleaseSummaries/" + packageName + "/" + packageName + "_ReleaseSummaries.json";
		File releaseSummaryFile = new File(releaseSummaryFilePath);
		
		ReleaseSummary rs = null;
		if (reportDataBroker.exists(releaseSummaryFile)) {
			rs = ReleaseSummary.loadFromS3(releaseSummaryFile, reportDataBroker);
			//Save a copy of the data with current timestamp
			String timeStamp = new SimpleDateFormat("_yyyyMMdd_HHmmss").format(new Date());
			File stampedCopy = new File(releaseSummaryFile.getPath().replace(".json",timeStamp + ".json"));
			rs.uploadToS3(stampedCopy, reportDataBroker);
		} else if (releaseSummaryFilePath.contains("International")){
			rs = ReleaseSummary.loadFromLocal(new File("resources/legacy_int_release_summary.json"));
		} else {
			rs = new ReleaseSummary();
		}
		
		//Now add the data obtained in our most recent analysis
		rs.addDetail(compileReleaseSummaryDetail());
		rs.uploadToS3(releaseSummaryFile, reportDataBroker);
	}

	private ReleaseSummaryDetail compileReleaseSummaryDetail() {
		ReleaseSummaryDetail rs =  new ReleaseSummaryDetail();
		rs.setEffectiveTime(thisEffectiveTime);
		rs.setPreviousEffectiveTime(previousEffectiveTime);
		
		String[] data = new String[24];
		data[0]  = sum(TAB_CONCEPTS, IDX_NEW);
		data[1]  = sum(TAB_CONCEPTS, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_MOVED_MODULE);
		data[2]  = sum(TAB_CONCEPTS, IDX_TOTAL);
		data[3]  = sum(TAB_DESCS, IDX_NEW_NEW);
		data[4]  = minusPlus(TAB_DESCS, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[5]  = sum(TAB_DESCS, IDX_TOTAL);
		data[6]  = sum(TAB_TEXT_DEFN, IDX_NEW_NEW);
		data[7]  = minusPlus(TAB_TEXT_DEFN, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[8]  = sum(TAB_TEXT_DEFN, IDX_TOTAL);
		data[9]  = sum(TAB_LANG, IDX_NEW_NEW);
		data[10] = minusPlus(TAB_LANG, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[11] = sum(TAB_LANG, IDX_TOTAL);
		data[12] = sum(TAB_AXIOMS, IDX_NEW_NEW);
		data[13] = minusPlus(TAB_AXIOMS, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[14] = sum(TAB_AXIOMS, IDX_TOTAL);
		data[15] = "0";
		data[16] = "0";
		data[17] = "1024719";
		data[18] = sum(TAB_RELS, IDX_NEW_NEW);
		data[19] = minusPlus(TAB_RELS, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[20] = sum(TAB_RELS, IDX_TOTAL);
		data[21] = sum(TAB_CD, IDX_NEW_NEW);
		data[22] = minusPlus(TAB_CD, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACT, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[23] = sum(TAB_CD, IDX_TOTAL);
		
		rs.setData(data);
		return rs;
	}

	private String sum(int tabIdx, int... columns) {
		long sumTotal = 0;
		for (int columnIdx : columns) {
			sumTotal += totals[tabIdx][columnIdx];
		}
		return Long.toString(sumTotal);
	}
	
	private String minusPlus(int tabIdx, int addIdx, int subtractIdx, int... columns) {
		long sumTotal = 0;
		sumTotal += totals[tabIdx][addIdx];
		sumTotal -= totals[tabIdx][subtractIdx];
		for (int columnIdx : columns) {
			sumTotal += totals[tabIdx][columnIdx];
		}
		return Long.toString(sumTotal);
	}

	private String removeZipExtension(String input) {
		return input.replaceAll("\\.zip", "");
	}

	private String removeTimeCode(String input) {
		// Grab date, i.e. 20210901T120000Z
		String dateBefore = input.substring(input.lastIndexOf("_") + 1);

		// Change date to new format (i.e. remove time code)
		try {
			String dateAfter = dateBefore;
			SimpleDateFormat sourceSDF = new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'");
			SimpleDateFormat targetSDF = new SimpleDateFormat("yyyyMMdd");
			sourceSDF.setTimeZone(TimeZone.getTimeZone("UTC"));
			dateAfter = targetSDF.format(sourceSDF.parse(dateAfter));

			// i.e. SnomedCT_InternationalRF2_PRODUCTION_20220731T120000Z => SnomedCT_InternationalRF2_PRODUCTION_20220731
			return input.replaceAll(dateBefore, dateAfter);
		} catch (ParseException e) {
			warn(String.format("Cannot remove time code from %s", input));
		}

		// Return original as default
		return input;
	}

}
