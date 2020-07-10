package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * DRUGS-463 A report to identify Medicinal Products where the 
 * active ingredient is itself a modification of another substance
 */
public class ModifiedActiveIngredients extends TermServerReport {
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		ModifiedActiveIngredients report = new ModifiedActiveIngredients();
		try {
			report.additionalReportColumns = "Ingredient, ModificationOf";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.runModifiedActiveIngredientsReport();
		} catch (Exception e) {
			info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("373873005");  // |Pharmaceutical / biologic product (product)|
	}

	private void runModifiedActiveIngredientsReport() throws TermServerScriptException {
		Collection<Concept> subHierarchyConcepts = subHierarchy.getDescendents(NOT_SET);
		for (Concept c : subHierarchyConcepts) {
			//We're only interested in Medicinal Products 
			if (c.isActive() && c.getFsn().contains("(medicinal product)")) {
				//Get all active ingredients and check them for having "Is Modification Of"
				Set<Relationship> ingredRels = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
				for (Relationship ingredRel : ingredRels) {
					Concept ingred = ingredRel.getTarget();
					//Does that ingredient declare that it's a modification of something?
					Set<Relationship> modRels = ingred.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE);
					for (Relationship modRel : modRels) {
						report (c, modRel.getSource().toString(), modRel.getTarget().toString());
						incrementSummaryInformation("Modifications");
					}
				}
			}
		}
		addSummaryInformation("Concepts checked", subHierarchyConcepts.size());
	}

}