package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;

/*
DRUGS-481 Update of Clinical Drugs authored for the 20180131 release:
	Add unit of presentation attribute
	Replace active ingredient with precise active ingredient
	Generate terming based on attributes
	Set parent to Medicinal product
	Make sufficiently defined
Select all product concepts that have a Boss but to NOT have "precisely" in the FSN
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CDUpdate extends DrugBatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(CDUpdate.class);

	static final int MINIMAL_DRUG_MODELLING = 8;
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);
	
	protected CDUpdate(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CDUpdate fix = new CDUpdate(null);
		try {
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			//We won't include the project export in our timings
			fix.startTimer();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		try {
			int changesMade = remodelConcept(t, loadedConcept);
			if (changesMade > 0) {
				updateConcept(t, loadedConcept, info);
			}
			return changesMade;
		} catch (Exception e) {
			report(t, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to remodel " + concept + " due to " + e.getClass().getSimpleName()  + " - " + e.getMessage());
			LOGGER.error("Exception encountered",e);
		}
		return 0;
	}

	private int remodelConcept(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		
		//Throw an error if the number of inferred and stated relationships are different
		Set<Relationship> stated = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		if (stated.size() < MINIMAL_DRUG_MODELLING) {
			throw new TermServerScriptException("Insufficient attributes for correct modelling");
		}
		
		//Copy the dose form as a unit of presentation, if we don't have any
		if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_UNIT_OF_PRESENTATION, ActiveState.ACTIVE).size() == 0) {
			Relationship doseFormRel = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE).iterator().next();
			Relationship unitPresRel = doseFormRel.clone(null);
			//incrementSummaryInformation("Dose form: " + unitPres.getTarget().getFsn());
			unitPresRel.setType(HAS_UNIT_OF_PRESENTATION);
			unitPresRel.setTarget(mapDoseFormToUnitOfPresentation(doseFormRel.getTarget()));
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, unitPresRel);
			changesMade++;
			c.addRelationship(unitPresRel);
		}

		//Remodel each ingredient
		for (Relationship ingredRel : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
			changesMade += remodel(t, c, ingredRel);
		}
		
		changesMade += setSingleParent(t, c, MEDICINAL_PRODUCT);
		
		//Set this concept to be sufficiently defined if required
		if (c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE)) {
			c.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);
			changesMade++;
			report(t, c, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept set to be sufficiently defined");
		}
		
		//We're definitely going to have everything we need in the stated form, and in fact the inferred will be wrong because we haven't changed it.
		changesMade += termGenerator.ensureTermsConform(t, c, null, CharacteristicType.STATED_RELATIONSHIP);
		return changesMade;
	}
	

	private Concept mapDoseFormToUnitOfPresentation(Concept doseForm) throws TermServerScriptException {
		String fsn = doseForm.getFsn();
		if (fsn.contains("tablet")) {
			return gl.getConcept("732936001"); // |Tablet (unit of presentation)|
		} else if (fsn.contains("capsule")) {
			return gl.getConcept("732937005"); // |Capsule (unit of presentation)|
		} else if (fsn.contains("suppository")) {
			return gl.getConcept("733019001"); // |Suppository (unit of presentation)|
		} else if (fsn.contains("pessary")) {
			return gl.getConcept("733007009"); // |Pessary (unit of presentation)|
		}
		throw new IllegalArgumentException("Unable to find Unit of Presentation for " + doseForm);
	}

	private int setSingleParent(Task t, Concept c, Concept parent) throws TermServerScriptException {
		int changesMade = 0;
		Relationship newParentRel = new Relationship (c, IS_A, parent, UNGROUPED);
		Set<Relationship> parentRels = new HashSet<> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
														IS_A,
														ActiveState.ACTIVE));
		boolean replacementNeeded = true;
		for (Relationship parentRel : parentRels) {
			if (!parentRel.equals(newParentRel)) {
				removeParentRelationship (t, parentRel, c, parent.toString(), null);
				changesMade++;
			} else {
				replacementNeeded = false;
			}
		}
		
		if (replacementNeeded) {
			Relationship thisNewParentRel = newParentRel.clone(null);
			thisNewParentRel.setSource(c);
			c.addRelationship(thisNewParentRel);
			changesMade++;
			String msg = "Single parent set to " + parent;
			report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg);
		}
		return changesMade;
	}

	private int remodel(Task t, Concept c, Relationship ingredRel) throws TermServerScriptException {
		int changesMade = 0;
		int groupId = ingredRel.getGroupId();
		//Check we're not in group 0
		if (ingredRel.getGroupId() == UNGROUPED) {
			throw new IllegalArgumentException("Ungrouped ingredient detected: " + ingredRel.toLongString());
		}
		
		//Swap the active ingredient for a precise ingredient
		Relationship preciseIngred = new Relationship (c, HAS_PRECISE_INGRED, ingredRel.getTarget(), groupId);
		c.addRelationship(preciseIngred);
		
		String msg;
		if (ingredRel.isReleased()) {
			ingredRel.setActive(false);
			msg = "Inactivated";
		} else {
			c.removeRelationship(ingredRel);
			msg = "Deleted";
		}
		msg += " active ingredient and replaced with " + preciseIngred;
		report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_REPLACED, msg);
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept c : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			Set<Relationship> bossRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_BOSS, ActiveState.ACTIVE);
			if (bossRels.size() > 0 && !c.getFsn().contains("precisely") && c.getFsn().contains("(clinical drug)")) {
				c.setConceptType(ConceptType.CLINICAL_DRUG);
				processMe.add(c);
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		return asComponents(processMe);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
