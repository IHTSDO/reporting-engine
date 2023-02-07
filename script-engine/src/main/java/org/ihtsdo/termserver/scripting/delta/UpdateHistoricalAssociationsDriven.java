package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.HashMap;
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
			delta.loadProjectSnapshot(true); 
			delta.postInit();
			delta.process();
			delta.flushFiles(false, true); //Need to flush files before zipping
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
					String assocStr = SnomedUtils.prettyPrintHistoricalAssociations(c, gl);
					report(c, Severity.LOW, ReportActionType.ASSOCIATION_CHANGED, assocStr, replacementMap.get(c));
				} else {
					report(c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is active");
				}
			}
		}
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

	public InactivationIndicatorEntry addConceptNotCurrentInactivationIndicator(Concept c, Description d) throws TermServerScriptException, ValidationFailure {
		InactivationIndicatorEntry cnc = InactivationIndicatorEntry.withDefaults(d);
		cnc.setInactivationReasonId(SCTID_INACT_CONCEPT_NON_CURRENT);
		d.addInactivationIndicator(cnc);
		return cnc;
	}

}
