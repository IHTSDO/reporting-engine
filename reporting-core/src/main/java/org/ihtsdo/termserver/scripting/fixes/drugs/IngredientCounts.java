package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugUtils;

/*
For DRUGS-482
Add ingredient counts where required.  Algorithm described in DRUGS-476.
*/
public class IngredientCounts extends DrugBatchFix implements ScriptConstants{


	protected IngredientCounts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		IngredientCounts fix = new IngredientCounts(null);
		try {
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.populateEditPanel = false;
			fix.populateTaskDescription = true;
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		try {
			int changes = assignIngredientCounts(task, loadedConcept, CharacteristicType.INFERRED_RELATIONSHIP);
			if (changes > 0) {
				updateConcept(task, loadedConcept, info);
			}
			return changes;
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return NO_CHANGES_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//List<Concept> processMe = new ArrayList<>(Collections.singletonList(gl.getConcept("377209001")));
		List<Concept> processMe = new ArrayList<>();
		for (Concept drug : MEDICINAL_PRODUCT.getDescendants(NOT_SET)) {
			DrugUtils.setConceptType(drug);
			switch (drug.getConceptType()) {
				case CLINICAL_DRUG: if (drug.getFsn().contains("precisely")) {
										processMe.add(drug);
				}
					break;
				case MEDICINAL_PRODUCT:
				case MEDICINAL_PRODUCT_FORM: if (drug.getFsn().contains("only")) {
					processMe.add(drug);
				}
					break;
				default:
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return asComponents(processMe);
	}
}
