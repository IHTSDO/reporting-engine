package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
/*
 * DRUGS-244 Report where the number of stated attributes of a given type does not equal
 * the number of inferred.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CardinalityMismatch extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(CardinalityMismatch.class);

	Concept attributeType = HAS_DISPOSITION;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		CardinalityMismatch report = new CardinalityMismatch();
		try {
			report.additionalReportColumns = "";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.finMultipleModifications();
		} catch (Exception e) {
			LOGGER.info("Failed to produce MultipleModifications due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void finMultipleModifications() throws TermServerScriptException {
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			List<Concept> statedDispositions = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)
					.stream()
					.map(rel -> rel.getTarget())
					.collect(Collectors.toList());
			List<Concept> infDispositions = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)
					.stream()
					.map(rel -> rel.getTarget())
					.collect(Collectors.toList()); 

			statedDispositions.removeAll(infDispositions);
			if (statedDispositions.size() >0 ) {
				incrementSummaryInformation("Issues identified");
				report (c, statedDispositions.stream().map(Concept::toString).collect(Collectors.joining(", ")));
			}
		}
	}
}
