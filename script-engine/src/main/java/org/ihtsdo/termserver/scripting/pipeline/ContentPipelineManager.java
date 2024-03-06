package org.ihtsdo.termserver.scripting.pipeline;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.delta.Rf2ConceptCreator;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.InactivationIndicatorEntry;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class ContentPipelineManager extends TermServerScript implements ContentPipeLineConstants {

	enum RunMode { NEW, INCREMENTAL_DELTA, INCREMENTAL_API};
	
	private static final Logger LOGGER = LoggerFactory.getLogger(ContentPipelineManager.class);
	
	protected static final RunMode runMode = RunMode.INCREMENTAL_DELTA;
	
	protected static int FILE_IDX_CONCEPT_IDS = 6;
	protected static int FILE_IDX_DESC_IDS = 7;
	protected static int FILE_IDX_PREVIOUS_ITERATION = 8;
	
	protected Concept scheme;
	protected String externalContentModule;

	protected Rf2ConceptCreator conceptCreator;

	protected int additionalThreadCount = 0;

	protected void ingestExternalContent(String[] args) throws TermServerScriptException {
		try {
			runStandAlone = false;
			getGraphLoader().setExcludedModules(new HashSet<>());
			getArchiveManager().setRunIntegrityChecks(false);
			init(args);
			loadProjectSnapshot(false);
			postInit();
			conceptCreator = Rf2ConceptCreator.build(this, getInputFile(FILE_IDX_CONCEPT_IDS), getInputFile(FILE_IDX_DESC_IDS), null);
			conceptCreator.initialiseGenerators(new String[]{"-nS","1010000", "-iR", "16470", "-m", SCTID_LOINC_EXTENSION_MODULE});
			importExternalContent();
			importPartMap();
			Set<TemplatedConcept> successfullyModelled = doModeling();
			TemplatedConcept.reportStats(getTab(TAB_SUMMARY));
			reportMissingMappings(getTab(TAB_MAP_ME));
			flushFiles(false);
			switch (runMode) {
				case NEW: outputAllConceptsToDelta(successfullyModelled);
					break;
				case INCREMENTAL_API:
				case INCREMENTAL_DELTA:
					determineChangeSet(successfullyModelled);
					break;
				default:
					throw new TermServerScriptException("Unrecognised Run Mode :" + runMode);
			}
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

	protected abstract void reportMissingMappings(int tabIdx) throws TermServerScriptException;

	private void outputAllConceptsToDelta(Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
		for (TemplatedConcept tc : successfullyModelled) {
			Concept concept = tc.getConcept();
			try {
				conceptCreator.copyStatedRelsToInferred(concept);
				conceptCreator.writeConceptToRF2(getTab(TAB_IMPORT_STATUS), concept, tc.getExternalIdentifier());
			} catch (Exception e) {
				report(getTab(TAB_IMPORT_STATUS), null, concept, Severity.CRITICAL, ReportActionType.API_ERROR, tc.getExternalIdentifier(), e);
			}
		}
		conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
	}
	

	private Set<TemplatedConcept> determineChangeSet(Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
		LOGGER.info("Determining change set for " + successfullyModelled.size() + " successfully modelled concepts");
		Set<ComponentType> skipForComparison = Set.of(
				ComponentType.INFERRED_RELATIONSHIP,
				ComponentType.LANGREFSET);
		Map<String, String> altIdentifierMap = gl.getSchemaMap(scheme);
		Set<String> externalIdentifiersProcessed = new HashSet<>();
		Set<TemplatedConcept> changeSet = new HashSet<>();
		Set<Concept> dirtyConcepts = new HashSet<>();
		
		for (TemplatedConcept tc : successfullyModelled) {
			dirtyConcepts.clear();
			//Set this concept to be clean.  We'll mark dirty where differences exist
			Concept concept = tc.getConcept();
			externalIdentifiersProcessed.add(tc.getExternalIdentifier());
			//Do we already have this concept?
			Concept existingConcept = null;
			String existingConceptSCTID = altIdentifierMap.get(tc.getExternalIdentifier());
			String previousIterationIndicator = null;
			Set<String> differencesList = new HashSet<>();
			if (existingConceptSCTID != null) {
				existingConcept = gl.getConcept(existingConceptSCTID, false, false);
				if (existingConcept == null) {
					String msg = "Alternate identifier " + tc.getExternalIdentifier() + " --> " + existingConceptSCTID + " but existing concept not found.  Did it get deleted?  Reusing ID.";
					//throw new TermServerScriptException(");
					addFinalWords(msg);
					concept.setId(existingConceptSCTID);
					previousIterationIndicator = "Resurrected";
				} else {
					//Temporarily correct all Alternate Identifiers
					existingConcept.setAlternateIdentifiers(new HashSet<>());
					existingConcept.addAlternateIdentifier(tc.getExternalIdentifier(), scheme.getId());
				}
			}
			
			if (existingConcept == null) {
				//This concept is entirely new, prepare to output all
				if (runMode.equals(RunMode.INCREMENTAL_DELTA)) {
					conceptCreator.populateIds(concept);
				}
				changeSet.add(tc);
				dirtyConcepts.add(concept);
				if (previousIterationIndicator == null) {
					previousIterationIndicator = "New";
					differencesList.add("All New");
				}
			} else {
				SnomedUtils.getAllComponents(concept).forEach(c -> { 
					c.setClean();
					//Normalise module
					c.setModuleId(conceptCreator.getTargetModuleId());
				});
				List<Component[]> differences = SnomedUtils.compareComponents(existingConcept, tc.getConcept(), skipForComparison);
				if (differences.size() == 0) {
					previousIterationIndicator = "Unchanged";
					differencesList.add("All Unchanged");
				}
				
				for (Component[] difference : differences) {
					previousIterationIndicator = "Updated";
					Component existingComponent = difference[0];
					Component newlyModelledComponent = difference[1];
					differencesList.add(newlyModelledComponent.getComponentType().toString());
					//If we have both, then just output the change
					if (existingComponent != null && newlyModelledComponent != null) {
						newlyModelledComponent.setId(existingComponent.getId());
						newlyModelledComponent.setDirty();
						dirtyConcepts.add(concept);
						//And we'll have the axiom id too
						String axiomId = existingConcept.getFirstActiveClassAxiom().getAxiomId();
						concept.getFirstActiveClassAxiom().setId(axiomId);
					} else if (existingComponent != null && newlyModelledComponent == null) {
						//If we have an existing component and it has no newly Modelled counterpart, 
						//then inactivate it
						existingComponent.setActive(false);
						existingComponent.setDirty();
						dirtyConcepts.add(existingConcept);
					} else {
						//If we only have a newly modelled component, give it an id 
						//and prepare to output
						conceptCreator.populateComponentId(newlyModelledComponent, externalContentModule);
						newlyModelledComponent.setDirty();
						dirtyConcepts.add(concept);
					}
				}
			}
			
			String differencesListStr = differencesList.stream().collect(Collectors.joining(",\n"));
			doProposedModelComparison(tc.getExternalIdentifier(), tc, existingConcept, previousIterationIndicator, differencesListStr);
			
			for (Concept dirtyConcept : dirtyConcepts) {
				conceptCreator.outputRF2(dirtyConcept);
			}
		}
		
		//What external codes do we currently have that we _aren't_ going forward with.
		//Those need to be inactivated
		Set<String> inactivatingCodes =  new HashSet<>(altIdentifierMap.keySet());
		inactivatingCodes.removeAll(successfullyModelled.stream().map(m -> m.getExternalIdentifier()).collect(Collectors.toSet()));
		for (String inactivatingCode : inactivatingCodes) {
			String existingConceptSCTID = altIdentifierMap.get(inactivatingCode);
			Concept existingConcept = gl.getConcept(existingConceptSCTID);
			List<String> differencesList = inactivateConcept(existingConcept);
			String differencesListStr = differencesList.stream().collect(Collectors.joining(",\n"));
			doProposedModelComparison(inactivatingCode, null, existingConcept, "Removed", differencesListStr);
		
			//Might not be obvious: the alternate identifier continues to exist even when the concept becomes inactive
			//So - temporarily again - we'll normalize the scheme id
			//Temporarily correct all Alternate Identifiers
			existingConcept.setAlternateIdentifiers(new HashSet<>());
			existingConcept.addAlternateIdentifier(inactivatingCode, scheme.getId());
		}
		return changeSet;
	}


	private List<String> inactivateConcept(Concept c) {
		List<String> differencesList = new ArrayList<>();
		//To inactivate a concept we need to inactivate the concept itself and the OWL axiom.
		//The descriptions remain active and we'll let classification sort out the inferred relationships
		if (c.isActive()) {
			c.setActive(false);
			InactivationIndicatorEntry ii = InactivationIndicatorEntry.withDefaults(c);
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

	protected abstract void doProposedModelComparison(String externalIdentifier, TemplatedConcept tc, Concept existingConcept, String statusStr, String differencesListStr) throws TermServerScriptException;

	protected abstract void importExternalContent() throws TermServerScriptException;

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
}