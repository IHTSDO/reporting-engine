package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;

import us.monoid.json.JSONObject;

/*
For DRUGS-422, DRUGS-434, DRUGS-435, DRUGS-447
Driven by a text file of concepts, move specified concepts to exist under
a new parent concept and remodel Terms.
*/
public class NormalizeDrugs extends DrugBatchFix implements RF2Constants{
	
	Relationship newParentRel;
	String newParent = "763158003"; // |Medicinal product (product)| 
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	protected NormalizeDrugs(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
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
			fix.startTimer();
			fix.processFile();
			info ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
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
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
				IS_A,
				ActiveState.ACTIVE));
		String parentCount = Integer.toString(parentRels.size());
		String attributeCount = Integer.toString(countAttributes(loadedConcept));
		int changes = replaceParents (task, loadedConcept, newParentRel, new String[] { parentCount, attributeCount });
		
		if (!loadedConcept.getConceptType().equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
			changes += termGenerator.ensureDrugTermsConform(task, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
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
				canFullyDefine = countAttributes(loadedConcept) > 0;
			}
			
			
			if (canFullyDefine) {
				loadedConcept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
				changes++;
				report (task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept marked as fully defined");
			} else {
				report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Unable to mark fully defined - insufficient attributes!");
			}
		}
		
		try {
			String conceptSerialised = gson.toJson(loadedConcept);
			debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
			}
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changes;
	}

	private Integer countAttributes(Concept c) {
		int attributeCount = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributeCount++;
			}
		}
		return attributeCount;
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0]);
		return Collections.singletonList(c);
	}
}
