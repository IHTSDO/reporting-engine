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

	enum RunMode { NEW, INCREMENTAL_DELTA, INCREMENTAL_API}
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ContentPipelineManager.class);
	
	protected static final RunMode runMode = RunMode.INCREMENTAL_DELTA;
	
	protected static int FILE_IDX_CONCEPT_IDS = 7;
	protected static int FILE_IDX_DESC_IDS = 8;
	protected static int FILE_IDX_REL_IDS = 9;
	
	protected Concept scheme;
	protected String namespace;
	protected String externalContentModule;
	protected Rf2ConceptCreator conceptCreator;
	protected int additionalThreadCount = 0;

	protected  Map<String, Integer> summaryCounts = new HashMap<>();

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
			getArchiveManager().setPopulateReleasedFlag(true);  //Needed for working out if we're deleteing or inactivating
			init(args);
			loadProjectSnapshot(false);
			postInit();
			//getReportManager().disableTab(getTab(TAB_MODELING_ISSUES));
			//getReportManager().disableTab(getTab(TAB_MAP_ME));
			getReportManager().disableTab(getTab(TAB_IOI));
			getReportManager().disableTab(getTab(TAB_STATS));
			conceptCreator = Rf2ConceptCreator.build(this, getInputFile(FILE_IDX_CONCEPT_IDS), getInputFile(FILE_IDX_DESC_IDS), getInputFile(FILE_IDX_REL_IDS), this.getNamespace());
			conceptCreator.initialiseGenerators(new String[]{"-nS",this.getNamespace(), "-m", SCTID_LOINC_EXTENSION_MODULE});
			loadSupportingInformation();
			importPartMap();
			Set<TemplatedConcept> successfullyModelled = doModeling();
			TemplatedConcept.reportStats(getTab(TAB_SUMMARY));
			reportMissingMappings(getTab(TAB_MAP_ME));
			reportExcludedConcepts(getTab(TAB_STATS), successfullyModelled);
			flushFiles(false);
			switch (runMode) {
				case NEW: outputAllConceptsToDelta(successfullyModelled);
					break;
				case INCREMENTAL_API:
				case INCREMENTAL_DELTA:
					determineChangeSet(successfullyModelled);
					conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
					break;
				default:
					throw new TermServerScriptException("Unrecognised Run Mode :" + runMode);
			}
			reportSummaryCounts(summaryCounts);
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

	private void reportSummaryCounts(Map<String, Integer> summaryCounts) throws TermServerScriptException {
		report(getTab(TAB_SUMMARY), "");
		for (String key : summaryCounts.keySet()) {
			report(getTab(TAB_SUMMARY), key, summaryCounts.get(key));
		}
	}

	protected abstract void reportMissingMappings(int tabIdx) throws TermServerScriptException;

	protected abstract void reportExcludedConcepts(int tabIdx, Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException;

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
		LOGGER.info("Determining change set for " + successfullyModelled.size() + " successfully modelled concepts");

		Set<String> externalIdentifiersProcessed = new HashSet<>();

		//Sort so that subsequent spreadsheets are somewhat comparable
		List<TemplatedConcept> sortedModelled = successfullyModelled.stream()
				.sorted(Comparator.comparing(TemplatedConcept::getExternalIdentifier))
				.collect(Collectors.toList());
		
		for (TemplatedConcept tc : sortedModelled) {
			determineChanges(tc, externalIdentifiersProcessed);
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
			summaryCounts.merge("Removed", 1, Integer::sum);

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
		summaryCounts.merge(tc.getIterationIndicator().toString(), 1, Integer::sum);
		//Is this a high usage concept?
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighUsage()) {
			summaryCounts.merge("Active with high usage", 1, Integer::sum);
		}
		if (activeIndicators.contains(tc.getIterationIndicator()) && tc.isHighestUsage()) {
			summaryCounts.merge("Active with highest usage", 1, Integer::sum);
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
}