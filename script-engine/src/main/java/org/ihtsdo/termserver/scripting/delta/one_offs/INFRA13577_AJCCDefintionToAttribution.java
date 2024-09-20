package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentAnnotationEntry;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.snomed.otf.script.dao.ReportSheetManager;

public class INFRA13577_AJCCDefintionToAttribution extends DeltaGenerator implements ScriptConstants{

	private static final int BATCH_SIZE = 100;
	private static final String MATCH_TEXT = "Used with permission of the American College of Surgeons";

	private String ecl = "<< 1222584008 |American Joint Committee on Cancer allowable value (qualifier value)| ";

	private Concept annotationType = null;
	private String annotationStr = "American College of Surgeons, Chicago, Illinois: https://www.facs.org/quality-programs/cancer/ajcc/cancer-staging";

	public static void main(String[] args) throws TermServerScriptException {
		INFRA13577_AJCCDefintionToAttribution delta = new INFRA13577_AJCCDefintionToAttribution();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.getArchiveManager().setPopulateReleasedFlag(true);
			delta.newIdsRequired = false;
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
				"Map Records Processed",
				"Map Records Skipped"
		};
		super.postInit(tabNames, columnHeadings, false);
	}

	private int process() throws TermServerScriptException {
		int conceptsInThisBatch = 0;
		for (Concept c : findConcepts(ecl)) {
			conceptsInThisBatch ++;
			inactivateTextDefinition(c);
			addAttribution(c);
			outputRF2(c);
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

	private void inactivateTextDefinition(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				if (d.getTerm().contains(MATCH_TEXT)) {
					d.setActive(false);
					report(c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, d);
				} else {
					report(c, Severity.HIGH, ReportActionType.NO_CHANGE, d);
				}
			}
		}
	}

	private void addAttribution(Concept c) throws TermServerScriptException {
		ComponentAnnotationEntry cae = ComponentAnnotationEntry.withDefaults(c, annotationType, annotationStr);
		c.addComponentAnnotationEntry(cae);
		report(c, Severity.LOW, ReportActionType.ANNOTATION_ADDED, annotationStr);
	}

}
