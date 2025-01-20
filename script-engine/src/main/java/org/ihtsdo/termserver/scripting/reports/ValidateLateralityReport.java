package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidateLateralityReport extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(ValidateLateralityReport.class);

	public static void main(String[] args) throws TermServerScriptException {
		ValidateLateralityReport report = new ValidateLateralityReport();
		try {
			report.additionalReportColumns = "EffectiveTime, Definition_Status,SemanticTag";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			List<Component> lateralizable = report.processFile();
			report.validateLaterality(lateralizable);
		} catch (Exception e) {
			LOGGER.info("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void validateLaterality(List<Component> lateralizable) throws TermServerScriptException {
		//For all concepts, if it is lateralized, check that concept is listed in our
		//set of lateralizable concepts.
		Concept laterality = gl.getConcept("272741003");
		Concept side = gl.getConcept("182353008");
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				Set<Relationship> lateralized = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,  laterality, ActiveState.ACTIVE);
				if (lateralized.size() > 0 && lateralized.iterator().next().getTarget().equals(side)) {
					if (!lateralizable.contains(c)) {
						report(c);
					}
				}
			}
		}
	}

	protected void report(Concept c) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE;
		writeToReportFile(line);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}
