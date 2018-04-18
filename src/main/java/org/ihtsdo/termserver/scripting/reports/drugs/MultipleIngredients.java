package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Report to check the number of ingredients matches the number of " and " in the FSN
 * @author Peter
 *
 */
public class MultipleIngredients extends TermServerReport {
	
	ConceptType[] validTypes = new ConceptType[] { ConceptType.MEDICINAL_PRODUCT, ConceptType.MEDICINAL_PRODUCT_FORM };
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		MultipleIngredients report = new MultipleIngredients();
		try {
			report.additionalReportColumns = "Ingredient Count";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.finMultipleModifications();
		} catch (Exception e) {
			info("Failed to produce MultipleIngredients due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void finMultipleModifications() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendents(NOT_SET)) {
			DrugUtils.setConceptType(c);
			if (SnomedUtils.isConceptType(c, validTypes)) {
				int ingredientCount = DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP).size();
				String fsn = c.getFsn().replaceAll("\\+", " and ");
				int fsnCount = fsn.split(" and ").length;
				if (fsnCount != ingredientCount) {
					incrementSummaryInformation("Issues found");
					report (c, ingredientCount);
				}
			}
		}
	}
}
