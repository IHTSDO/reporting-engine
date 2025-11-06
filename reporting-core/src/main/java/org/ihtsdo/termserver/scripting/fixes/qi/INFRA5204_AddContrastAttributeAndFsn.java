package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * INFRA-5204 Add "Contrast" to FSN and attribute 
 */
public class INFRA5204_AddContrastAttributeAndFsn extends BatchFix {

	private Set<String> exclusionTexts;
	private RelationshipTemplate addTemplate;
	private RelationshipTemplate matchTemplate;
	
	protected INFRA5204_AddContrastAttributeAndFsn(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		INFRA5204_AddContrastAttributeAndFsn fix = new INFRA5204_AddContrastAttributeAndFsn(null);
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
		//INFRA-5204
		subsetECL = "<< 420040002|Fluoroscopic angiography (procedure)|";
		addTemplate = new RelationshipTemplate(gl.getConcept("424361007|Using substance (attribute)|"), 
				gl.getConcept("385420005|Contrast media (substance)|"));
		matchTemplate = new RelationshipTemplate(METHOD, 
				gl.getConcept("312275004|Fluoroscopic imaging - action (qualifier value)|"));
		
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
			switch (d.getType()) {
				case FSN : changesMade += modifyFSN(t, c);
							break;
				case SYNONYM :  if (!isExcluded(d.getTerm().toLowerCase())) {
									String replacement = d.getTerm() + " with contrast";
									replaceDescription(t, c, d, replacement, null);
									changesMade++;
								};
								break;
				default : 
			}
		}
		return changesMade;
	}

	private int modifyFSN(Task t, Concept c) throws TermServerScriptException {
		String[] fsnParts = SnomedUtils.deconstructFSN(c.getFsn());
		String replacement = fsnParts[0] + " with contrast " + fsnParts[1];
		replaceDescription(t, c, c.getFSNDescription(), replacement, null);
		return CHANGE_MADE;
	}

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		boolean matchFound = false;
		//Find the groupId of the matching relationship template
		for (Relationship r : c.getRelationships(matchTemplate, ActiveState.ACTIVE)) {
			matchFound = true;
			Relationship attrib = addTemplate.createRelationship(c, r.getGroupId(), null);
			changesMade += addRelationship(t, c, attrib);
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
