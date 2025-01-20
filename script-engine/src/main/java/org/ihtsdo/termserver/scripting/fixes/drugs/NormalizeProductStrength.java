package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.NotImplementedException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.*;

/*
DRUGS-489
Existing remodeled concepts need to be reviewed and updated as needed to comply with normalized strength expressions of metric units:

Use picogram if value is <1000; if if > then convert to nanogram
Use nanogram if value is <1000; if > then convert to microgram
Use microgram if value is <1000; if > then convert to milligram
Use milligram if value is <1000; if > then convert to gram

Or if value is < 1 switch to the next smaller unit and multiple the value by 1000.
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NormalizeProductStrength extends DrugBatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(NormalizeProductStrength.class);

	protected NormalizeProductStrength(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NormalizeProductStrength fix = new NormalizeProductStrength(null);
		try {
			fix.additionalReportColumns = "Num/Den, Current Unit, New Strength, New Unit, Role Group";
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
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = normalizeProductStrength(t, loadedConcept);
		if (changesMade > 0) {
			changesMade += termGenerator.ensureTermsConform(t, loadedConcept, CharacteristicType.STATED_RELATIONSHIP);
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}
	
	private int normalizeProductStrength(Task t, Concept c) throws TermServerScriptException {
		//Running the two types separately because pattern 2a uses both
		int changesMade = normalizeProductStrength(t, c, new Concept[] { HAS_PRES_STRENGTH_VALUE},
				new Concept[] { HAS_PRES_STRENGTH_UNIT} );
		changesMade += normalizeProductStrength(t, c, new Concept[] {  HAS_CONC_STRENGTH_VALUE},
				new Concept[] { HAS_CONC_STRENGTH_UNIT} );
		changesMade += normalizeProductStrength(t, c, new Concept[] { HAS_PRES_STRENGTH_DENOM_VALUE},
				new Concept[] { HAS_PRES_STRENGTH_DENOM_UNIT} );
		changesMade += normalizeProductStrength(t, c, new Concept[] { HAS_CONC_STRENGTH_DENOM_VALUE},
				new Concept[] { HAS_CONC_STRENGTH_DENOM_UNIT} );
		return changesMade;
	}

	private int normalizeProductStrength(Task t, Concept c, Concept[] strengthTypes, Concept[] unitTypes) throws TermServerScriptException {
		int changesMade = 0;
		//For any numerator, check if the unit is > 1000 and consider switching to the next unit
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			Relationship strengthRel = getTargetRel(c, strengthTypes, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
			if (strengthRel != null) {
				if (true)
					throw new NotImplementedException();
				double strengthNumber = 0;  //When you read this, recover the strength from the concrete value
				Relationship unitRel = getTargetRel(c, unitTypes, g.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
				StrengthUnit strengthUnit = new StrengthUnit(strengthNumber, unitRel.getTarget());
				if (DrugUtils.normalizeStrengthUnit(strengthUnit)) {
					if (!quiet) {
						remodelConcept (t, c, strengthRel, strengthUnit.getStrengthStr(), unitRel, strengthUnit.getUnit());
						report(t, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, strengthNumber + " " + unitRel.getTarget() + " --> " + strengthUnit, g);
					}
					changesMade++;
				}
			}
		}
		return changesMade;
	}
	
	private void remodelConcept(Task t, Concept c, Relationship strengthRel, String newStrengthStr,
			Relationship unitRel, Concept newUnit) throws TermServerScriptException {
		ConcreteValue cv = new ConcreteValue(ConcreteValue.ConcreteValueType.DECIMAL, newStrengthStr);
		replaceRelationship(t, c, strengthRel.getType(), null, cv, strengthRel.getGroupId(), RelationshipTemplate.Mode.UNIQUE_TYPE_IN_THIS_GROUP);
		replaceRelationship(t, c, unitRel.getType(), newUnit, unitRel.getGroupId(), false);
	}

	//Where we have multiple potential responses eg concentration or presentation strength, return the first one found given the
	//order specified by the array
	private Relationship getTargetRel(Concept c, Concept[] types, int groupId, CharacteristicType charType) {
		for (Concept type : types) {
			Set<Relationship> rels = c.getRelationships(charType, type, groupId);
			if (rels.size() > 1) {
				LOGGER.warn("{} has multiple {} in group {}", c, type, groupId);
			} else if (rels.size() == 1) {
				//This might not be the full concept, so recover it fully from our loaded cache
				return rels.iterator().next();
			}
		}
		return null;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Concept> processMe = new ArrayList<>();  //We want to process in the same order each time, in case we restart and skip some.
		setQuiet(true);
		for (Concept c : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			SnomedUtils.populateConceptType(c);
			if (c.getConceptType().equals(ConceptType.CLINICAL_DRUG) && normalizeProductStrength(null, c) > 0) {
				processMe.add(c);
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		setQuiet(false);
		LOGGER.info("{} concepts to process", processMe.size());
		return asComponents(processMe);
	}
}
