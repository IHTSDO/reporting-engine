package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * NUTRITION-50 Batch change of estimated/measured nutritional intake observable content
 */
public class NUTRITION50_ReplaceAttributeAndReterm extends BatchFix {

	private Set<String> exclusionTexts;
	private RelationshipTemplate addTemplate;
	private RelationshipTemplate matchTemplate;
	private RelationshipTemplate replaceTemplate;
	
	protected NUTRITION50_ReplaceAttributeAndReterm(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NUTRITION50_ReplaceAttributeAndReterm fix = new NUTRITION50_ReplaceAttributeAndReterm(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subsetECL = "<< 363787002 |Observable entity (observable entity)| : 370130000 |Property (attribute)| = 118544000 |Mass rate (property) (qualifier value)|";
		addTemplate = null;
		replaceTemplate = new RelationshipTemplate(gl.getConcept("370130000 |Property (attribute)|"), 
				gl.getConcept("118597006 |Quantity rate (property) (qualifier value)| "));
		matchTemplate = new RelationshipTemplate(gl.getConcept("370130000 |Property (attribute)|"), 
				gl.getConcept("118544000 |Mass rate (property) (qualifier value)|"));
		
		exclusionTexts = new HashSet<>();
		exclusionTexts.add("contrast");
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
			/*switch (d.getType()) {
				case FSN : changesMade += modifyFSN(t, c);
							break;
				case SYNONYM :  if (!isExcluded(d.getTerm().toLowerCase())) {
									String replacement = d.getTerm() + " with contrast";
									replaceDescription(t, c, d, replacement, null);
									changesMade++;
								};
								break;
				default : 
			}*/
			if (d.isPreferred() && !d.getTerm().contains("quantity of")) {
				if (!d.getTerm().contains("intake")) {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Preferred description did not contain target word 'intake' to which to add 'quantity of'", d);
				} else {
					String replacement = d.getTerm().replace("intake", "quantity of intake");
					replaceDescription(t, c, d, replacement, null);
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	/*private int modifyFSN(Task t, Concept c) throws TermServerScriptException {
		String[] fsnParts = SnomedUtils.deconstructFSN(c.getFsn());
		String replacement = fsnParts[0] + " with contrast " + fsnParts[1];
		replaceDescription(t, c, c.getFSNDescription(), replacement, null);
		return CHANGE_MADE;
	}*/

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		boolean matchFound = false;
		//Find the groupId of the matching relationship template
		for (Relationship r : c.getRelationships(matchTemplate, ActiveState.ACTIVE)) {
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
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Attribute to match not detected", matchTemplate);
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
