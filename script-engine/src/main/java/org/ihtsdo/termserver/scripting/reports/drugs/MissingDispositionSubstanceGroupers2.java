package org.ihtsdo.termserver.scripting.reports.drugs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * INFRA-14594
 * Report to find missing substance groupers using an alternative algorithm:
 * substances in < 766739005 |Substance categorized by disposition (substance)| that have 'mechanism of action'
 * in their terming and that are NOT used to define concepts in << 766779001 |Medicinal product categorized by disposition (product)|
 * as the target of a Has active ingredient (attribute) relationship.
 */
public class MissingDispositionSubstanceGroupers2 extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingDispositionSubstanceGroupers2.class);

	public static void main(String[] args) throws TermServerScriptException {
		MissingDispositionSubstanceGroupers2 report = new MissingDispositionSubstanceGroupers2();
		try {
			ReportSheetManager.setTargetFolderId(GFOLDER_DRUGS_MISSING);
			report.additionalReportColumns="FSN,Used by";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runReport();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void runReport() throws TermServerScriptException {
		Set<Concept> substancesUsedToDefineDrugsCatagorizedByDisposition = getSubstancesUsedToDefineDrugsCatagorizedByDisposition();

		for (Concept substance : getMechanismOfActionSubstances()) {
			if (!substancesUsedToDefineDrugsCatagorizedByDisposition.contains(substance)) {
				report(PRIMARY_REPORT, substance);
			}
		}
	}

	private Set<Concept> getSubstancesUsedToDefineDrugsCatagorizedByDisposition() throws TermServerScriptException {
		Set<Concept> substancesUsedToDefineDrugsCatagorizedByDisposition = new HashSet<>();
		for (Concept c : findConcepts("<< 766779001 |Medicinal product categorized by disposition (product)|")) {
			substancesUsedToDefineDrugsCatagorizedByDisposition.addAll(DrugUtils.getIngredients(c, CharacteristicType.INFERRED_RELATIONSHIP));
		}
		return substancesUsedToDefineDrugsCatagorizedByDisposition;
	}

	private Set<Concept> getMechanismOfActionSubstances() throws TermServerScriptException {
		return findConcepts("< 766739005 |Substance categorized by disposition (substance)|")
				.stream()
				.filter(c -> c.getDescriptions(ActiveState.ACTIVE).stream()
						.anyMatch(d -> d.getTerm().contains("mechanism of action")))
				.sorted(SnomedUtils::compareSemTagFSN)
				.collect(Collectors.toCollection(LinkedHashSet::new));
	}

}
