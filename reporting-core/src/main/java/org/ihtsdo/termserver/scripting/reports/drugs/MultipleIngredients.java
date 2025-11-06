package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.PrintStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * DRUGS-494
 * Report to check the number of ingredients matches the number of " and " in the FSN
 * @author Peter
 *
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipleIngredients extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultipleIngredients.class);

	ConceptType[] validTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_FORM, ConceptType.CLINICAL_DRUG };
	String[] falsePositives = new String[] { "gastro-resistant and prolonged-release" };
	
	public static void main(String[] args) throws TermServerScriptException {
		MultipleIngredients report = new MultipleIngredients();
		try {
			report.additionalReportColumns = "Ingredient Count";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.finMultipleModifications();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MultipleIngredients due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void finMultipleModifications() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendants(NOT_SET)) {
			DrugUtils.setConceptType(c);
			if (SnomedUtils.isConceptType(c, validTypes)) {
				int ingredientCount = DrugUtils.getIngredients(c, CharacteristicType.STATED_RELATIONSHIP).size();
				String fsn = c.getFsn().replaceAll("\\+", " and ");
				//Remove any fragments of terms that cause false positives
				for (String falsePositive : falsePositives) {
					fsn = fsn.replace(falsePositive, "");
				}
				int fsnCount = fsn.split(" and ").length;
				if (fsnCount != ingredientCount) {
					incrementSummaryInformation("Issues found");
					report(c, ingredientCount);
				}
			}
		}
	}
}
