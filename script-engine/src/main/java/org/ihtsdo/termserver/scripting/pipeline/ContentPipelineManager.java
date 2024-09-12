package org.ihtsdo.termserver.scripting.pipeline;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.delta.Rf2ConceptCreator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ContentPipelineManager extends TermServerScript implements ContentPipeLineConstants {

	public static final String CHANGES_SINCE_LAST_ITERATION = "Changes since last iteration";
	public static final String HIGHEST_USAGE_COUNTS = "Highest usage counts";
	public static final String CONTENT_COUNT = "Content counts";
	public static final String FAILED_TO_LOAD = "Failed to load ";
	
	enum RunMode { NEW, INCREMENTAL_DELTA, INCREMENTAL_API}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ContentPipelineManager.class);
	
	protected static final RunMode runMode = RunMode.INCREMENTAL_DELTA;
	
	protected static final int FILE_IDX_CONCEPT_IDS = 7;
	protected static final int FILE_IDX_DESC_IDS = 8;
	protected static final int FILE_IDX_REL_IDS = 9;
	
	protected Map<String, ExternalConcept> externalConceptMap = new HashMap<>();
	protected AttributePartMapManager attributePartMapManager;
	protected Map<String, Part> partMap = new HashMap<>();
	protected Map<String, String> partMapNotes = new HashMap<>();
	protected Map<Part, Set<ExternalConcept>> missingPartMappings = new HashMap<>();
	
	protected Concept scheme;
	protected String namespace;
	protected String externalContentModule;
	protected Rf2ConceptCreator conceptCreator;
	protected int additionalThreadCount = 0;

	private  Map<String, Map<String, Integer>> summaryCountsByCategory = new HashMap<>();

	protected Set<ComponentType> skipForComparison = Set.of(
			ComponentType.INFERRED_RELATIONSHIP,
			ComponentType.LANGREFSET);

	protected List<TemplatedConcept.IterationIndicator> activeIndicators = List.of(
			TemplatedConcept.IterationIndicator.NEW,
			TemplatedConcept.IterationIndicator.UNCHANGED,
			TemplatedConcept.IterationIndicator.MODIFIED,
			TemplatedConcept.IterationIndicator.RESURRECTED);

	protected void ingestExternalContent(String[] args) throws TermServerScriptException {
		try {
			runStandAlone = false;
			getGraphLoader().setExcludedModules(new HashSet<>());
			getArchiveManager().setRunIntegrityChecks(false);
			getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);  //Needed for working out if we're deleteing or inactivating
			init(args);
			loadProjectSnapshot(false);
			postInit();
			//getReportManager().disableTab(getTab(TAB_MODELING_ISSUES));
			//getReportManager().disableTab(getTab(TAB_MAP_ME));
			//getReportManager().disableTab(getTab(TAB_IOI));
			//getReportManager().disableTab(getTab(TAB_STATS));
			conceptCreator = Rf2ConceptCreator.build(this, getInputFile(FILE_IDX_CONCEPT_IDS), getInputFile(FILE_IDX_DESC_IDS), getInputFile(FILE_IDX_REL_IDS), this.getNamespace());
			conceptCreator.initialiseGenerators(new String[]{"-nS",this.getNamespace(), "-m", SCTID_LOINC_EXTENSION_MODULE});
			loadSupportingInformation();
			importPartMap();
			Set<TemplatedConcept> successfullyModelled = doModeling();
			checkSpecificConcepts(successfullyModelled);
			TemplatedConcept.reportStats(getTab(TAB_SUMMARY));
			reportMissingMappings(getTab(TAB_MAP_ME));
			reportIncludedExcludedConcepts(getTab(TAB_STATS), successfullyModelled);
			flushFiles(false);
			switch (runMode) {
				case NEW: outputAllConceptsToDelta(successfullyModelled);
					break;
				case INCREMENTAL_API, INCREMENTAL_DELTA:
					determineChangeSet(successfullyModelled);
					conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
					break;
				default:
					throw new TermServerScriptException("Unrecognised Run Mode :" + runMode);
			}
			reportSummaryCounts();
		} finally {
			while (additionalThreadCount > 0) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
			finish();
			if (conceptCreator != null) {
				conceptCreator.finish();
			}
		}
	}

	private void reportSummaryCounts() throws TermServerScriptException {
		int summaryTabIdx = getTab(TAB_SUMMARY);
		report(summaryTabIdx, "");
		//Work through each catagory (sorted) and then output each summary Count for that category
		summaryCountsByCategory.keySet().stream()
				.sorted()
				.forEach(cat -> {
					reportSafely(summaryTabIdx, cat);
					Map<String, Integer> summaryCounts = summaryCountsByCategory.get(cat);
					summaryCounts.keySet().stream()
							.sorted()
							.forEach(summaryItem -> reportSafely(summaryTabIdx, "", summaryItem, summaryCounts.get(summaryItem)));
				});
	}

	private void outputAllConceptsToDelta(Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
		for (TemplatedConcept tc : successfullyModelled) {
			Concept concept = tc.getConcept();
			try {
				conceptCreator.writeConceptToRF2(getTab(TAB_IMPORT_STATUS), concept, tc.getExternalIdentifier());
			} catch (Exception e) {
				report(getTab(TAB_IMPORT_STATUS), null, concept, Severity.CRITICAL, ReportActionType.API_ERROR, tc.getExternalIdentifier(), e);
			}
		}
		conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
	}
	

	private void determineChangeSet(Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
		LOGGER.info("Determining change set for {} successfully modelled concepts", successfullyModelled.size());

		Set<String> externalIdentifiersProcessed = new HashSet<>();

		//Sort so that subsequent spreadsheets are somewhat comparable
		List<TemplatedConcept> sortedModelled = successfullyModelled.stream()
				.sorted(Comparator.comparing(TemplatedConcept::getExternalIdentifier))
				.collect(Collectors.toList());
		
		for (TemplatedConcept tc : sortedModelled) {
			determineChanges(tc, externalIdentifiersProcessed);
			incrementSummaryCount("Counts per template", tc.getClass().getSimpleName());
		}

		determineInactivations(sortedModelled);
	}

	private void determineInactivations(List<TemplatedConcept> sortedModelled) throws TermServerScriptException {
		//What external codes do we currently have that we _aren't_ going forward with.
		//Those need to be inactivated
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		Set<String> inactivatingCodes =  new HashSet<>(altIdentifierMap.keySet());
		inactivatingCodes.removeAll(sortedModelled.stream().map(m -> m.getExternalIdentifier()).collect(Collectors.toSet()));
		for (String inactivatingCode : inactivatingCodes) {
			String existingConceptSCTID = altIdentifierMap.get(inactivatingCode);
			Concept existingConcept = gl.getConcept(existingConceptSCTID, false, false);
			if (existingConcept != null) {
				inactivateConcept(existingConcept);
				conceptCreator.outputRF2Inactivation(existingConcept);
			}
			doProposedModelComparison(TemplatedConceptNull.create(inactivatingCode));
			incrementSummaryCount(CHANGES_SINCE_LAST_ITERATION, TemplatedConcept.IterationIndicator.REMOVED.toString());

			//Might not be obvious: the alternate identifier continues to exist even when the concept becomes inactive
			//So - temporarily again - we'll normalize the scheme id
			//Temporarily correct all Alternate Identifiers
			if (existingConcept != null) {
				existingConcept.setAlternateIdentifiers(new HashSet<>());
				existingConcept.addAlternateIdentifier(inactivatingCode, scheme.getId());
			}
		}
	}

	private void determineChanges(TemplatedConcept tc, Set<String> externalIdentifiersProcessed) throws TermServerScriptException {
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		Concept concept = tc.getConcept();
		externalIdentifiersProcessed.add(tc.getExternalIdentifier());

		//Do we already have this concept?
		String existingConceptSCTID = altIdentifierMap.get(tc.getExternalIdentifier());
		Set<String> differencesList = new HashSet<>();
		Concept existingConcept = getExistingConceptIfExists(existingConceptSCTID, tc);
		tc.setExistingConcept(existingConcept);

		//We need to make any adjustments to inferred relationships before we lose the stated ones in the transformation to axioms
		adjustInferredRelationships(concept, existingConcept);

		if (existingConcept == null) {
			//This concept is entirely new, prepare to output all
			if (runMode.equals(RunMode.INCREMENTAL_DELTA)) {
				conceptCreator.populateIds(concept);
			}

			if (tc.getIterationIndicator() == null) {
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.NEW);
				differencesList.add("All New");
			}
			convertStatedRelationshipsToAxioms(concept, true, true);
			concept.setAxiomEntries(AxiomUtils.convertClassAxiomsToAxiomEntries(concept));
		} else {
			determineChangesWithExistingConcept(tc);
		}

		//Update the summary count based on the comparison to the previous iteration
		incrementSummaryCount(CHANGES_SINCE_LAST_ITERATION, tc.getIterationIndicator().toString());

		//Is this a high usage concept?
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighUsage()) {
			incrementSummaryCount(HIGHEST_USAGE_COUNTS, "Active with high usage");
		}
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighestUsage()) {
			incrementSummaryCount(HIGHEST_USAGE_COUNTS,"Active with highest usage");
		}
		doProposedModelComparison(tc);

		if (!tc.getIterationIndicator().equals(TemplatedConcept.IterationIndicator.UNCHANGED)) {
			conceptCreator.outputRF2(getTab(TAB_IMPORT_STATUS), tc.getConcept(), "");
		}

		if (tc.existingConceptHasInactivations()) {
			conceptCreator.outputRF2Inactivation(tc.getExistingConcept());
		}
	}

	/**
	 * @return true if changes are detected
	 * @throws TermServerScriptException
	 */
	private boolean determineChangesWithExistingConcept(TemplatedConcept tc) throws TermServerScriptException {
		if (tc.getExistingConcept().getId().equals("89711010000103")) {
			LOGGER.debug("Here");
		}
		boolean changesDetected = false;
		SnomedUtils.getAllComponents(tc.getConcept()).forEach(c -> {
			c.setClean();
			//Normalise module
			c.setModuleId(conceptCreator.getTargetModuleId());
		});

		//We need to populate the concept SCTID before we can create axiom entries
		tc.getConcept().setId(tc.getExistingConcept().getId());
		//And we can apply that to the alternate identifiers early on so they don't show up as a change
		tc.getConcept().getAlternateIdentifiers().stream()
				.forEach(a -> a.setReferencedComponentId(tc.getExistingConcept().getId()));
		//Copy the axiom entry from the existing concept so relationship changes can be applied there
		tc.getConcept().setAxiomEntries(tc.getExistingConcept().getAxiomEntries(ActiveState.ACTIVE, false));
		convertStatedRelationshipsToAxioms(tc.getConcept(), true, true);
		tc.getConcept().setAxiomEntries(AxiomUtils.convertClassAxiomsToAxiomEntries(tc.getConcept()));

		List<ComponentComparisonResult> componentComparisonResults = SnomedUtils.compareComponents(tc.getExistingConcept(), tc.getConcept(), skipForComparison);
		if (ComponentComparisonResult.hasChanges(componentComparisonResults)) {
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.MODIFIED);
			changesDetected = true;
		} else {
			tc.setIterationIndicator(TemplatedConcept.IterationIndicator.UNCHANGED);
			tc.addDifferenceFromExistingConcept("All Unchanged");
		}

		for (ComponentComparisonResult componentComparisonResult : componentComparisonResults) {
			processComponentComparison(tc, componentComparisonResult);
		}
		return changesDetected;
	}

	private void processComponentComparison(TemplatedConcept tc, ComponentComparisonResult componentComparisonResult) throws TermServerScriptException {
		Component existingComponent = componentComparisonResult.getLeft();
		Component newlyModelledComponent = componentComparisonResult.getRight();

		if (!componentComparisonResult.isMatch()) {
			tc.addDifferenceFromExistingConcept(componentComparisonResult.getComponentTypeStr());
		}

		//If we have both, then just output the change
		if (existingComponent != null && newlyModelledComponent != null) {
			newlyModelledComponent.setId(existingComponent.getId());
			if (componentComparisonResult.isMatch()) {
				newlyModelledComponent.setClean();
			} else {
				newlyModelledComponent.setDirty();
			}

			//Any component specific actions?
			switch (existingComponent.getComponentType()) {
				case CONCEPT:
					tc.getConcept().getAlternateIdentifiers().stream()
							.forEach(a -> a.setReferencedComponentId(tc.getExistingConcept().getId()));
					break;
				case DESCRIPTION:
					Description desc = (Description)existingComponent;
					Description newDesc = (Description)newlyModelledComponent;
					newDesc.setConceptId(tc.getExistingConcept().getId());
					//Copy over the langRefset entries from the existing description
					//I'm assuming we're never going to change the acceptability
					newDesc.setLangRefsetEntries(desc.getLangRefsetEntries());
					break;
				default:
					break;
			}
		} else if (existingComponent != null && newlyModelledComponent == null) {
			//If we have an existing component and it has no newly Modelled counterpart, then inactivate it
			existingComponent.setActive(false);
			existingComponent.setDirty();
			tc.setExistingConceptHasInactivations(true);
		} else {
			//If we only have a newly modelled component, give it an id
			//and prepare to output
			conceptCreator.populateComponentId(tc.getExistingConcept(), newlyModelledComponent, externalContentModule);
			newlyModelledComponent.setDirty();
		}
	}

	private Concept getExistingConceptIfExists(String existingConceptSCTID, TemplatedConcept tc) throws TermServerScriptException {
		Concept existingConcept = null;
		if (existingConceptSCTID != null) {
			existingConcept = gl.getConcept(existingConceptSCTID, false, false);
			if (existingConcept == null) {
				String msg = "Alternate identifier " + tc.getExternalIdentifier() + " --> " + existingConceptSCTID + " but existing concept not found.  Did it get deleted?  Reusing ID.";
				//throw new TermServerScriptException(");
				addFinalWords(msg);
				tc.getConcept().setId(existingConceptSCTID);
				tc.setIterationIndicator(TemplatedConcept.IterationIndicator.RESURRECTED);
			} else {
				//Temporarily correct all Alternate Identifiers
				existingConcept.setAlternateIdentifiers(new HashSet<>());
				existingConcept.addAlternateIdentifier(tc.getExternalIdentifier(), scheme.getId());
			}
		}
		return existingConcept;
	}

	private boolean adjustInferredRelationships(Concept concept, Concept existingConcept) {
		boolean changesMade = false;
		if (existingConcept == null) {
			conceptCreator.copyStatedRelsToInferred(concept);
			changesMade = true;
		} else {
			//TODO TEMPORARY CODE
			//For existing concepts we're going to group inferred relationships if required
			for (Relationship r : existingConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getGroupId() > 1) {
					Relationship inferredRelOnNewConcept = r.cloneWithIds();
					inferredRelOnNewConcept.setGroupId(1);
					concept.addRelationship(inferredRelOnNewConcept);
					changesMade = true;
				}
			}
		}
		return changesMade;
	}

	private List<String> inactivateConcept(Concept c) {
		List<String> differencesList = new ArrayList<>();
		//To inactivate a concept we need to inactivate the concept itself and the OWL axiom.
		//The descriptions remain active, and we'll let classification sort out the inferred relationships
		if (c.isActive()) {
			c.setActive(false);  //This will inactivate the concept and all relationships
			InactivationIndicatorEntry ii = InactivationIndicatorEntry.withDefaults(c, SCTID_INACT_OUTDATED);
			ii.setModuleId(externalContentModule);
			c.addInactivationIndicator(ii);
			differencesList.add("CONCEPT");
		}
		
		for (AxiomEntry a : c.getAxiomEntries(ActiveState.ACTIVE, true)) {
			a.setActive(false);
			differencesList.add("AXIOM");
		}
		return differencesList;
	}
	
	protected void reportIncludedExcludedConcepts(int tabIdx, Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
		Set<String> successfullyModelledLoincNums = successfullyModelled.stream()
				.map(tc -> tc.getExternalIdentifier())
				.collect(Collectors.toSet());

		//Collect both included and excluded terms by property
		Map<String, List<ExternalConcept>> included = externalConceptMap.values().stream()
				.filter(lt -> successfullyModelledLoincNums.contains(lt.getExternalIdentifier()))
				.collect(Collectors.groupingBy(ExternalConcept::getProperty));

		Map<String, List<ExternalConcept>> excluded = externalConceptMap.values().stream()
				.filter(lt -> !successfullyModelledLoincNums.contains(lt.getExternalIdentifier()))
				.collect(Collectors.groupingBy(ExternalConcept::getProperty));

		Set<String> properties = new LinkedHashSet<>(included.keySet());
		properties.addAll(excluded.keySet());

		for (String property : properties) {
			int includedCount = included.getOrDefault(property, new ArrayList<>()).size();
			int includedInTop2KCount = included.getOrDefault(property, new ArrayList<>()).stream()
					.filter(ExternalConcept::isHighestUsage)
					.toList().size();
			int excludedCount = excluded.getOrDefault(property, new ArrayList<>()).size();
			int excludedInTop2KCount = excluded.getOrDefault(property, new ArrayList<>()).stream()
					.filter(ExternalConcept::isHighestUsage)
					.toList().size();
			report(tabIdx, property, inScope(property), includedCount, includedInTop2KCount, excludedCount, excludedInTop2KCount);
		}
	}
	
	protected abstract String inScope(String property) throws TermServerScriptException;

	protected abstract void doProposedModelComparison(TemplatedConcept tc) throws TermServerScriptException;

	protected abstract void loadSupportingInformation() throws TermServerScriptException;

	protected abstract void importPartMap() throws TermServerScriptException;

	protected abstract Set<TemplatedConcept> doModeling() throws TermServerScriptException;

	protected abstract String[] getTabNames() ;

	public int getTab(String tabName) throws TermServerScriptException {
		String[] tabNames = getTabNames();
		for (int i = 0; i < tabNames.length; i++) {
			if (tabNames[i].equals(tabName)) {
				return i;
			}
		}
		throw new TermServerScriptException("Tab '" + tabName + "' not recognised");
	}
	
	protected String getNamespace() {
		return namespace;
	}

	public void incrementSummaryCount(String category, String summaryItem) {
		//Increment the count for this summary item, in the appropriate category
		Map<String, Integer> summaryCounts = summaryCountsByCategory.computeIfAbsent(category, k -> new HashMap<>());
		summaryCounts.merge(summaryItem, 1, Integer::sum);
	}

	public static final List<String> ITEMS_OF_INTEREST =
			List.of("882-1","881-3","61151-7","1751-7","9318-7","1759-0","33037-3","41276-7","10466-1",
					"5767-9","33511-7","5769-5","11555-0","24321-2","1968-7","925-8","933-2","936-5",
					"62292-8","14155-6","9830-1","9322-9","2106-3","14979-9","5902-2","6301-6","38875-1",
					"50553-7","24323-8","35591-7","49024-3","39004-7","5787-7","11277-1","33219-7","12258-0",
					"788-0","30384-2","30385-9","21000-5","785-6","28539-5","786-4","28540-3","787-2",
					"30428-7","6742-1","4537-7","30341-2","58413-6","19048-8","27353-2","53553-4","49541-6",
					"48058-2","33914-3","77147-7","62238-1","48643-1","48642-3","51584-1","53115-2","34165-1",
					"38518-7","71695-1","4544-3","31100-1","56888-1","2500-7","2502-3","2532-0","24318-8",
					"26485-3","53797-7","664-3","2695-5","2708-6","32623-1","28542-9","2890-2","8251-1",
					"2965-2","5811-5","50562-8","53326-5","66746-9","3097-3","44734-2");

	public static final Map<String, String> MANUALLY_MAINTAINED_ITEMS = Map.of(
			"925-8", "580271010000105 |Blood product disposition [Type] (observable entity)|",
			"8251-1", "580221010000109 |Service comment (observable entity)|",
			"49024-3", "580231010000107 |Differential cell count method - Blood (observable entity)|",
			"49541-6", "580241010000104 |Fasting status - Reported (observable entity)|",
			"14155-6", "580261010000100 |Cholesterol in LDL [Percentile] (observable entity)|",
			"9322-9", "580251010000102 |Cholesterol.total/Cholesterol in HDL [Percentile] (observable entity)| ",
			"56888-1", "570211010000106 |Presence of Human immunodeficiency virus 1 antibody and/or Human immunodeficiency virus 2 antibody and/or Human immunodeficiency virus 1 protein 24 antigen in serum or plasma at point in time by immunoassay (observable entity)| "
	);

	public static String getSpecialInterestIndicator(String externameIdentifer) {
		return ITEMS_OF_INTEREST.contains(externameIdentifer) ? "Y" : "";
	}

	private static final String TOP_88 = "Top 88";
	private void checkSpecificConcepts(Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
		for (String loincNum : ITEMS_OF_INTEREST) {
			boolean found = false;
			if (MANUALLY_MAINTAINED_ITEMS.containsKey(loincNum)) {
				report(getTab(TAB_IOI),TOP_88, loincNum, "Modelled manually", MANUALLY_MAINTAINED_ITEMS.get(loincNum));
				continue;
			}

			for (TemplatedConcept tc : successfullyModelled) {
				if (tc.getExternalIdentifier().equals(loincNum)) {
					report(getTab(TAB_IOI), TOP_88, tc.getExternalIdentifier(), "Modelled",tc.getConcept());
					found = true;
					break;
				}
			}

			if (!found) {
				report(getTab(TAB_IOI),TOP_88, loincNum, "Not Modelled");
			}
		}
	}

	public abstract List<String> getMappingsAllowedAbsent();
	
	protected void reportMissingMappings(int tabIdx) throws TermServerScriptException {
		for (Part part : missingPartMappings.keySet()) {
			Set<ExternalConcept> externalConcepts = missingPartMappings.get(part);
			String[] highUsageIndicators = getHighUsageIndicators(externalConcepts);
			report(tabIdx, 
					part.getPartNumber(),
					part.getPartName(),
					part.getPartTypeName(),
					highUsageIndicators[0],
					highUsageIndicators[1],
					"N/A",
					externalConcepts.size(),
					highUsageIndicators[2],
					highUsageIndicators[3],
					highUsageIndicators[4]);
		}
	}

	protected abstract String[] getHighUsageIndicators(Set<ExternalConcept> externalConcepts);

	public ExternalConcept getExternalConcept(String externalIdentifier) {
		return externalConceptMap.get(externalIdentifier);
	}
	
	public void addMissingMapping(String partNum, String externalIdentifier) {
		Part part = partMap.get(partNum);
		if (part == null) {
			part = new Part(partNum, "Unknown Type", "Unknown to part input file.");
		}
		missingPartMappings.computeIfAbsent(part, key -> new HashSet<>())
							.add(getExternalConcept(externalIdentifier));
	}

	public Map<String, ExternalConcept> getExternalConceptMap() {
		return externalConceptMap;
	}
	
	public AttributePartMapManager getAttributePartManager() {
		return attributePartMapManager;
	}

}