package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class to fix Case Significance issues by setting to defined value
 * ISRS-302
 * 
 */
public class CaseSignificanceSpecified extends DeltaGenerator implements RF2Constants {

	String newEffectiveTime = "20180131";
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		CaseSignificanceSpecified delta = new CaseSignificanceSpecified();
		try {
			delta.newIdsRequired = false; // We'll only be modifying existing descriptions\
			delta.additionalReportColumns = "Old, New, EffectiveTime, Notes";
			delta.inputFileHasHeaderRow = true;
			delta.runStandAlone = true;
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(false); 
			//We won't include the project export in our timings
			delta.startTimer();
			List<Component> modifiedConcepts = delta.processFile();
			delta.writeRF2(modifiedConcepts);
			delta.flushFiles(false, true); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	private void writeRF2(List<Component> modifiedConcepts) throws TermServerScriptException {
		for (Component c : modifiedConcepts) {
			outputRF2((Concept)c);
			incrementSummaryInformation("ConceptsModified");
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		//Format indicator , description id
		String newIndicator = lineItems[0];
		String descId = lineItems[1];
		Description d = gl.getDescription(descId);
		Concept c = gl.getConcept(d.getConceptId());
		String currentIndicator = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
		if (newIndicator.toLowerCase().contains("error")) {
			report(c,d,Severity.MEDIUM,ReportActionType.VALIDATION_CHECK, currentIndicator, "", d.getEffectiveTime(), newIndicator);
			incrementSummaryInformation("descriptionsSkipped");
		} else {
			//Is this actually a change?
			if (currentIndicator.equals(newIndicator)) {
				report(c,d,Severity.LOW,ReportActionType.VALIDATION_CHECK, currentIndicator, newIndicator, d.getEffectiveTime(), "Case Significance already at correct value");
				incrementSummaryInformation("descriptionsSkipped");
			} else {
				report(c,d,Severity.LOW,ReportActionType.DESCRIPTION_CHANGE_MADE, currentIndicator, newIndicator, d.getEffectiveTime());
				d.setCaseSignificance(SnomedUtils.translateCaseSignificanceFromString(newIndicator));
				d.setEffectiveTime(newEffectiveTime);
				d.setDirty();
				incrementSummaryInformation("descriptionsModified");
			}
		}
		return Collections.singletonList(c);
	}
}

