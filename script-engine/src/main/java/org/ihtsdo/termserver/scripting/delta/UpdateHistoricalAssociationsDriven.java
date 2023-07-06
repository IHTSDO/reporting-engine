package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/* INFRA-10432 Update On Examination historical associations based on a spreadsheet.
*/
public class UpdateHistoricalAssociationsDriven extends DeltaGenerator implements ScriptConstants{
	
	private Map<Concept, Concept> replacementMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		UpdateHistoricalAssociationsDriven delta = new UpdateHistoricalAssociationsDriven();
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			delta.newIdsRequired = false; 
			delta.init(args);
			delta.loadProjectSnapshot(false); 
			delta.postInit();
			delta.process();
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	private void process() throws ValidationFailure, TermServerScriptException {
		
		populateReplacementMap();
		
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			//Is this a concept we've been told to replace the associations on?
			if (replacementMap.containsKey(c)) {
				if (!c.isActive()) {
					//Change the Inactivation Indicator
					List<InactivationIndicatorEntry> indicators = c.getInactivationIndicatorEntries(ActiveState.ACTIVE);
					if (indicators.size() != 1) {
						report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept has " + indicators.size() + " active inactivation indicators");
						continue;
					}
					InactivationIndicatorEntry i = indicators.get(0);
					InactivationIndicator prevInactValue = SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId());
					//We're going to skip concepts that are currently ambigous and just say what they're set to
					if (prevInactValue.equals(InactivationIndicator.AMBIGUOUS)) {
						String assocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
						report(c, Severity.MEDIUM, ReportActionType.NO_CHANGE, assocStr, "Supplied: " + replacementMap.get(c));
						continue;
					}
				
					if (!prevInactValue.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
						i.setInactivationReasonId(SCTID_INACT_NON_CONFORMANCE);
						i.setEffectiveTime(null);  //Will mark as dirty
						report(c, Severity.LOW, ReportActionType.INACT_IND_MODIFIED, prevInactValue + " --> NCEP");
					}
					
					replaceHistoricalAssociations(c);
					
				} else {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active");
				}
			}
		}
	}

	private void replaceHistoricalAssociations(Concept c) throws TermServerScriptException {
		String prevAssocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		for (AssociationEntry a : c.getAssociationEntries()) {
			if (a.getRefsetId().equals(SCTID_ASSOC_REPLACED_BY_REFSETID)) {
				report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept already using a ReplacedBy association");
				return;
			}
			a.setActive(false);
			a.setEffectiveTime(null);
		}
		Concept replacement = replacementMap.get(c);
		AssociationEntry assoc = AssociationEntry.create(c, SCTID_ASSOC_REPLACED_BY_REFSETID, replacement);
		c.getAssociationEntries().add(assoc);
		String newAssocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
		report(c, Severity.LOW, ReportActionType.ASSOCIATION_CHANGED, prevAssocStr, newAssocStr);
	}

	private void populateReplacementMap() throws TermServerScriptException {
		try {
			for (String line : Files.readAllLines(getInputFile().toPath(), Charset.defaultCharset())) {
				try {
					String[] items = line.split(TAB);
					Concept inactive = gl.getConcept(items[0]);
					Concept replacement = gl.getConcept(items[2]);
					Concept existing = replacementMap.get(inactive);
					if (existing != null && !existing.equals(replacement)) {
						warn("Map replacement for inactive " + inactive + " was " + existing + " now " + replacement);
					}
					replacementMap.put(inactive, replacement);
				} catch (Exception e) {
					warn("Failed to parse line: " + line);
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
		
	}

}
