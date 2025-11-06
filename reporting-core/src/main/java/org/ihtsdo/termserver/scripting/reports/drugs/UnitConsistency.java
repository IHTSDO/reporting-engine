package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * DRUGS-289
 * Report to ensure that any given ingredient across all products is always represented using the same units.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnitConsistency extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(UnitConsistency.class);

	//For each ingredient, for each unit, list the products that use it.
	Map<Concept, Map<Concept, List<Concept>>> ingredientUnitExamples = new HashMap<>();
	Concept[] ingredient_types = new Concept[] { HAS_ACTIVE_INGRED, HAS_PRECISE_INGRED };
	CharacteristicType cType = CharacteristicType.INFERRED_RELATIONSHIP;
	Concept[] unit_types = new Concept[] { HAS_PRES_STRENGTH_UNIT, HAS_CONC_STRENGTH_UNIT };
	
	public static void main(String[] args) throws TermServerScriptException {
		UnitConsistency report = new UnitConsistency();
		try {
			report.additionalReportColumns = "Using Ingredient, With Unit, Among, Compared to, instances, For Example";
			report.runStandAlone = false;  //We may need the real data from termserver due to problems with switching relationships ids and groupid at the same time
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.findAllIngredientUnits();
			report.reportInconsistentUnits();
		} catch (Exception e) {
			LOGGER.info("Failed to produce UnitConsistency Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void findAllIngredientUnits() throws TermServerScriptException {
		LOGGER.info("Finding all ingredient units");
		for (Concept c : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			//We're only interested in clinical drugs if we're talking about units
			SnomedUtils.populateConceptType(c);
			if (!c.getConceptType().equals(ConceptType.CLINICAL_DRUG)) {
				continue;
			}
			//Find the ingredients in each group 
			for (RelationshipGroup g : c.getRelationshipGroups(cType)) {
				Concept i = getTarget (c, ingredient_types, g.getGroupId(), cType);
				
				//If we don't find an ingredient, we might have got confusion with a relationship moving group and inactivating.
				//Load the concept in this case and retry
				if (i == null && g.getGroupId() > 0) {
					LOGGER.warn ("Possible inactivation confusion in " + c + " loading from termserver");
					c = loadConcept(c, project.getBranchPath());
					i = getTarget (c, ingredient_types, g.getGroupId(), cType);
				}
				
				if (i != null) {
					//Now what is the unit used here?
					Concept unit = getTarget (c, unit_types, g.getGroupId(), cType);
					
					if (unit == null) {
						LOGGER.warn (c + " has no unit specified for ingredient " + i + " in group " + g.getGroupId());
					} else {
						//Have we seen this ingredient before?
						Map<Concept, List<Concept>> unitExamples = ingredientUnitExamples.get(i);
						if (unitExamples == null) {
							unitExamples = new HashMap<>();
							ingredientUnitExamples.put(i, unitExamples);
						}
						//Have we seen this unit before?
						List<Concept> examples = unitExamples.get(unit);
						if (examples == null) {
							examples = new ArrayList<>();
							unitExamples.put(unit, examples);
						}
						//Add this example
						examples.add(c);
					}
				}
			}
		}
	}
	
	private Concept getTarget(Concept c, Concept[] types, int groupId, CharacteristicType charType) throws TermServerScriptException {
		Set<Relationship> rels = new HashSet<>();
		String typeString = "";
		for (Concept type : types) {
			rels.addAll(c.getRelationships(charType, type, groupId));
			typeString += type.getFsn() + " ";
		}
		if (rels.size() > 1) {
			LOGGER.warn("{} has multiple {} in group {}", c, typeString, groupId);
		} else if (rels.size() == 1) {
			Concept target = rels.iterator().next().getTarget();
			//This might not be the full concept, so recover it fully from our loaded cache
			return gl.getConcept(target.getConceptId());
		}
		return null;
	}

	private void reportInconsistentUnits() throws TermServerScriptException {
		//For every ingredient, if there are multiple units reports, report the minority
		for (Concept ingredient : ingredientUnitExamples.keySet()) {
			Map<Concept, List<Concept>> unitExamples = ingredientUnitExamples.get(ingredient);
			if (unitExamples.size() > 1) {
				Concept mostUsedUnit = findMostUsedUnit(unitExamples);
				int mostUsedCount = unitExamples.get(mostUsedUnit).size();
				for (Concept unit : unitExamples.keySet()) {
					if (!unit.equals(mostUsedUnit)) {
						for (Concept example : unitExamples.get(unit)) {
							report(example, ingredient, unit, unitExamples.get(unit).size(),  mostUsedUnit, mostUsedCount, unitExamples.get(mostUsedUnit).get(0));
						}
					}
				}
			}
		}
	}

	private Concept findMostUsedUnit(Map<Concept, List<Concept>> unitExamples) {
		Concept mostUsedUnit = null;
		for (Concept unit : unitExamples.keySet()) {
			if (mostUsedUnit == null || unitExamples.get(unit).size() > unitExamples.get(mostUsedUnit).size()) {
				mostUsedUnit = unit;
			}
		}
		return mostUsedUnit;
	}

}
