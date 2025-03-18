package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.snomed.otf.script.dao.ReportSheetManager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * DRUGS-506 List Substances that have more than one modification attribute
 * DRUGS-476 Similar, list CDs that have ingredient which has multiple modifications
 */
public class MultipleModifications extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultipleModifications.class);

	Set<Concept> substancesWithMultipleModifications = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		MultipleModifications report = new MultipleModifications();
		try {
			ReportSheetManager.setTargetFolderId("1hYd96nzfB35ggffWR_SdPbybpmzynlI6"); //Drugs
			report.additionalReportColumns = "FSN, Attribute Type, Ingredient Value, Bases";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.findMultipleModifications();
			report.findCDsWithMultipleModifications();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void findMultipleModifications() throws TermServerScriptException {
		for (Concept c : SUBSTANCE.getDescendants(NOT_SET)) {
			List<Concept> bases = getBases(c);
			if (bases.size() > 1) {
				substancesWithMultipleModifications.add(c);
			}
		}
	}

	private List<Concept> getBases(Concept c) {
		return c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE)
				.stream()
				.map(Relationship::getTarget)
				.toList();
	}

	private void findCDsWithMultipleModifications() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendants(NOT_SET)) {
			for (Relationship r : DrugUtils.getIngredientRelationships(c, CharacteristicType.INFERRED_RELATIONSHIP, true)) {
				if (substancesWithMultipleModifications.contains(r.getTarget())) {
					String basesStr = getBases(r.getTarget())
							.stream()
							.map(Concept::toString)
							.collect(Collectors.joining(", \n"));
					report(c, r.getType().getPreferredSynonym(), r.getTarget(), basesStr);
				}
			}
		}
	}

}
