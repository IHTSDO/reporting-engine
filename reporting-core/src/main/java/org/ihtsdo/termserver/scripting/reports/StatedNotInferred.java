package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * SUBST-267 Find any attributes that have been stated, which do not exactly
 * exist in the inferred form - ie the classifier has removed them as redundant.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class StatedNotInferred extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(StatedNotInferred.class);

	Concept subHierarchy = SUBSTANCE;
	Concept attributeType = HAS_DISPOSITION;
	
	public static void main(String[] args) throws TermServerScriptException {
		StatedNotInferred report = new StatedNotInferred();
		try {
			report.additionalReportColumns = "FSN, Redundant Disposition";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.runStatedNotInferredReport();
		} catch (Exception e) {
			LOGGER.info("Failed to produce StatedNotInferred Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void runStatedNotInferredReport() throws TermServerScriptException {
		Collection<Concept> subHierarchy = gl.getConcept(this.subHierarchy.getConceptId()).getDescendants(NOT_SET);
		LOGGER.info("Finding redundant relationships");
		for (Concept c : subHierarchy) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
				//How many of these do we have?
				int match = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).size();
				if (match == 0) {
					incrementSummaryInformation("Issues Encountered");
					report(c, r.getTarget());
				}
			}
		}
		addSummaryInformation("Concepts checked", subHierarchy.size());
	}

	
	
}
