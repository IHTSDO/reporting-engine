package org.ihtsdo.termserver.scripting.reports.managed_service;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.release.HistoricDataUser;
import org.ihtsdo.termserver.scripting.reports.release.HistoricStatsGenerator;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.DerivativeHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExtensionImpactReport extends HistoricDataUser implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtensionImpactReport.class);

	private static final String INTERNATIONAL_RELEASE = "Proposed International Release Archive";
	private static final String ECL_FILTER = "ECL Filter (optional)";
	private static final String COMMON_HEADINGS = "SCTID, FSN, SemTag,";

	private static final boolean RUN_INTEGRITY_CHECKS = true;  //Make false locally if required

	private String incomingDataKey;
	private Map<String, HistoricData> incomingData;
	
	private Map<Concept, Set<Concept>> usedInStatedModellingMap; 
	private Map<Concept, Set<Concept>> usedAsStatedParentMap;
	private Map<Concept, String> historicalAssociationStrMap;
	
	private String[][] columnNames;  //Used for both column names, and to track totals
	private String proposedUpgrade;
	private String ecl;
	private DerivativeHelper derivativeHelper;
	private Set<Concept> conceptsOfInterest;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(INTERNATIONAL_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20250901T120000Z.zip");
		params.put(ECL_FILTER, "^ 1303957004 |Nutrition Care Process Terminology reference set (foundation metadata concept)| or ^ 1157358007 |International Classification for Nursing Practice reference set (foundation metadata concept)| or ^ 816080008 |International Patient Summary (foundation metadata concept)|");
		TermServerScript.run(ExtensionImpactReport.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INTERNATIONAL_RELEASE).withType(JobParameter.Type.RELEASE_ARCHIVE).withOptions(INT)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Extension Impact Report")
				.withDescription("This report estimates the size of the changes that will need to be made when the extension is upgraded to the latest international edition.  Leave the parameter blank for a preview of impact of current unpublished MAIN.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		origProject = run.getProject();
		summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
		
		if (!getProject().getBranchPath().startsWith("MAIN/SNOMEDCT-")) {
			throw new TermServerScriptException("Cannot work with '" + run.getProject() + "'. This report can only be run against an extension. ");
		}
		
		if (!StringUtils.isEmpty(getJobRun().getTask())) {
			throw new TermServerScriptException("This report cannot be run against tasks");
		}

		if (!RUN_INTEGRITY_CHECKS) {
			getArchiveManager().setRunIntegrityChecks(false);
		}
	}
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		boolean compareTwoSnapshots = false; 
		previousTransitiveClosureNeeded = false;
		LOGGER.info("International Release data being imported, wiping Graph Loader for safety.");
		Project previousProject = project.clone();
		SnapshotGenerator.setSkipSave(true); //This takes a copy of the graph in memory, so avoid for this expensive report.

		if (StringUtils.isEmpty(getJobRun().getParamValue(INTERNATIONAL_RELEASE))) {
			Branch branch = tsClient.getBranch("MAIN");
			project.setBranchPath("MAIN");
			project.setKey("MAIN");
			project.setMetadata(branch.getMetadata());
			proposedUpgrade = "MAIN";
		} else {
			currentPositionProjectKey = getJobRun().getParamValue(INTERNATIONAL_RELEASE);
			project.setKey(currentPositionProjectKey);
			project.setBranchPath(currentPositionProjectKey);
			proposedUpgrade = currentPositionProjectKey;
		}

		try {
			incomingDataKey = project.getKey();
			ArchiveManager mgr = getArchiveManager();
			mgr.loadSnapshot(fsnOnly);
			HistoricStatsGenerator statsGenerator = new HistoricStatsGenerator(this);
			statsGenerator.runJob();
			//Generate a map of historical associations now, since they won't be available in the target location
			LOGGER.info("Generating map of historical associations");
			historicalAssociationStrMap = gl.getAllConcepts().stream()
							.filter(c -> !c.isActive())
							.collect(Collectors.toMap(c -> c, c -> {
								try {
									return SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
								} catch (TermServerScriptException e) {
									throw new IllegalStateException(e);
								}
							}));
			mgr.reset();
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException("Historic Data Generation failed due to " + e.getMessage(), e);
		}
		currentPositionProjectKey = previousProject.getKey();
		project = previousProject;
		loadCurrentPosition(compareTwoSnapshots, fsnOnly);
	}

	@Override
	protected void loadCurrentPosition(boolean compareTwoSnapshots, boolean fsnOnly) throws TermServerScriptException {
		thisDependency = project.getMetadata().getDependencyPackage();
		LOGGER.info("Setting dependency archive: {}", thisDependency);
		setDependencyArchive(thisDependency);
		super.loadCurrentPosition(false, fsnOnly);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		//Need to set the original project back, otherwise it'll get filtered
		//out by the security of which projects a user can see
		if (getJobRun() != null) {
			getJobRun().setProject(origProject);
		}

		ecl = jobRun.getParamValue(ECL_FILTER);
		
		columnNames = new String[][] {	{"Has Inactivated Stated Parent", "Inactivated Concept Used As Stated Parent", "Has Inactivated Stated Attribute", "Inactivated Concept Used In Stated Modelling", "Has Inactivated Inferred Parent", "Inactivated Concept Used As Inferred Parent", "Inactivated with Extension Axiom"},
										{"New Concept Requires Translation", "Updated FSN Requires Translation", "Updated FSN No Current Translation", "Translated Concept Inactivated - Replacement Requires Translation", "Translated Concept Inactivated - No Replacement Specified"}};

		String[] columnHeadings = new String[] {"Summary Item, Count",
				COMMON_HEADINGS + formColumnNames(columnNames[0], true),
				COMMON_HEADINGS + "Impact,Affected Concept,Historical Associations",
				COMMON_HEADINGS + formColumnNames(columnNames[1], false),
				COMMON_HEADINGS + "Impact Information,Existing FSN,Translated FSN,Translated PT",
				COMMON_HEADINGS + "Axioms Affected"};
		
		String[] tabNames = new String[]{"Summary Counts",  //PRIMARY
				"Inactivations",   //SECONDARY
				"Inactivation Detail",  //TERTIARY
				"Translations",   //QUAD
				"Translation Detail", //QUINARY
				"Non-core Axioms Detail" //SENARY_REPORT
		};
		super.postInit(GFOLDER_RELEASE_STATS, tabNames, columnHeadings, false);

		derivativeHelper = new DerivativeHelper(this);
		derivativeHelper.initialise();
	}
	
	private String formColumnNames(String[] columnNames, boolean includeExamples) {
		StringBuilder header = new StringBuilder();
		boolean isFirst = true;
		int pairCount = 0;
		for (String columnName : columnNames) {
			if (!isFirst) {
				header.append(",");
			} else {
				isFirst = false;
			}
			header.append(columnName);
			pairCount++;
			
			if (includeExamples && pairCount == 2) {
				header.append(",Example");
				pairCount = 0;
			}
		}
		header.append(", ,");
		return header.toString();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		//We've always loaded historic data as 'prev' but in this case, we're looking the release that
		//we're about to upgrade to.  So we'll give that a more appropriate name
		LOGGER.info("Loading Previous Data");
		incomingData = loadData(incomingDataKey, true);
		
		LOGGER.info("Populating map of all concepts used in stated modelling");
		populateStatedModellingMap();

		if (!StringUtils.isEmpty(ecl)) {
			LOGGER.info("Finding concepts matching ECL: {}", ecl);
			findConceptsOfInterest(ecl);
		} else {
			LOGGER.info("No ECL specified, all concepts in scope will be considered");
		}
		
		//Work through the top level hierarchies
		List<Concept> topLevelHierarchies = SnomedUtils.sort(ROOT_CONCEPT.getDescendants(IMMEDIATE_CHILD));
		for (Concept topLevelConcept : topLevelHierarchies) {
			LOGGER.info("Processing - {}", topLevelConcept);
			Set<String> thisHierarchy = getHierarchy(topLevelConcept);
			reportInactivations(topLevelConcept, thisHierarchy, columnNames[0]);
			reportTranslations(topLevelConcept, thisHierarchy, columnNames[1]);
		}
		
		//We can now populate all the of the total columns
		writeTotalRow(SECONDARY_REPORT, columnNames[0], true);
		writeTotalRow(QUATERNARY_REPORT, columnNames[1], false);
	}

	private void reportInactivations(Concept topLevelConcept, Set<String> thisHierarchy, String[] summaryNames) throws TermServerScriptException {
		LOGGER.info("Reporting Inactivations");
		int[] inactivationCounts = new int[7];
		String[] examples = new String[4];
		
		Set<Concept> noInScopeDescendentsCache = new HashSet<>();
		Set<Concept> yesInScopeDescendentsCache = new HashSet<>();
		for (String sctId : thisHierarchy) {
			int[] theseInactivationCounts = reportInactivations(sctId, summaryNames, noInScopeDescendentsCache, yesInScopeDescendentsCache, examples);
			for (int i = 0; i < theseInactivationCounts.length; i++) {
				inactivationCounts[i] += theseInactivationCounts[i];
			}
		}

		report(SECONDARY_REPORT, topLevelConcept,
				inactivationCounts[0],
				inactivationCounts[1], examples[0],
				inactivationCounts[2],
				inactivationCounts[3], examples[1],
				inactivationCounts[4],
				inactivationCounts[5], examples[2],
				inactivationCounts[6], examples[3]);
	}

	private int[] reportInactivations(String sctId, String[] summaryNames, Set<Concept> noInScopeDescendentsCache, Set<Concept> yesInScopeDescendentsCache, String[] examples) throws TermServerScriptException {
		int[] emptyCounts = new int[7];

		Concept currentConcept = gl.getConcept(sctId, false, false);  //Don't create or validate
		//If this concept is already inactive, or doesn't yet exist we don't need to count it
		if (currentConcept == null || !currentConcept.isActive()) {
			return emptyCounts;
		}

		//Or if it's not in the International Edition or still active in the incoming data
		HistoricData datum = incomingData.get(sctId);
		if (datum == null || datum.isActive()) {
			return emptyCounts;
		}

		InactivationStats inactivationStats = new InactivationStats();

		countInactivatedConceptsUsedAsStatedParents(currentConcept, summaryNames, examples, inactivationStats);
		countInactivatedConceptsUsedInStatedModelling(currentConcept, summaryNames, examples, inactivationStats);
		countInactivatedConceptsUsedAsInferredParents(currentConcept, summaryNames, examples, inactivationStats, noInScopeDescendentsCache, yesInScopeDescendentsCache);
		countInactivatedConceptsWithNonCoreAxioms(currentConcept, summaryNames, examples, inactivationStats);

		return new int[] {
				inactivationStats.hasInactivatedStatedParent,
				inactivationStats.inactivatedConceptUsedAsStatedParent,
				inactivationStats.hasInactivatedStatedAttribute,
				inactivationStats.inactivatedConceptUsedInStatedModelling,
				inactivationStats.hasInactivatedInferredParent,
				inactivationStats.inactivatedConceptUsedAsInferredParent,
				inactivationStats.hasNonCoreAxioms
		};
	}

	private void countInactivatedConceptsUsedAsStatedParents(Concept currentConcept, String[] summaryNames, String[] examples, InactivationStats inactivationStats) throws TermServerScriptException {
		boolean isCause = isConceptOfInterest(currentConcept.getId());

		//The IMPACT is not the count of International Concepts involved,
		//but how many _extension_ concepts each inactivated INT concept is used in.
		boolean hasImpact = false;
		Set<Concept> usedIn = usedAsStatedParentMap.get(currentConcept);
		if (usedIn != null && !usedIn.isEmpty()) {
			String histAssocStr = historicalAssociationStrMap.get(currentConcept);
			for (Concept detail : usedIn) {
				if (isCause || isConceptOfInterest(detail.getId())) {
					hasImpact = true;
					inactivationStats.hasInactivatedStatedParent++;
					incrementSummaryInformation(summaryNames[0]);
					examples[0] = currentConcept + "\nParent of : " + detail;
					//Only report if either the inactivated concept or the extension concept using it is of interest
					report(TERTIARY_REPORT, currentConcept, "Becomes inactive, stated parent of", detail, histAssocStr);
				}
			}
			if (hasImpact) {
				inactivationStats.inactivatedConceptUsedAsStatedParent++;
				incrementSummaryInformation(summaryNames[1]);
			}
		}
	}

	private void countInactivatedConceptsUsedInStatedModelling(Concept currentConcept, String[] summaryNames, String[] examples, InactivationStats inactivationStats) throws TermServerScriptException {
		boolean isCause = isConceptOfInterest(currentConcept.getId());

		boolean hasImpact = false;
		Set<Concept> usedIn = usedInStatedModellingMap.get(currentConcept);
		if (usedIn != null && !usedIn.isEmpty()) {
			String histAssocStr = historicalAssociationStrMap.get(currentConcept);
			for (Concept detail : usedIn) {
				if (isCause || isConceptOfInterest(detail.getId())) {
					hasImpact = true;
					inactivationStats.hasInactivatedStatedAttribute++;
					incrementSummaryInformation(summaryNames[2]);
					examples[1] = currentConcept + "\nUsed in : " + usedIn + "\n" + detail.toExpression(CharacteristicType.STATED_RELATIONSHIP);
					report(TERTIARY_REPORT, currentConcept, "Becomes inactive, used in modelling of", detail, histAssocStr);
				}
			}
			if (hasImpact) {
				inactivationStats.inactivatedConceptUsedInStatedModelling++;
				incrementSummaryInformation(summaryNames[3]);
			}
		}
	}

	private void countInactivatedConceptsUsedAsInferredParents(Concept currentConcept, String[] summaryNames, String[] examples, InactivationStats inactivationStats, Set<Concept> noInScopeDescendentsCache, Set<Concept> yesInScopeDescendentsCache) {
		boolean isCause = isConceptOfInterest(currentConcept.getId());

		//Does this concept have any inScope children?  We'll step this with new code
		//to shortcut finding the complete set and allocating memory for that
		boolean hasImpact = false;
		if (hasInScopeDescendents(currentConcept, noInScopeDescendentsCache, yesInScopeDescendentsCache)) {
			Set<Concept> inferredChildren = currentConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).stream()
					.filter(this::inScope)
					.collect(Collectors.toSet());

			if (!inferredChildren.isEmpty()) {
				for (Concept detail : inferredChildren) {
					if (isCause || isConceptOfInterest(detail.getId())) {
						hasImpact = true;
						inactivationStats.hasInactivatedInferredParent++;
						incrementSummaryInformation(summaryNames[4]);
						examples[2] = currentConcept + " inferred parent of " + currentConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).iterator().next();
					}
				}
				if (hasImpact) {
					inactivationStats.inactivatedConceptUsedAsInferredParent++;
					incrementSummaryInformation(summaryNames[5]);
				}
			}
		}
	}

	private void countInactivatedConceptsWithNonCoreAxioms(Concept currentConcept, String[] summaryNames, String[] examples, InactivationStats inactivationStats) throws TermServerScriptException {
		boolean isCause = isConceptOfInterest(currentConcept.getId());

		//Do we have non-core axioms on this core concept?
		if (isCause && hasNonCoreAxioms(currentConcept)) {
			inactivationStats.hasNonCoreAxioms++;
			examples[3] = currentConcept.toString();
			incrementSummaryInformation(summaryNames[6]);
			//And since we're not expecting very many of these, output each one on a detail tab
			report(SENARY_REPORT, currentConcept, getFirstNonCoreAxiom(currentConcept));
		}
	}

	private void reportTranslations(Concept topLevelConcept, Set<String> thisHierarchy, String[] summaryNames) throws TermServerScriptException {
		LOGGER.info("Reporting Translations Required");
		int[] translationCounts = new int[5];

		Set<String> conceptReplacementSeen = new HashSet<>();
		
		for (String sctId : thisHierarchy) {
			int[] theseTranslationCounts = countTranslations(sctId, summaryNames, conceptReplacementSeen);
			for (int i = 0; i < theseTranslationCounts.length; i++) {
				translationCounts[i] += theseTranslationCounts[i];
			}
		}
		report(QUATERNARY_REPORT, topLevelConcept,
				translationCounts[0],
				translationCounts[1],
				translationCounts[2],
				translationCounts[3],
				translationCounts[4]);
	}

	private int[] countTranslations(String sctId, String[] summaryNames, Set<String> conceptReplacementSeen) throws TermServerScriptException {
		TranslationStats translationStats = new TranslationStats();

		HistoricData datum = incomingData.get(sctId);
		Concept currentConcept = gl.getConcept(sctId, false, false);  //Don't create or validate

		if (currentConcept == null) {
			//No need to check scope, this can only have come from the international edition
			countNewConcepts(datum, summaryNames, translationStats);
		} else if (SnomedUtils.isInternational(currentConcept)) {
			if (datum == null) {
				throw new TermServerScriptException(sctId + " is known to extension, is considered International and yet is not known to proposed update package.  Check date specified.  It must be in advance of current extension upgrade point");
			}
			compareCurrentConceptWithPreviousState(currentConcept, datum, summaryNames, translationStats, conceptReplacementSeen);
		}
		return new int[] {
			translationStats.newConceptCount,
			translationStats.changedFSNCount,
			translationStats.changedFSNCountNoCurrent,
			translationStats.translatedInactivatedCount,
			translationStats.translatedInactivatedWithoutReplacement
		};
	}

	private void countNewConcepts(HistoricData datum, String[] summaryNames, TranslationStats translationStats) throws TermServerScriptException {
		String sctId = String.valueOf(datum.getConceptId());

		if (isConceptOfInterest(sctId)) {
			translationStats.newConceptCount++;
			incrementSummaryInformation(summaryNames[0]);
			String fsn = datum.getFsn();
			String semTag = SnomedUtilsBase.deconstructFSN(fsn)[1];
			report(QUINARY_REPORT, sctId, fsn, semTag, "New Concept", "", "", "");
		}
	}

	private void compareCurrentConceptWithPreviousState(Concept currentConcept, HistoricData datum, String[] summaryNames, TranslationStats translationStats, Set<String> conceptReplacementSeen) throws TermServerScriptException {
		checkForChangedFSN(currentConcept, datum, translationStats, summaryNames);

		//Report translated concepts that have been inactivated where the replacement has not been translated
		if (!datum.isActive() &&
				currentConcept.isActiveSafely() &&
				hasTranslation(currentConcept)) {
			//Can't use association entries because of course _this_ snapshot doesn't know
			//about the inactivation.  Pull it from the datum instead
			if (datum.getHistAssocTargets() == null) {
				if (isConceptOfInterest(currentConcept.getId())) {
					translationStats.translatedInactivatedWithoutReplacement++;
					incrementSummaryInformation(summaryNames[4]);
					String fsn = datum.getFsn();
					String semTag = SnomedUtilsBase.deconstructFSN(fsn)[1];
					report(QUINARY_REPORT, datum.getConceptId(), fsn, semTag, "Translated concept has been inactivated but no replacement specified", currentConcept.getFsn(), getTranslatedFsn(currentConcept), getTranslatedPreferredSynonym(currentConcept));
				}
			} else {
				checkHistoricalAssociationsForReplacements(datum, translationStats, summaryNames, conceptReplacementSeen);
			}
		}
	}

	private void checkHistoricalAssociationsForReplacements(HistoricData datum, TranslationStats translationStats, String[] summaryNames, Set<String> conceptReplacementSeen) throws TermServerScriptException {
		boolean isConceptOfInterest = isConceptOfInterest(String.valueOf(datum.getConceptId()));

		for (String histAssocTarget : datum.getHistAssocTargets()) {
			//Only count a given replacement once
			if (!conceptReplacementSeen.contains(histAssocTarget) && (isConceptOfInterest || isConceptOfInterest(histAssocTarget))) {
				Concept targetConcept = gl.getConcept(histAssocTarget, false, false);
				//If we don't have this concept then it definitely won't have a translation
				if (targetConcept == null || !hasTranslation(targetConcept)) {
					translationStats.translatedInactivatedCount++;
					incrementSummaryInformation(summaryNames[3]);
					//We might not know about this concept in this project, but it should be known to the incoming data
					String parentConcept = datum.getConceptId() + " |" + datum.getFsn() + "|";
					HistoricData incomingDatum = incomingData.get(histAssocTarget);
					String histFsn = incomingDatum.getFsn();
					String histSemTag = SnomedUtilsBase.deconstructFSN(histFsn)[1];
					report(QUINARY_REPORT, histAssocTarget, histFsn, histSemTag, "Concept is untranslated replacement for " + parentConcept, datum.getFsn(), "", "");
				}
				conceptReplacementSeen.add(histAssocTarget);
			}
		}
	}

	private void checkForChangedFSN(Concept currentConcept, HistoricData datum, TranslationStats translationStats, String[] summaryNames) throws TermServerScriptException {
		//Has the FSN changed from what's currently here?
		if (!currentConcept.getFsn().equals(datum.getFsn()) && isConceptOfInterest(currentConcept.getId())) {
			String fsn = datum.getFsn();
			String semTag = SnomedUtilsBase.deconstructFSN(fsn)[1];
			if (hasTranslation(currentConcept)) {
				report(QUINARY_REPORT, datum.getConceptId(), fsn, semTag, "Existing translated FSN has been replaced", currentConcept.getFsn(), getTranslatedFsn(currentConcept), getTranslatedPreferredSynonym(currentConcept));
				translationStats.changedFSNCount++;
				incrementSummaryInformation(summaryNames[1]);
			} else {
				report(QUINARY_REPORT, datum.getConceptId(), fsn, semTag, "Existing untranslated FSN has been replaced", currentConcept.getFsn(), "", "");
				translationStats.changedFSNCountNoCurrent++;
				incrementSummaryInformation(summaryNames[2]);
			}
		}
	}

	private void writeTotalRow(int tabNum, String[] columnNames, boolean includeExamples) throws TermServerScriptException {
		String[] data = new String[columnNames.length];
		if (includeExamples) {
			data = new String[columnNames.length + 3];
		}
		int idx = 0;
		for (String columnName : columnNames) {
			int count = getSummaryInformationInt(columnName);
			countIssue(null, count);
			data[idx++] = Integer.toString(count);
			if (includeExamples && idx >= 2 && (idx+1) % 3 == 0) {
				data[idx++] = "N/A";
			}
		}
		report(tabNum, "");
		report(tabNum, "", "", "Total:", data);
	}

	private Set<String> getHierarchy(Concept topLevelConcept) throws TermServerScriptException {
		//Form the top level set as the sum of current hierarchy and what's in the incoming data
		Set<String> hierarchy = topLevelConcept.getDescendants(NOT_SET).stream()
				.map(c -> c.getId())
				.collect(Collectors.toSet());
		
		//Now add in what we see in the proposed upgrade version
		for (Map.Entry<String, HistoricData> entry : incomingData.entrySet()) {
			if (entry.getValue().getHierarchy() != null
					&& entry.getValue().getHierarchy().equals(topLevelConcept.getId())) {
				hierarchy.add(entry.getKey());
			}
		}
		return hierarchy;
	}

	private void findConceptsOfInterest(String ecl) throws TermServerScriptException {
		conceptsOfInterest = new HashSet<>();
		for (Concept refset : derivativeHelper.getDerivativeRefsetConcepts()) {
			if (ecl.contains(refset.getId())) {
				EclCache eclCache = derivativeHelper.getEclCacheForDerivativeRefset(refset.getId());
				conceptsOfInterest.addAll(eclCache.findConcepts(ecl));
			}
		}
	}

	private void populateStatedModellingMap() {
		usedInStatedModellingMap = new HashMap<>();
		usedAsStatedParentMap = new HashMap<>();
		//I'm not calling getRelationships(Active, Stated) because that causes memory to be allocated
		//and we're going through A LOT of loops here, so just test values
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely() && inScope(c)) {
				populateStatedModellingMap(c);
			}
		}
	}

	private void populateStatedModellingMap(Concept c) {
		for (Relationship r : c.getRelationships()) {
			if (r.isActiveSafely() && r.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP)) {
				if (r.getType().equals(IS_A)) {
					addToMap(usedAsStatedParentMap, r.getType(), c);
					addToMap(usedAsStatedParentMap, r.getTarget(), c);
				} else {
					addToMap(usedInStatedModellingMap, r.getType(), c);
					if (r.isNotConcrete()) {
						addToMap(usedInStatedModellingMap, r.getTarget(), c);
					}
				}
			}
		}
	}

	private void addToMap(Map<Concept, Set<Concept>> map, Concept key, Concept value) {
		Set<Concept> set = map.computeIfAbsent(key, k -> new HashSet<>());
		set.add(value);
	}

	private boolean hasInScopeDescendents(Concept c, Set<Concept> noInScopeDescendentsCache, Set<Concept> yesInScopeDescendentsCache) {
		for (Concept child : c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			//If we already know that this concept has no descendants, then no need to check again
			if (noInScopeDescendentsCache.contains(child)) {
				return false;
			}
			
			if (yesInScopeDescendentsCache.contains(child)) {
				return true;
			}
			
			if (inScope(child) || hasInScopeDescendents(child, noInScopeDescendentsCache, yesInScopeDescendentsCache)) {
				yesInScopeDescendentsCache.add(child);
				return true;
			} else {
				noInScopeDescendentsCache.add(child);
			}
		}
		noInScopeDescendentsCache.add(c);
		return false;
	}
	
	@Override
	protected boolean inScope(Component c) {
		return !SnomedUtils.isInternational(c);
	}
	
	private boolean hasTranslation(Concept c) {
		return c.getDescriptions().stream()
				.anyMatch(d -> !d.getLang().equals("en"));
	}

	private String getTranslatedFsn(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE)) {
			if (!SnomedUtils.isInternational(d)) {
				return d.getTerm();
			}
		}
		return "No translated FSN found for concept " + c.getId();
	}

	private String getTranslatedPreferredSynonym(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
			if (!SnomedUtils.isInternational(d)) {
				return d.getTerm();
			}
		}
		return "No translated preferred synonym found for concept " + c.getId();
	}

	@Override
	protected void recordFinalWords() throws TermServerScriptException {
		report(PRIMARY_REPORT,"Proposed upgrade to", proposedUpgrade);
		SnapshotGenerator.setSkipSave(false); //reset for subsequent reuse

	}

	private boolean hasNonCoreAxioms(Concept c) {
		return c.getAxiomEntries(ActiveState.ACTIVE, false)
				.stream()
				.anyMatch(a -> !SnomedUtils.inModule(a, INTERNATIONAL_MODULES));
	}

	private AxiomEntry getFirstNonCoreAxiom(Concept c) {
		return c.getAxiomEntries(ActiveState.ACTIVE, false)
				.stream()
				.filter(a -> !SnomedUtils.inModule(a, INTERNATIONAL_MODULES))
				.findFirst()
				.orElse(null);
	}

	private boolean isConceptOfInterest(String sctId) {
		return conceptsOfInterest == null || conceptsOfInterest.stream().anyMatch(c -> c.getId().equals(sctId));
	}

	class InactivationStats {
		int hasInactivatedStatedParent = 0;
		int inactivatedConceptUsedAsStatedParent = 0;
		int hasInactivatedStatedAttribute = 0;
		int inactivatedConceptUsedInStatedModelling = 0;
		int hasInactivatedInferredParent = 0;
		int inactivatedConceptUsedAsInferredParent = 0;
		int hasNonCoreAxioms = 0;
	}

	class TranslationStats {
		int newConceptCount = 0;
		int changedFSNCount = 0;
		int changedFSNCountNoCurrent = 0;
		int translatedInactivatedCount = 0;
		int translatedInactivatedWithoutReplacement = 0;
	}

}
