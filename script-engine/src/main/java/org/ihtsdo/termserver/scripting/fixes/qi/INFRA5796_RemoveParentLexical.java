package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * INFRA-5796
 * First, subtypes of 12456005 |Iatrogenic disorder (disorder)| that do not have
 *  the word iatrogenic in the FSN should not be subtypes of 
 *  12456005 |Iatrogenic disorder (disorder)| and should have that IS-A parent 
 *  relationship inactivated.  There are 27 of these. 
 *
 */

public class INFRA5796_RemoveParentLexical extends BatchFix {
	String ecl = "<< 12456005 |Iatrogenic disorder (disorder)| ";
	String searchTerm = "iatrogenic";
	
	protected INFRA5796_RemoveParentLexical(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		INFRA5796_RemoveParentLexical fix = new INFRA5796_RemoveParentLexical(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.populateTaskDescription = false;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		changesMade += removeParent(t, loadedConcept, gl.getConcept("12456005 |Iatrogenic disorder (disorder)|"));
		
		if (changesMade > 0 
				&& loadedConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) 
				&& SnomedUtils.countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP) == 0 
				&& loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE).size() == 1) {
			
			loadedConcept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			report(t, c, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Single parent and no attributes must be Primitive");
		}
		
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int removeParent(Task t, Concept c, Concept removedParent) throws TermServerScriptException {
		int changesMade = 0;
		//How many parents do we have?
		if (c.getParents(CharacteristicType.STATED_RELATIONSHIP).size() == 1) {
			report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Attempted to orphan child" , c.getParents(CharacteristicType.STATED_RELATIONSHIP).iterator().next());
			return NO_CHANGES_MADE;
		}
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE)) {
			if (r.getTarget().equals(removedParent)) {
				changesMade += removeRelationship(t, c, r);
			}
		}
		return changesMade;
	}

	private boolean containsTargetTerm(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		return fsn.contains(searchTerm);
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> concepts = findConcepts(ecl).stream()
				.filter(c -> !containsTargetTerm(c))
				.collect(Collectors.toList());
		
		return new ArrayList<>(concepts);
	}

}
