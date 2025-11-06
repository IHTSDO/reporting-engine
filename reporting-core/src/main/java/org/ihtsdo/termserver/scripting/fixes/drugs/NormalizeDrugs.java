package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
For DRUGS-422, DRUGS-434, DRUGS-435, DRUGS-447
Driven by a text file of concepts, move specified concepts to exist under
a new parent concept and remodel Terms.
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NormalizeDrugs extends DrugBatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(NormalizeDrugs.class);

	Relationship newParentRel;
	String newParent = "763158003"; // |Medicinal product (product)| 
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	protected NormalizeDrugs(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NormalizeDrugs fix = new NormalizeDrugs(null);
		try {
			fix.reportNoChange = true;
			fix.populateEditPanel = true;
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
		parentConcept.setFsn("Medicinal product (product)");
		newParentRel = new Relationship(null, IS_A, parentConcept, 0);
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
				IS_A,
				ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(SnomedUtils.countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP));
		int changes = replaceParents (t, loadedConcept, newParentRel, new String[] { parentCount, attributeCount });
		
		if (!loadedConcept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
			changes += termGenerator.ensureTermsConform(t, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
		}
		
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			
			int activeIngredientCount = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, Concept.HAS_ACTIVE_INGRED, ActiveState.ACTIVE).size();
			int doseFormCount = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, Concept.HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE).size();
			boolean canFullyDefine = true;
			
			if (loadedConcept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) || loadedConcept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT)) {
				if (activeIngredientCount == 0) {
					canFullyDefine = false;
				}
				if (loadedConcept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM) && doseFormCount == 0) {
					canFullyDefine = false;
				}
			} else {
				canFullyDefine = SnomedUtils.countAttributes(loadedConcept, CharacteristicType.STATED_RELATIONSHIP) > 0;
			}
			
			
			if (canFullyDefine) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changes++;
				report(t, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report(t, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - insufficient attributes!");
			}
		}
		
		updateConcept(t, loadedConcept, info);
		return changes;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		return Collections.singletonList(c);
	}
}
