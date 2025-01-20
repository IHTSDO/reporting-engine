package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * DRUGS-463 A report to identify Medicinal Products where the 
 * active ingredient is itself a modification of another substance
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModifiedActiveIngredients extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModifiedActiveIngredients.class);

	public static void main(String[] args) throws TermServerScriptException {
		ModifiedActiveIngredients report = new ModifiedActiveIngredients();
		try {
			report.additionalReportColumns = "Ingredient, ModificationOf";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runModifiedActiveIngredientsReport();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report ", e);
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("373873005");  // |Pharmaceutical / biologic product (product)|
	}

	private void runModifiedActiveIngredientsReport() throws TermServerScriptException {
		Collection<Concept> subHierarchyConcepts = subHierarchy.getDescendants(NOT_SET);
		for (Concept c : subHierarchyConcepts) {
			//We're only interested in Medicinal Products 
			if (c.isActiveSafely() && c.getFsn().contains("(medicinal product)")) {
				//Get all active ingredients and check them for having "Is Modification Of"
				Set<Relationship> ingredRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
				for (Relationship ingredRel : ingredRels) {
					Concept ingred = ingredRel.getTarget();
					//Does that ingredient declare that it's a modification of something?
					Set<Relationship> modRels = ingred.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE);
					for (Relationship modRel : modRels) {
						report(c, modRel.getSource().toString(), modRel.getTarget().toString());
						incrementSummaryInformation("Modifications");
					}
				}
			}
		}
		addSummaryInformation("Concepts checked", subHierarchyConcepts.size());
	}

}
