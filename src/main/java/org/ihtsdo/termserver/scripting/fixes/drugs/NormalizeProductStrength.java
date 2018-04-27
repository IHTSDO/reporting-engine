package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
DRUGS-489
Existing remodeled concepts need to be reviewed and updated as needed to comply with normalized strength expressions of metric units:

Use picogram if value is <1000; if if > then convert to nanogram
Use nanogram if value is <1000; if > then convert to microgram
Use microgram if value is <1000; if > then convert to milligram
Use milligram if value is <1000; if > then convert to gram
*/
public class NormalizeProductStrength extends DrugBatchFix implements RF2Constants {
	
	Concept [] units = new Concept [] { PICOGRAM, NANOGRAM, MICROGRAM, MILLIGRAM, GRAM };
	
	protected NormalizeProductStrength(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		NormalizeProductStrength fix = new NormalizeProductStrength(null);
		try {
			fix.additionalReportColumns = "Num/Den, Current Unit, New Unit, Role Group";
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
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		try {
			int changesMade = normalizeProductStrength(task, loadedConcept);
			if (changesMade > 0) {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun?"Dry run updating":"Updating") + " state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			}
			return changesMade;
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to remodel " + concept + " due to " + e.getClass().getSimpleName()  + " - " + e.getMessage());
			e.printStackTrace();
		}
		return 0;
	}

	private int normalizeProductStrength(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		//For any numerator, check if the unit is > 1000 and consider switching to the next unit
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			Concept strength = getTarget(c, new Concept[] { HAS_PRES_STRENGTH_VALUE, HAS_CONC_STRENGTH_VALUE}, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
			if (strength != null && DrugUtils.getConceptAsNumber(strength) >= 1000) {
				Concept unit = getTarget(c, new Concept[] { HAS_PRES_STRENGTH_UNIT, HAS_CONC_STRENGTH_UNIT}, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
				int currentIdx =  ArrayUtils.indexOf(units, unit);
				if (currentIdx != NOT_SET) {
					Concept newUnit = units[currentIdx + 1];
					if (!quiet) {
						report (t, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Numerator", unit, newUnit, g);
					}
				}
			}
		}
		
		//Same for denominator
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			Concept volume = getTarget(c, new Concept[] { HAS_PRES_STRENGTH_DENOM_VALUE, HAS_CONC_STRENGTH_DENOM_VALUE}, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
			if (volume!= null && DrugUtils.getConceptAsNumber(volume) >= 1000) {
				Concept unit = getTarget(c, new Concept[] { HAS_PRES_STRENGTH_DENOM_UNIT, HAS_CONC_STRENGTH_DENOM_UNIT}, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
				int currentIdx =  ArrayUtils.indexOf(units, unit);
				if (currentIdx != NOT_SET) {
					Concept newUnit = units[currentIdx + 1];
					if (!quiet) {
						report (t, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Denominator", unit, newUnit, g);
					}
				}
			}
		}
		return changesMade;
	}
	
	//Where we have multiple potential responses eg concentration or presentation strength, return the first one found given the 
	//order specified by the array
	private Concept getTarget(Concept c, Concept[] types, int groupId, CharacteristicType charType) throws TermServerScriptException {
		for (Concept type : types) {
			List<Relationship> rels = c.getRelationships(charType, type, groupId);
			if (rels.size() > 1) {
				TermServerScript.warn(c + " has multiple " + type + " in group " + groupId);
			} else if (rels.size() == 1) {
				//This might not be the full concept, so recover it fully from our loaded cache
				return gl.getConcept(rels.get(0).getTarget().getConceptId());
			}
		}
		return null;
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<>();  //We want to process in the same order each time, in case we restart and skip some.
		//setQuiet(true);
		for (Concept c : PHARM_BIO_PRODUCT.getDescendents(NOT_SET)) {
			SnomedUtils.populateConceptType(c);
			if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				if (normalizeProductStrength(null, c) > 0) {
					//processMe.add(c);
				}
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		//setQuiet(false);
		return asComponents(processMe);
	}

	@Override
	protected List<Concept> loadLine(String[] lineItems) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
