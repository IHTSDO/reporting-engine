package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 */
public class RetermConcepts extends BatchFix {
	
	private String identifyingText = "caused by";
	private Map<String, String> replacementMap;
	private boolean forcePTAlignment = true;
	
	protected RetermConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RetermConcepts fix = new RetermConcepts(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			//fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		subsetECL = "<< 404684003 |Clinical finding|";
		replacementMap = new HashMap<>();
		//text should be lower case.
		//TODO Make replacement match case, if there's a chance of it being the first word
		replacementMap.put("due to", "caused by");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = reterm(task, loadedConcept);
			if (changesMade > 0 && forcePTAlignment) {
				changesMade += forcePTAlignment(task, loadedConcept);
			}
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int reterm(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
			String find = entry.getKey();
			String replace = entry.getValue();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				//Skip FSNs & text definitions
				if (!d.getType().equals(DescriptionType.SYNONYM)) {
					continue;
				}
				//In this case we're looking for an entire match
				if (d.getTerm().contains(find)) {
					if (!d.isReleased()) {
						report(t, c, Severity.MEDIUM, ReportActionType.INFO, "New description this cycle");
					}
					String replacement = d.getTerm().replaceAll(find, replace);
					replaceDescription(t, c, d, replacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					changesMade++;
				}
			}
		}
		return changesMade;
	}
	
	
	private int forcePTAlignment(Task t, Concept c) throws TermServerScriptException {
		String alignedPT = SnomedUtils.deconstructFSN(c.getFsn())[0];
		Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
		
		if (!usPT.equals(gbPT)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "US/GB Variance!", gbPT);
		}
		
		//We've already deleted the unwanted terms, so we'll demote this PT rather than inactivate
		if (!alignedPT.equals(usPT.getTerm())) {
			replaceDescription(t, c, usPT, alignedPT, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, true, null);
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		nextConcept:
		for (Concept c : findConcepts(subsetECL, true, true)) {
			//Flag up any descriptions that have both the find AND the replace in the same term.
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
					continue;
				}
				
				for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
					String find = entry.getKey();
					String replace = entry.getValue();
					String term = d.getTerm().toLowerCase();
					if (term.contains(find) && term.contains(replace)) {
						report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Term contains both '" + find + "' and '" + replace + "'", d);
						continue nextConcept;
					}
				}
			}
			
			if (c.getFsn().toLowerCase().contains(identifyingText)) {
				for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
					String find = entry.getKey();
					for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
						//Skip FSNs & text definitions
						if (!d.getType().equals(DescriptionType.SYNONYM)) {
							continue;
						}
						//In this case we're looking for an entire match
						if (d.getTerm().contains(find)) {
							toProcess.add(c);
							continue nextConcept;
						}
					}
				}
			}
		}
		return toProcess;
	}
}
