package org.ihtsdo.termserver.scripting.pipeline;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.delta.Rf2ConceptCreator;
import org.ihtsdo.termserver.scripting.domain.Concept;

public abstract class ContentPipelineManager extends TermServerScript implements ContentPipeLineConstants {

	protected static int FILE_IDX_CONCEPT_IDS = 6;
	protected static int FILE_IDX_DESC_IDS = 7;
	protected static int FILE_IDX_PREVIOUS_ITERATION = 8;

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
			conceptCreator.initialiseGenerators(new String[]{"-nS","1010000", "-iR", "16470", "-m", "11010000107"});
			importExternalContent();
			importPartMap();
			Set<TemplatedConcept> successfullyModelled = doModeling();
			TemplatedConcept.reportStats(getTab(TAB_SUMMARY));
			TemplatedConcept.reportMissingMappings(getTab(TAB_MAP_ME));
			flushFiles(false);
			for (TemplatedConcept tc : successfullyModelled) {
				Concept concept = tc.getConcept();
				try {
					conceptCreator.copyStatedRelsToInferred(concept);
					conceptCreator.writeConceptToRF2(getTab(TAB_IMPORT_STATUS), concept, tc.getExternalIdentifier(), SCTID_LOINC_EXTENSION_MODULE);
				} catch (Exception e) {
					report(getTab(TAB_IMPORT_STATUS), null, concept, Severity.CRITICAL, ReportActionType.API_ERROR, tc.getExternalIdentifier(), e);
				}
			}
			conceptCreator.createOutputArchive(getTab(TAB_IMPORT_STATUS));

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