package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

public class MultipleModifications extends TermServerReport {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		MultipleModifications report = new MultipleModifications();
		try {
			report.additionalReportColumns = "Modifications";
			report.init(args);
			report.loadProjectSnapshot(true);  
			report.finMultipleModifications();
		} catch (Exception e) {
			info("Failed to produce MultipleModifications due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void finMultipleModifications() throws TermServerScriptException {
		for (Concept c : SUBSTANCE.getDescendents(NOT_SET)) {
			List<Concept> bases = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE)
					.stream()
					.map(rel -> rel.getTarget())
					.collect(Collectors.toList());
			
			if (bases.size() > 1) {
				report (c, bases.stream().map(Concept::toString).collect(Collectors.joining(", ")));
				incrementSummaryInformation("Issue detected");
			}
		}
	}
}
