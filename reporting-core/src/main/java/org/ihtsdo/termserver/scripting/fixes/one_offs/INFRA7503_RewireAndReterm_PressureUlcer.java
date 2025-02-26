package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * 399912005 |Pressure ulcer (disorder)|will be inactivated and the remaining 
 * 33 descendants that do not mention a specific stage will be renamed using 
 * Pressure injury instead of pressure ulcer with the value of associated morphology 
 * changed from 420226006 |Pressure ulcer (morphologic abnormality) |
 * to 1163214006 |Pressure injury (morphologic abnormality) |. 
 * This will result in these concepts being relocated under 
 * 1163215007 |Pressure injury (disorder)|.
 */
public class INFRA7503_RewireAndReterm_PressureUlcer extends BatchFix {

	private Set<String> exclusionTexts;
	private RelationshipTemplate addTemplate;
	private RelationshipTemplate searchTemplate;
	private RelationshipTemplate replaceTemplate;
	
	Map<String, String> searchAndReplaceText;
	
	protected INFRA7503_RewireAndReterm_PressureUlcer(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA7503_RewireAndReterm_PressureUlcer fix = new INFRA7503_RewireAndReterm_PressureUlcer(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail, Further Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subsetECL = "< 399912005 |Pressure ulcer (disorder)|";
		addTemplate = null;
		replaceTemplate = new RelationshipTemplate(ASSOC_MORPH, 
				gl.getConcept("1163215007 |Pressure injury (disorder)|"));
		searchTemplate = new RelationshipTemplate(ASSOC_MORPH, 
				gl.getConcept("420226006 |Pressure ulcer (morphologic abnormality) |"));
		
		exclusionTexts = new HashSet<>();
		exclusionTexts.add("stage");
		
		searchAndReplaceText = new HashMap<>();
		searchAndReplaceText.put("Pressure ulcer", "Pressure injury");
		searchAndReplaceText.put("pressure ulcer", "pressure injury");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
			changesMade += addAttribute(task, loadedConcept);
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

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			if (d.isPreferred()) {
				for (Map.Entry<String, String> entry : searchAndReplaceText.entrySet()) {
					if (d.getTerm().contains(entry.getKey())) {
						String newTerm = d.getTerm().replace(entry.getKey(), entry.getValue());
						replaceDescription(t, c, d, newTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, true);
						changesMade++;
					}
				}
			}
		}
		return changesMade;
	}

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		boolean matchFound = false;
		//Find the groupId of the matching relationship template
		for (Relationship r : c.getRelationships(searchTemplate, ActiveState.ACTIVE)) {
			matchFound = true;
			//Are we adding and/or replacing the matched relationship?
			if (addTemplate != null) {
				Relationship addAttrib = addTemplate.createRelationship(c, r.getGroupId(), null);
				changesMade += addRelationship(t, c, addAttrib);
			}
			
			if (replaceTemplate != null) {
				Relationship replaceAttrib = replaceTemplate.createRelationship(c, r.getGroupId(), null);
				changesMade += replaceRelationship(t, c, r, replaceAttrib);
			}
			
		}
		if (!matchFound) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Attribute to match not detected", searchTemplate);
		}
		return changesMade;
	}

	private boolean isExcluded(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		return isExcluded(fsn);
	}
	
	private boolean isExcluded(String term) {
		for (String exclusionWord : exclusionTexts) {
			if (term.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}


	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return findConcepts(subsetECL)
				.stream()
				.filter(c -> !isExcluded(c))
				.collect(Collectors.toList());
	}
}
