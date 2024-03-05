package org.ihtsdo.termserver.scripting.pipeline;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.delta.Rf2ConceptCreator;
import org.ihtsdo.termserver.scripting.domain.Concept;
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
				conceptCreator.writeConceptToRF2(getTab(TAB_IMPORT_STATUS), concept, tc.getExternalIdentifier(), externalContentModule);
			} catch (Exception e) {
				report(getTab(TAB_IMPORT_STATUS), null, concept, Severity.CRITICAL, ReportActionType.API_ERROR, tc.getExternalIdentifier(), e);
			}
		}
		conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));
	}
	

	private Set<TemplatedConcept> determineChangeSet(Set<TemplatedConcept> successfullyModelled) throws TermServerScriptException {
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
			if (existingConceptSCTID != null) {
				existingConcept = gl.getConcept(existingConceptSCTID, false, false);
				if (existingConcept == null) {
					String msg = "Alternate identifier " + tc.getExternalIdentifier() + " --> " + existingConceptSCTID + " but existing concept not found.  Did it get deleted?  Reusing ID.";
					//throw new TermServerScriptException(");
					addFinalWords(msg);
					concept.setId(existingConceptSCTID);
				}
			}
 
			if (existingConcept == null) {
				//This concept is entirely new, prepare to output all
				if (runMode.equals(RunMode.INCREMENTAL_DELTA)) {
					conceptCreator.populateIds(concept, existingConceptSCTID);
				}
				changeSet.add(tc);
				dirtyConcepts.add(concept);
			} else {
				SnomedUtils.getAllComponents(concept).forEach(c -> { 
					c.setClean();
					//Normalise module
					c.setModuleId(conceptCreator.getTargetModuleId());
				});
				List<Component[]> differences = SnomedUtils.compareComponents(existingConcept, tc.getConcept());
				for (Component[] difference : differences) {
					Component existingComponent = difference[0];
					Component newlyModelledComponent = difference[1];
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
			
			for (Concept dirtyConcept : dirtyConcepts) {
				conceptCreator.outputRF2(dirtyConcept);
			}
		}
		return changeSet;
	}


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