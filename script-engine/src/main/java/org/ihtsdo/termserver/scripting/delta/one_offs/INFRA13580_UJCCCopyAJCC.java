package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class INFRA13580_UJCCCopyAJCC extends DeltaGenerator implements ScriptConstants{

	private static final int BATCH_SIZE = 20;
	private static final Map<String, String> REPLACEMENT_MAP = Map.of(
			"American Joint Committee on Cancer", "Union for International Cancer Control",
			"AJCC", "UICC"
	);

	private Concept annotationType = null;
	private String annotationStr = "Union for International Cancer Control: https://www.uicc.org/who-we-are/about-uicc/uicc-and-tnm";


	public static void main(String[] args) throws TermServerScriptException {
		INFRA13580_UJCCCopyAJCC delta = new INFRA13580_UJCCCopyAJCC();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.init(args);
			delta.inputFileHasHeaderRow = true;
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit();
			delta.annotationType = delta.gl.getConcept("1295448001"); // |Attribution (attribute)|
			int lastBatchSize = delta.process();
			delta.createOutputArchive(false, lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Info, detail, , ",
				"SCTID, FSN, SemTag, Reason, detail, detail,"
		};

		String[] tabNames = new String[]{
				"Records Processed",
				"Records Skipped"
		};
		super.postInit(tabNames, columnHeadings, false);
	}

	private int process() throws TermServerScriptException {
		int conceptsInThisBatch = 0;
		for (Component c : processFile()) {
			conceptsInThisBatch++;
			cloneConceptAsUJCC((Concept)c);
			addAnnotation((Concept)c);
			if (conceptsInThisBatch >= BATCH_SIZE) {
				if (!dryRun) {
					createOutputArchive(false, conceptsInThisBatch);
					outputDirName = "output"; //Reset so we don't end up with _1_1_1
					initialiseOutputDirectory();
					initialiseFileHeaders();
				}
				gl.setAllComponentsClean();
				conceptsInThisBatch = 0;
			}
		}
		return conceptsInThisBatch;
	}

	private void cloneConceptAsUJCC(Concept c) throws TermServerScriptException {
		Concept ujcc = c.clone(conIdGenerator.getSCTID());
		for (Description d : new ArrayList<>(ujcc.getDescriptions())) {
			normaliseDescription(ujcc, d);
		}
		//Mark inferred relationships as clean so we don't export them
		for (Relationship r : ujcc.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			r.setClean();
		}
		addAnnotation(ujcc);
		outputRF2(ujcc);
		report(c, Severity.LOW, ReportActionType.CONCEPT_ADDED, ujcc);
	}

	private void normaliseDescription(Concept c, Description d) throws TermServerScriptException {
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
			c.removeDescription(d);
			return;
		}
		String originalTerm = d.getTerm();
		String term = d.getTerm();
		for (Map.Entry<String, String> replacement : REPLACEMENT_MAP.entrySet()) {
			term = term.replace(replacement.getKey(), replacement.getValue());
		}
		if (term.length() < 10) {
			term += " (UJCC)";
		}
		d.setTerm(term);
		if (d.getType().equals(DescriptionType.FSN)) {
			c.setFsn(term);
		}
		report(c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, originalTerm, d);
		String descId = descIdGenerator.getSCTID();
		d.setDescriptionId(descId);
		for (LangRefsetEntry l : d.getLangRefsetEntries()) {
			l.setReferencedComponentId(descId);
		}
	}

	private void addAnnotation(Concept c) throws TermServerScriptException {
			ComponentAnnotationEntry cae = ComponentAnnotationEntry.withDefaults(c, annotationType, annotationStr);
			c.addComponentAnnotationEntry(cae);
			report(c, Severity.LOW, ReportActionType.ANNOTATION_ADDED, annotationStr);
	}

}
