package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.DrugUtils;

/**
 * DRUGS-506 List Substances that have more than one modification attribute
 * DRUGS-476 Similar, list CDs that have ingredient which has multiple modifications
 */
public class MultipleModifications extends TermServerReport {
	
	Set<Concept> substancesWithMultipleModifications = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		MultipleModifications report = new MultipleModifications();
		try {
			ReportSheetManager.targetFolderId = "1hYd96nzfB35ggffWR_SdPbybpmzynlI6"; //Drugs
			//report.additionalReportColumns = "FSN, Modifications";
			report.additionalReportColumns = "FSN, Attribute Type, Ingredient Value, Bases";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.findMultipleModifications();
			report.findCDsWithMultipleModifications();
		} catch (Exception e) {
			info("Failed to produce MultipleModifications due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void findMultipleModifications() throws TermServerScriptException {
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			List<Concept> bases = getBases(c);
			
			if (bases.size() > 1) {
				//report (c, bases.stream().map(Concept::toString).collect(Collectors.joining(", ")));
				//incrementSummaryInformation("Issue detected");
				substancesWithMultipleModifications.add(c);
			}
		}
	}

	private List<Concept> getBases(Concept c) {
		return c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE)
				.stream()
				.map(rel -> rel.getTarget())
				.collect(Collectors.toList());
	}

	private void findCDsWithMultipleModifications() throws TermServerScriptException {
		for (Concept c : MEDICINAL_PRODUCT.getDescendents(NOT_SET)) {
			for (Relationship r : DrugUtils.getIngredientRelationships(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (substancesWithMultipleModifications.contains(r.getTarget())) {
					String basesStr = getBases(r.getTarget())
							.stream()
							.map(t -> t.toString())
							.collect(Collectors.joining(", \n"));
					report (c, r.getType().getPreferredSynonym(), r.getTarget(), basesStr);
				}
			}
		}
	}

}
