package org.ihtsdo.termserver.scripting.reports.release;

import org.apache.commons.io.FileUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportConfiguration.ReportFormatType;
import org.snomed.otf.script.dao.ReportConfiguration.ReportOutputType;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Stream;

import static java.nio.charset.StandardCharsets.UTF_8;
/**
 * RP-288
 * */
public class SummaryComponentStats extends HistoricDataUser implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(SummaryComponentStats.class);

	static final int TAB_CONCEPTS = 0, TAB_DESCS = 1, TAB_RELS = 2, TAB_CD = 3, TAB_AXIOMS = 4,
			TAB_LANG = 5, TAB_INACT_IND = 6, TAB_HIST = 7, TAB_TEXT_DEFN = 8, TAB_QI = 9,
			TAB_DESC_HIST = 10, TAB_DESC_CNC = 11, TAB_DESC_INACT = 12, TAB_REFSET = 13,
			TAB_DESC_BY_LANG = 14;  
	static final int MAX_REPORT_TABS = 15;
	static final int DATA_WIDTH = 29;  //New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Moved Module, Changed Inactive, extra1, extra2, Total, next 11 fields are the inactivation reason, concept affected, reactivated
	static final int IDX_NEW = 0, IDX_CHANGED = 1, IDX_INACTIVATED = 2, IDX_REACTIVATED = 3, IDX_NEW_INACTIVE = 4, IDX_NEW_NEW = 5,
			IDX_MOVED_MODULE = 6, IDX_CHANGED_INACTIVE = 7, IDX_NEW_P = 8, IDX_NEW_SD = 9,
			IDX_TOTAL = 10, IDX_INACT_AMBIGUOUS = 11,  IDX_INACT_MOVED_ELSEWHERE = 12, IDX_INACT_CONCEPT_NON_CURRENT = 13,
			IDX_INACT_DUPLICATE = 14, IDX_INACT_ERRONEOUS = 15, IDX_INACT_INAPPROPRIATE = 16, IDX_INACT_LIMITED = 17,
			IDX_INACT_OUTDATED = 18, IDX_INACT_PENDING_MOVE = 19, IDX_INACT_NON_CONFORMANCE = 20,
			IDX_INACT_NOT_EQUIVALENT = 21, IDX_CONCEPTS_AFFECTED = 22, IDX_TOTAL_ACTIVE = 23, IDX_PROMOTED=24,
			IDX_NEW_IN_QI_SCOPE = 25, IDX_GAINED_ATTRIBUTES = 26, IDX_LOST_ATTRIBUTES = 27, IDX_INACT_OTHER = 28;
	
	static Map<Integer, List<Integer>> sheetFieldsByIndex = getSheetFieldsMap();

	protected static final List<DescriptionType> TEXT_DEFN = List.of(DescriptionType.TEXT_DEFINITION);
	protected static final List<DescriptionType> NOT_TEXT_DEFN = List.of(DescriptionType.FSN, DescriptionType.SYNONYM);

	static final String DESCRIPTION = "Description";
	static final String TEXT_DEFINITION = "Text Defn";

	List<Concept> topLevelHierarchies;
	File debugFile;

	protected int[][] totals;
	protected Map<String, Map<String, int[]>> descriptionStatsByLanguage = new HashMap<>();

	protected int[] associationSubTotals = new int[DATA_WIDTH];
	protected int[] languageSubTotals = new int[DATA_WIDTH];
	protected int[] indicatorSubTotals = new int[DATA_WIDTH];

	private String[] columnHeadings = new String[] {
			// * Concepts
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Moved Module, Changed Inactive, New SD, New P, Total Active, Total, Promoted",
			// * Descriptions
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
			// * Relationships
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
			// * Concrete Rels
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
			// * Axioms
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
			// * LangRefSet
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Concepts Affected",
			// * Inactivations
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Ambiguous, Moved Elsewhere, Concept Non Current, Duplicate, Erroneous, Inappropriate, Limited, Outdated, Pending Move, Non Conformance, Not Equivalent, Other, Total Active, Concepts Affected",
			// * Hist Assoc
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Concepts Affected",
			// * Text Defn
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total, Concepts Affected",
			// * QI Scope
			"Sctid, Hierarchy, SemTag, In Scope New, Attributes Added, Model Removed, Model Inactivated, Total In Scope",
			// * Desc Assoc
			"Sctid, Hierarchy, SemTag, New, Inactivated, Reactivated, New Inactive, Total Active, Total",
			// * Desc CNC
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
			// * Desc Inact
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
			// * Refsets
			"Sctid, Hierarchy, SemTag, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total",
			// * Desc By Lang
			" , , Language, New, Changed, Inactivated, Reactivated, New Inactive, New with New Concept, Changed Inactive, Total Active, Total"
	};
	private String[] tabNames = new String[] {
			"Concepts",
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
			"Refsets",
			"Desc by Lang"
	};

	Concept[] QIScope = new Concept[] { BODY_STRUCTURE, CLINICAL_FINDING,
			PHARM_BIO_PRODUCT, PROCEDURE,
			SITN_WITH_EXP_CONTXT, SPECIMEN,
			OBSERVABLE_ENTITY, EVENT, 
			PHARM_DOSE_FORM};
	
	protected static final EnumSet<ComponentType> typesToDebugToFile = EnumSet.of(ComponentType.CONCEPT);
	
	protected static Set<String> refsetsToDebugToFile = new HashSet<>();

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(PREV_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20250101T120000Z.zip");
		params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20241201T120000Z.zip");
		// REPORT_OUTPUT_TYPES, "S3" REPORT_FORMAT_TYPE, "JSON"
		TermServerScript.run(SummaryComponentStats.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(THIS_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
				.add(PREV_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE)
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
	
	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		//Reset this flag for Editions as we might run against the same project so not reset as expected.
		getArchiveManager().setLoadDependencyPlusExtensionArchive(false);
		summaryDataMap = new HashMap<>();
		refsetDataMap = new HashMap<>();
		
		boolean runIntegrityChecks = Boolean.parseBoolean(run.getParamValue("runIntegrityChecks", "true"));
		LOGGER.info("Running report with runIntegrityChecks set to {}", runIntegrityChecks);
		getArchiveManager().setRunIntegrityChecks(runIntegrityChecks);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		topLevelHierarchies = new ArrayList<>(ROOT_CONCEPT.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
		topLevelHierarchies.add(UNKNOWN_CONCEPT); // Add this a we might not always be able to get the top level hierarchy
		topLevelHierarchies.add(ROOT_CONCEPT);
		topLevelHierarchies.sort(Comparator.comparing(Concept::getFsn));
		super.postInit(getTabNames(), getColumnHeadings());
	}

	public String[] getTabNames() {
		return tabNames;
	}

	public String[] getColumnHeadings() {
		return columnHeadings;
	}

	@Override
	public String[] getColumnWidths() {
		String[] columnWidths = new String[MAX_REPORT_TABS];

		for (int tabIdx = 0; tabIdx < columnWidths.length; tabIdx++) {
			String[] tabColumnWidths = new String[DATA_WIDTH];
			Arrays.fill(tabColumnWidths, 0, 3, "0");
			Arrays.fill(tabColumnWidths, 3, DATA_WIDTH, "85");
			columnWidths[tabIdx] = String.join(",", tabColumnWidths);
		}

		return columnWidths;
	}

	@Override
	public void runJob() throws TermServerScriptException {
		LOGGER.info("Loading Previous Data");
		loadData(prevRelease);
		LOGGER.info("Analysing Data");
		analyzeConcepts();
		LOGGER.info("Outputting Results");
		outputResults();
		
		//Do we need to also produce a release summary file in S3?
		if (getReportManager().isWriteToS3() && isPublishedReleaseAnalysis) {
			try {
				generateReleaseSummaryFile();
			} catch (Exception e) {
				LOGGER.error("Failed to generate Release Summary File", e);
			}
		}
		LOGGER.info("Cleaning Up");
		prevData = null;
		summaryDataMap = null;
		refsetDataMap = null;
		topLevelHierarchies = null;
		System.gc();
		LOGGER.info("Complete");
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
		LOGGER.info("Analysing concepts");
		Concept topLevel;
		for (Concept c : gl.getAllConcepts()) {
			HistoricData datum = prevData.get(c.getConceptId());
			//Is this concept in scope?  Even if its not, some of its components might be.
			if (c.isActiveSafely()) {
				topLevel = SnomedUtils.getHierarchy(gl, c);
			} else {
				//Was it active in the previous release?
				if (datum != null) {
					topLevel = gl.getConcept(datum.getHierarchy());
				} else {
					//If not, it's been created and made inactive since the previous data was created.
					//This is a separate category of concept.
					topLevel = UNKNOWN_CONCEPT;
				}
			}

			//Have we seen this hierarchy before?
			int[][] summaryData = summaryDataMap.computeIfAbsent(topLevel, k -> new int[MAX_REPORT_TABS][DATA_WIDTH]);
			boolean isNewConcept = datum == null;

			analyzeConcept(c, topLevel, datum, summaryData[TAB_CONCEPTS], summaryData[TAB_QI]);

			analyzeDescriptions(c, datum, summaryData[TAB_DESC_HIST], summaryData[TAB_DESC_INACT], summaryData[TAB_DESC_CNC]);

			List<Relationship> normalRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)
					.stream()
					.filter(r -> !r.isConcrete())
					.toList();

			List<Relationship> concreteRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.BOTH)
					.stream()
					.filter(Relationship::isConcrete)
					.toList();

			//Component changes
			analyzeComponents(isNewConcept, (datum==null?null:datum.getDescIds()), (datum==null?null:datum.getDescIdsInact()), summaryData[TAB_DESCS], c.getDescriptions(ActiveState.BOTH, NOT_TEXT_DEFN));
			analyzeComponents(isNewConcept, (datum==null?null:datum.getDescIds()), (datum==null?null:datum.getDescIdsInact()), summaryData[TAB_TEXT_DEFN], c.getDescriptions(ActiveState.BOTH, TEXT_DEFN));
			analyzeComponents(isNewConcept, (datum==null?null:datum.getRelIds()), (datum==null?null:datum.getRelIdsInact()), summaryData[TAB_RELS], normalRels);
			analyzeComponents(isNewConcept, (datum==null?null:datum.getRelIds()), (datum==null?null:datum.getRelIdsInact()), summaryData[TAB_CD], concreteRels);
			analyzeComponents(isNewConcept, (datum==null?null:datum.getAxiomIds()), (datum==null?null:datum.getAxiomIdsInact()), summaryData[TAB_AXIOMS], c.getAxiomEntries());
			analyzeComponents(isNewConcept, (datum==null?null:datum.getInactivationIds()), (datum==null?null:datum.getInactivationIdsInact()), summaryData[TAB_INACT_IND], c.getInactivationIndicatorEntries());
			analyzeComponents(isNewConcept, (datum==null?null:datum.getHistAssocIds()), (datum==null?null:datum.getHistAssocIdsInact()), summaryData[TAB_HIST], c.getAssociationEntries(ActiveState.BOTH, true));
			List<LangRefsetEntry> langRefsetEntries = c.getDescriptions().stream()
					.flatMap(d -> d.getLangRefsetEntries().stream())
					.toList();
			analyzeComponents(isNewConcept, (datum==null?null:datum.getLangRefsetIds()), (datum==null?null:datum.getLangRefsetIdsInact()), summaryData[TAB_LANG], langRefsetEntries);
		}
	}

	private void analyzeConcept(Concept c, Concept topLevel, HistoricData datum, int[] counts, int[] qiCounts) {
		// Concept is no longer in the target module
		if (matchesModuleFilter(c.getModuleId())) {
			counts[IDX_TOTAL]++;
			if (c.isActiveSafely()) {
				incrementActiveConceptCounts(c, datum, counts);
			} else {
				incrementInactiveConceptCounts(c, datum, counts);
			}

			//Check state change for QI tab
			//Are we in scope for QI?
			if (inQIScope(topLevel)) {
				incrementQIScopeCounts(c, datum, qiCounts);
			}
		} else {
			// Was it in the target module last time?
			if (datum != null && matchesModuleFilter(datum.getModuleId())) {
				counts[IDX_PROMOTED]++;
			}
		}
	}

	private void incrementActiveConceptCounts(Concept c, HistoricData datum, int[] counts) {
		counts[IDX_TOTAL_ACTIVE]++;
		if (datum == null) {
			// No previous data, so this is a new concept
			if (c.isPrimitive()) {
				counts[IDX_NEW_P]++;
			} else {
				counts[IDX_NEW_SD]++;
			}
			counts[IDX_NEW]++;
			counts[IDX_NEW_NEW]++;
		} else if (datum.getModuleId().equals(c.getModuleId())) {
			if (!datum.isActive()) {
				// Was inactive in the last release, but active now
				counts[IDX_REACTIVATED]++;
			} else if (isChangedSinceLastRelease(c)) {
				// Remains active and changed since last release
				counts[IDX_CHANGED]++;
			}
		} else {
			// Was in a different module but has now moved into this module
			counts[IDX_MOVED_MODULE]++;
		}
	}

	private void incrementInactiveConceptCounts(Concept c, HistoricData datum, int[] counts) {
		if (datum == null) {
			// No previous data and inactive, so this is a new inactive concept
			counts[IDX_NEW_INACTIVE]++;
		} else if (datum.getModuleId().equals(c.getModuleId())) {
			if (datum.isActive()) {
				// Was active in the last release, but inactive now
				counts[IDX_INACTIVATED]++;
			} else if (isChangedSinceLastRelease(c)) {
				// Remains inactive and changed since last release
				counts[IDX_CHANGED_INACTIVE]++;
			}
		} else {
			// Was in a different module but has now moved into this module
			counts[IDX_MOVED_MODULE]++;
		}
	}

	private void incrementQIScopeCounts(Concept c, HistoricData datum, int[] qiCounts) {
		//Does it have a model?
		boolean hasModel = SnomedUtils.countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP) > 0;
		boolean hadModel = datum != null && datum.hasAttributes();
		//Is it new with a model ?
		//Or Has it gained a model since we last saw it?
		if (datum == null && hasModel) {
			qiCounts[IDX_NEW_IN_QI_SCOPE]++;
		} else if (datum != null && !hadModel && hasModel) {
			qiCounts[IDX_GAINED_ATTRIBUTES]++;
		} else if (c.isActiveSafely() && datum != null && hadModel && !hasModel) {
			qiCounts[IDX_LOST_ATTRIBUTES]++;
		} else if (!c.isActiveSafely() && hadModel) {
			qiCounts[IDX_INACTIVATED]++;
		}

		if (hasModel) {
			qiCounts[IDX_TOTAL_ACTIVE]++;
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
	
	private int[] getDescriptionByLanguageArray (DescriptionType type, String lang) {
		String statBucket = (type == DescriptionType.TEXT_DEFINITION ? TEXT_DEFINITION : DESCRIPTION);
		Map<String, int[]> statsByLanguage = descriptionStatsByLanguage.get(statBucket);
		if (statsByLanguage == null) {
			statsByLanguage = new HashMap<>();
			descriptionStatsByLanguage.put(statBucket, statsByLanguage);
		}
		
		int[] descCounts = statsByLanguage.get(lang);
		if (descCounts == null) {
			descCounts = new int[DATA_WIDTH];
			statsByLanguage.put(lang, descCounts);
		}
		return descCounts;
	}
	
	private void analyzeDescriptions(Concept c, HistoricData datum, int[] counts, int[] inactCounts, int[] cncCounts) throws TermServerScriptException {
		for (Description d : c.getDescriptions()) {
			//If the description is not in the target module, skip it.
			//That said, for a promoted description, we might still see dependent refset members
			//in the extension module, so check in any event.
			analyzeDescriptionHistoricalAssociations(d, datum, counts);
			analyzeDescriptionInactivationIndicators(d, datum, inactCounts, cncCounts);
		} 
	}

	private void analyzeDescriptionHistoricalAssociations(Description d, HistoricData datum, int[] counts) throws TermServerScriptException {
		// Descriptions have historic associations if they refer to other concepts
		for (AssociationEntry a : d.getAssociationEntries()) {
			if (!matchesModuleFilter(a.getModuleId())) {
				continue;
			}
			incrementCounts(a, counts, IDX_TOTAL);
			if (a.isActiveSafely()) {
				countActiveDescriptionHistoricalAssociations(a, datum, counts);
			} else {
				countInactiveDescriptionHistoricalAssociations(a, datum, counts);
			}
		}
	}

	private void countActiveDescriptionHistoricalAssociations(AssociationEntry a, HistoricData datum, int[] counts) throws TermServerScriptException {
		incrementCounts(a, counts, IDX_TOTAL_ACTIVE);
		if (datum == null || !(datum.getDescHistAssocIds().contains(a.getId()) || datum.getDescHistAssocIdsInact().contains(a.getId()))) {
			// Is active, but either the concept is new, or we have not seen this id before (active or inactive), then it's new
			incrementCounts(a, counts, IDX_NEW);
			debugToFile(a, "New");
		} else if (datum.getDescHistAssocIdsInact().contains(a.getId())) {
			// If previously inactive and now active, then it's reactivated
			incrementCounts(a, counts, IDX_REACTIVATED);
			debugToFile(a, "Reactivated");
		} else if (datum.getDescHistAssocIds().contains(a.getId()) && isChangedSinceLastRelease(a)) {
			// Was and is active, yet it has changed since last release, then it's changed
			incrementCounts(a, counts, IDX_CHANGED);
			debugToFile(a, "Changed");
		}
	}

	private void countInactiveDescriptionHistoricalAssociations(AssociationEntry a, HistoricData datum, int[] counts) throws TermServerScriptException {
		if (datum == null || !(datum.getDescHistAssocIds().contains(a.getId()) || datum.getDescHistAssocIdsInact().contains(a.getId()))) {
			// Is inactive, but either the concept is new, or we have not seen this id before (active or inactive), then it's new inactive
			incrementCounts(a, counts, IDX_NEW_INACTIVE);
			debugToFile(a, "New Inactive");
		} else if (datum.getDescHistAssocIds().contains(a.getId())) {
			// If previously active and now inactive, then it's inactivated
			incrementCounts(a, counts, IDX_INACTIVATED);
			debugToFile(a, "Inactivated");
		} else if (datum.getDescHistAssocIdsInact().contains(a.getId()) && isChangedSinceLastRelease(a)) {
			// Was and is inactive, yet it has changed since last release, then it's changed inactive
			incrementCounts(a, counts, IDX_CHANGED_INACTIVE);
			debugToFile(a, "Changed Inactive");
		}
	}

	private void analyzeDescriptionInactivationIndicators(Description d, HistoricData datum, int[] inactCounts, int[] cncCounts) throws TermServerScriptException {
		// Descriptions can also have inactivation indicators if their concept is inactive
		for (InactivationIndicatorEntry i : d.getInactivationIndicatorEntries()) {
			if (!matchesModuleFilter(i.getModuleId())) {
				continue;
			}
			// Are we writing our results to the default tab, or specific to CNC?
			int[] thisInactTab = i.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT) ? cncCounts: inactCounts;
			incrementCounts(i, thisInactTab, IDX_TOTAL);
			if (i.isActiveSafely()) {
				countActiveDescriptionInactivationIndicators(i, datum, thisInactTab);
			} else {
				countInactiveDescriptionInactivationIndicators(i, datum, thisInactTab);
			}
		}
	}

	private void countInactiveDescriptionInactivationIndicators(InactivationIndicatorEntry i, HistoricData datum, int[] counts) throws TermServerScriptException {
		if (datum == null
				|| !(datum.getDescInactivationIds().contains(i.getId())
				|| datum.getDescInactivationIdsInact().contains(i.getId()))) {
			// Is inactive, but either the concept is new, or we have not seen this id before (active or inactive), then it's new inactive
			incrementCounts(i, counts, IDX_NEW_INACTIVE);
			debugToFile(i, "New Inactive");
		} else if (datum.getDescInactivationIds().contains(i.getId())) {
			// If previously active and now inactive, then it's inactivated
			incrementCounts(i, counts, IDX_INACTIVATED);
			debugToFile(i, "Inactivated");
		} else if (datum.getDescInactivationIdsInact().contains(i.getId())
				&& isChangedSinceLastRelease(i)) {
			// Was and is inactive, yet it has changed since last release, then it's changed inactive
			incrementCounts(i, counts, IDX_CHANGED_INACTIVE);
			debugToFile(i, "Changed Inactive");
		}
	}

	private void countActiveDescriptionInactivationIndicators(InactivationIndicatorEntry i, HistoricData datum, int[] counts) throws TermServerScriptException {
		incrementCounts(i, counts, IDX_TOTAL_ACTIVE);
		if (datum == null
				|| !(datum.getDescInactivationIds().contains(i.getId())
				|| datum.getDescInactivationIdsInact().contains(i.getId()))) {
			// Is active, but either the concept is new, or we have not seen this id before (active or inactive), then it's new
			incrementCounts(i, counts, IDX_NEW);
			debugToFile(i, "New");
		} else if (datum.getDescInactivationIdsInact().contains(i.getId())) {
			// If previously inactive and now active, then it's reactivated
			incrementCounts(i, counts, IDX_REACTIVATED);
			debugToFile(i, "Reactivated");
		} else if (datum.getDescInactivationIds().contains(i.getId()) && isChangedSinceLastRelease(i)) {
			// Was and is active, yet it has changed since last release, then it's changed
			incrementCounts(i, counts, IDX_CHANGED);
			debugToFile(i, "Changed");
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
		boolean conceptAffected = false;

		for (Component component : components) {
			if (!matchesModuleFilter(component.getModuleId())) {
				continue;
			}

			incrementCounts(component, counts, IDX_TOTAL);
			debugToFile(component, "Total");

			//Was the component present in the previous data?
			boolean previouslyExistedActive = ids != null && ids.contains(component.getId());
			boolean previouslyExistedInactive = idsInactive != null && idsInactive.contains(component.getId());

			if (component.isActiveSafely()) {
				incrementCounts(component, counts, IDX_TOTAL_ACTIVE);

				if (!(previouslyExistedActive || previouslyExistedInactive)) {
					incrementCounts(component, counts, IDX_NEW);
					debugToFile(component, "New");
					conceptAffected = true;

					if (isNewConcept) {
						//This component is new because it was created as part of a new concept
						//so it's not been 'added' as such.  Well, we might want to count additions
						//to existing concepts separately.
						incrementCounts(component, counts, IDX_NEW_NEW);
						debugToFile(component, "NewNew");
					}

					if (component instanceof InactivationIndicatorEntry inactivationIndicatorEntry) {
						incrementInactivationReason(counts, inactivationIndicatorEntry.getInactivationReasonId());
					}
				} else if (previouslyExistedInactive) {
					incrementCounts(component, counts, IDX_REACTIVATED);
					debugToFile(component, "Reactivated");
					conceptAffected = true;
				} else if (previouslyExistedActive && isChangedSinceLastRelease(component)) {
					incrementCounts(component, counts, IDX_CHANGED);
					debugToFile(component, "Changed");
					conceptAffected = true;
				}
			} else {
				if (!(previouslyExistedActive || previouslyExistedInactive)) {
					incrementCounts(component, counts, IDX_NEW_INACTIVE);
					debugToFile(component, "New Inactive");
					conceptAffected = true;
				} else if (previouslyExistedActive) {
					incrementCounts(component, counts, IDX_INACTIVATED);
					debugToFile(component, "Inactivated");
					conceptAffected = true;
				} else if (previouslyExistedInactive && isChangedSinceLastRelease(component)) {
					incrementCounts(component, counts, IDX_CHANGED_INACTIVE);
					debugToFile(component, "Changed Inactive");
					conceptAffected = true;
				}
			}
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
		if (component instanceof RefsetMember refsetMember) {
			getRefsetData(refsetMember.getRefsetId())[idx]++;
		}
		//If this component is a description, then also increment our descriptions by language summary
		if (component instanceof Description description) {
			getDescriptionByLanguageArray(description.getType(), description.getLang())[idx]++;
		}
	}

	private boolean matchesModuleFilter(String moduleId) {
		return moduleFilter == null || moduleFilter.contains(moduleId);
	}

	private void debugToFile(Component c, String statType) throws TermServerScriptException {
		// Only debug if we enable it (for testing really).
		if (!DEBUG_TO_FILE
				|| !typesToDebugToFile.contains(c.getComponentType())) {
			return;
		}
		
		if (!refsetsToDebugToFile.isEmpty()
				&& c instanceof RefsetMember rm
				&& !refsetsToDebugToFile.contains(rm.getRefsetId())) {
			return;
		}
		
		try {
			//If this is our first write, do not append.
			boolean append = true;
			if (debugFile == null) {
				debugFile = new File("summary_component_debug.txt");
				append = false;
			}
			String line = c.getId() + TAB + statType + "\n";
			FileUtils.writeStringToFile(debugFile, line, UTF_8, append);
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
			default:
				counts[IDX_INACT_OTHER]++;
				break;
		}
	}

	private void outputResults() throws TermServerScriptException {
		Concept totalConcept = new Concept("","Total");
		totals = new int[MAX_REPORT_TABS][DATA_WIDTH];
		for (Concept hierarchy : topLevelHierarchies) {
			int[][] summaryData = summaryDataMap.get(hierarchy);
			if (summaryData != null) {
				for (int idxTab = 0; idxTab < MAX_REPORT_TABS - 2; idxTab++) {
					report(idxTab, hierarchy, summaryData[idxTab]);
					for (int idxMovement = 0; idxMovement < DATA_WIDTH; idxMovement++) {
						totals[idxTab][idxMovement] += summaryData[idxTab][idxMovement];
					}
				}
			}
		}
		
		//Refset data is not broken down by major hierarchy
		//Split into each type of refset with sub totals
		associationSubTotals = outputRefsetData(TAB_REFSET, "association", totals);
		languageSubTotals = outputRefsetData(TAB_REFSET, "language", totals);
		indicatorSubTotals = outputRefsetData(TAB_REFSET, "indicator", totals);
		
		outputDescriptionByLanguage(TAB_DESC_BY_LANG, totals);
		
		for (int idxTab = 0; idxTab < MAX_REPORT_TABS; idxTab++) {
			report(idxTab, totalConcept, totals[idxTab]);
		}
	}
	
	private int[] outputRefsetData(int tabIdx, String filter, int[][] totals) throws TermServerScriptException {
		int[] subTotals = new int[DATA_WIDTH];
		Concept subTotalConcept = new Concept("","  SubTotal");
		for (Map.Entry<String, int[]> entry : refsetDataMap.entrySet()) {
			Concept refset = gl.getConcept(entry.getKey());
			if (refset.getFsn().contains(filter)) {
				report(tabIdx, refset, entry.getValue());
				for (int idxMovement = 0; idxMovement < DATA_WIDTH; idxMovement++) {
					subTotals[idxMovement] += entry.getValue()[idxMovement];
					totals[tabIdx][idxMovement] +=  entry.getValue()[idxMovement];
				}
			}
		}
		report(tabIdx, subTotalConcept, subTotals);
		report(tabIdx, "");
		return subTotals;
	}
	
	private void outputDescriptionByLanguage(int tabIdx, int[][] totals) throws TermServerScriptException {
		for (Map.Entry<String, Map<String, int[]>> descTypeEntry : descriptionStatsByLanguage.entrySet()) {
			int[] subTotals = new int[DATA_WIDTH];
			String descType = descTypeEntry.getKey();
			for (Map.Entry<String, int[]> entry : descTypeEntry.getValue().entrySet()) {
				String lang = entry.getKey();
				report(tabIdx, descType + " - " + lang, entry.getValue());
				for (int idxMovement = 0; idxMovement < DATA_WIDTH; idxMovement++) {
					subTotals[idxMovement] += entry.getValue()[idxMovement];
					totals[tabIdx][idxMovement] +=  entry.getValue()[idxMovement];
				}
			}
			report(tabIdx, "Subtotal", subTotals);
			report(tabIdx, "");
		}
	}

	protected void report(int idxTab, Concept c, int[] data) throws TermServerScriptException {
		super.report(idxTab, c, getReportData(idxTab, data));
		countIssue(c);
	}
	
	protected void report(int idxTab, String lang, int[] data) throws TermServerScriptException {
		super.report(idxTab, "", "", lang, getReportData(idxTab, data));
	}

	private static Map<Integer, List<Integer>> getSheetFieldsMap() {
		// set up the report sheets and the fields they contain
		final Map<Integer, List<Integer>> sheetFieldsByIndex = new HashMap<>();

		sheetFieldsByIndex.put(TAB_CONCEPTS, new LinkedList<>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_MOVED_MODULE, IDX_CHANGED_INACTIVE, IDX_NEW_SD, IDX_NEW_P, IDX_TOTAL_ACTIVE, IDX_TOTAL, IDX_PROMOTED)));

		Stream.of(TAB_DESCS, TAB_RELS, TAB_CD, TAB_AXIOMS, TAB_TEXT_DEFN).forEach(index ->
			sheetFieldsByIndex.put(index, new LinkedList<>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE, IDX_TOTAL_ACTIVE, IDX_TOTAL, IDX_CONCEPTS_AFFECTED)))
		);
		
		Stream.of(TAB_DESC_CNC, TAB_DESC_INACT, TAB_REFSET, TAB_DESC_BY_LANG).forEach(index ->
			sheetFieldsByIndex.put(index, new LinkedList<>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE, IDX_TOTAL_ACTIVE, IDX_TOTAL)))
		);

		sheetFieldsByIndex.put(TAB_INACT_IND, new LinkedList<>(Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE,
				IDX_INACT_AMBIGUOUS, IDX_INACT_MOVED_ELSEWHERE,	IDX_INACT_CONCEPT_NON_CURRENT, IDX_INACT_DUPLICATE, IDX_INACT_ERRONEOUS, IDX_INACT_INAPPROPRIATE, IDX_INACT_LIMITED,
				IDX_INACT_OUTDATED, IDX_INACT_PENDING_MOVE, IDX_INACT_NON_CONFORMANCE, IDX_INACT_NOT_EQUIVALENT, IDX_INACT_OTHER, IDX_TOTAL_ACTIVE, IDX_CONCEPTS_AFFECTED)));

		Stream.of(TAB_LANG, TAB_HIST).forEach(index ->
			sheetFieldsByIndex.put(index, new LinkedList<>((Arrays.asList(IDX_NEW, IDX_CHANGED, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_NEW_NEW, IDX_CHANGED_INACTIVE, IDX_TOTAL_ACTIVE, IDX_CONCEPTS_AFFECTED))))
		);
		
		sheetFieldsByIndex.put(TAB_QI, new LinkedList<>(Arrays.asList(IDX_NEW_IN_QI_SCOPE, IDX_GAINED_ATTRIBUTES, IDX_LOST_ATTRIBUTES, IDX_INACTIVATED, IDX_TOTAL_ACTIVE)));

		sheetFieldsByIndex.put(TAB_DESC_HIST, new LinkedList<>(Arrays.asList(IDX_NEW, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_TOTAL_ACTIVE, IDX_TOTAL)));

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
		data[1]  = sum(TAB_CONCEPTS, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE, IDX_MOVED_MODULE);
		data[2]  = sum(TAB_CONCEPTS, IDX_TOTAL);
		data[3]  = sum(TAB_DESCS, IDX_NEW_NEW);
		data[4]  = minusPlus(TAB_DESCS, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[5]  = sum(TAB_DESCS, IDX_TOTAL);
		data[6]  = sum(TAB_TEXT_DEFN, IDX_NEW_NEW);
		data[7]  = minusPlus(TAB_TEXT_DEFN, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[8]  = sum(TAB_TEXT_DEFN, IDX_TOTAL);
		data[9]  = sum(TAB_LANG, IDX_NEW_NEW);
		data[10] = minusPlus(TAB_LANG, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[11] = sum(TAB_LANG, IDX_TOTAL);
		data[12] = sum(TAB_AXIOMS, IDX_NEW_NEW);
		data[13] = minusPlus(TAB_AXIOMS, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[14] = sum(TAB_AXIOMS, IDX_TOTAL);
		data[15] = "0";
		data[16] = "0";
		data[17] = "1024719";
		data[18] = sum(TAB_RELS, IDX_NEW_NEW);
		data[19] = minusPlus(TAB_RELS, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE);
		data[20] = sum(TAB_RELS, IDX_TOTAL);
		data[21] = sum(TAB_CD, IDX_NEW_NEW);
		data[22] = minusPlus(TAB_CD, IDX_NEW, IDX_NEW_NEW, IDX_CHANGED, IDX_CHANGED_INACTIVE, IDX_INACTIVATED, IDX_REACTIVATED, IDX_NEW_INACTIVE);
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
			LOGGER.warn("Cannot remove time code from {}", input);
		}

		// Return original as default
		return input;
	}

}
