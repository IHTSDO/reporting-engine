package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * SUBST-300
 * Can you run a query against substance hierarchy to identify concepts 
 * where FSN (or any of the descriptions) contain the word "hydrate" 
 * (by itself or variations such as monohydrate, dihydrate, etc)?
 *
 * The query result should contain: 
 * Concept ID,FSN,Other description(s) containing word "hydrate" 
 * (Only required if FSN doesn't contain this word. If multiple such 
 * descriptions exist, create multiple rows)
 * Is modification attribute value (If multiple, create multiple rows. 
 * If doesn't exist, leave empty).
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Hydrates extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(Hydrates.class);

	String matchText = "hydrate" ;
	Concept subHierarchy = SUBSTANCE;
	
	public static void main(String[] args) throws TermServerScriptException {
		Hydrates report = new Hydrates();
		try {
			ReportSheetManager.setTargetFolderId("1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d");
			report.additionalReportColumns = "FSN, Descriptions, Modifications";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.reportDescriptionContainsX();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void reportDescriptionContainsX() throws TermServerScriptException {

		for (Concept c : subHierarchy.getDescendants(NOT_SET)) {
			//Does the FSN match?
			boolean fsnMatch = c.getFsn().toLowerCase().contains(matchText);
			List<Description> matchingDescs = null;
			if (!fsnMatch) {
				matchingDescs = new ArrayList<>();
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getTerm().toLowerCase().contains(matchText)) {
						matchingDescs.add(d);
					}
				}
			}
			
			//Did we find a match?  Report all modifications if so
			if (fsnMatch || matchingDescs.size() > 0) {
				String descStr = "";
				if (!fsnMatch) {
					descStr = matchingDescs.stream()
							.map(d -> d.toString())
							.collect(Collectors.joining(",\n"));
				}
				incrementSummaryInformation("Concepts reported");
				report(c, descStr, getModifications(c));
			}
		}
	}

	private String getModifications(Concept c) {
		return c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE)
			.stream()
			.map(r -> r.getTarget().toString())
			.collect(Collectors.joining(",\n"));
	}

}
