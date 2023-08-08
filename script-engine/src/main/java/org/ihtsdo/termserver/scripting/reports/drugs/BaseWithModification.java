package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Investigating cases where we have a product with both a base and a modification of the same substances.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseWithModification extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(BaseWithModification.class);

	Concept[] type = new Concept[] {IS_MODIFICATION_OF};
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		BaseWithModification report = new BaseWithModification();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.findBaseWithModifications();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MissingAttributeReport due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void findBaseWithModifications() throws TermServerScriptException {
		for (Concept c : PHARM_BIO_PRODUCT.getDescendents(NOT_SET)) {
			List<Concept> ingredients = DrugUtils.getIngredients(c, CharacteristicType.STATED_RELATIONSHIP);
			//We won't consider the transitive case, just immediate modifications
			for (Concept ingredient : ingredients) {
				//Are we a modification of a base which is also an ingredient?
				Concept base = SnomedUtils.getTarget(ingredient, type, UNGROUPED, CharacteristicType.STATED_RELATIONSHIP);
				if (ingredients.contains(base)) {
					report (c, ingredient, base);
					incrementSummaryInformation("Base with modification found");
				}
			}
		}
	}

}
