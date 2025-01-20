package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


/*
For DRUG-422, DRUG-431
Driven by a text file of concepts, move specified concepts to exist under
a parent concept.
*/
public class ReplaceParentsDriven extends BatchFix implements ScriptConstants{
	
	Relationship newParentRel;
	String newParent = "763158003"; // |Medicinal product (product)| 
	
	protected ReplaceParentsDriven(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceParentsDriven fix = new ReplaceParentsDriven(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.additionalReportColumns = "ACTION_DETAIL, DEF_STATUS, PARENT_COUNT, ATTRIBUTE_COUNT";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		Concept parentConcept =  gl.getConcept(newParent);
		newParentRel = new Relationship(null, IS_A, parentConcept, 0);
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = replaceParents(t, loadedConcept);
		
		//All concepts should be fully defined, if possible
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			if (countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP) > 0) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changesMade++;
				report(t, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report(t, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - no attributes!");
			}
		}
		
		updateConcept(t, loadedConcept, info);
		return changesMade;
	}

	private int replaceParents(Task task, Concept loadedConcept) throws TermServerScriptException {
		
		int changesMade = 0;
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																		IS_A,
																		ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP));
		
		String semTag = SnomedUtils.deconstructFSN(loadedConcept.getFsn())[1];
		switch (semTag) {
			case "(medicinal product form)" : loadedConcept.setConceptType(ConceptType.MEDICINAL_PRODUCT_FORM);
												break;
			case "(product)" : loadedConcept.setConceptType(ConceptType.PRODUCT);
								break;
			case "(medicinal product)" : loadedConcept.setConceptType(ConceptType.MEDICINAL_PRODUCT);
										 break;
			case "(clinical drug)" : loadedConcept.setConceptType(ConceptType.CLINICAL_DRUG);
										break;
			default : loadedConcept.setConceptType(ConceptType.UNKNOWN);
		}
		
		boolean replacementNeeded = true;
		for (Relationship parentRel : parentRels) {
			if (!parentRel.equals(newParentRel)) {
				removeParentRelationship (task, parentRel, loadedConcept, newParentRel.getTarget().toString(), null);
				changesMade++;
			} else {
				replacementNeeded = false;
			}
		}
		
		if (replacementNeeded) {
			Relationship thisNewParentRel = newParentRel.clone(null);
			thisNewParentRel.setSource(loadedConcept);
			loadedConcept.addRelationship(thisNewParentRel);
			changesMade++;
			String msg = "Single parent set to " + newParent;
			report(task, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, msg, loadedConcept.getDefinitionStatus().toString(), parentCount, attributeCount);
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		return Collections.singletonList(c);
	}

}
