package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports instances of more than one attributes of a given type
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AttributeCardinalityReport extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(AttributeCardinalityReport.class);

	List<String> criticalErrors = new ArrayList<>();
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	String targetAttributeStr = "411116001"; // |Has manufactured dose form (attribute)|

	public static void main(String[] args) throws TermServerScriptException {
		AttributeCardinalityReport report = new AttributeCardinalityReport();
		try {
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.runAttributeCardinalityReport();
		} catch (Exception e) {
			LOGGER.error("Failed to produce AttributeCardinalityReport Report due to ", e);
		} finally {
			report.finish();
			for (String err : report.criticalErrors) {
				LOGGER.info (err);
			}
		}
	}

	private void runAttributeCardinalityReport() throws TermServerScriptException {
		Collection<Concept> subHierarchy = gl.getConcept(subHierarchyStr).getDescendants(NOT_SET);
		LOGGER.info("Validating all relationships");
		long issuesEncountered = 0;
		for (Concept c : subHierarchy) {
			if (c.isActiveSafely()) {
				issuesEncountered += checkAttributeCardinality(c);
			}
		}
		addSummaryInformation("Concepts checked", subHierarchy.size());
		addSummaryInformation("Issues encountered", issuesEncountered);
	}

	private long checkAttributeCardinality(Concept c) throws TermServerScriptException {
		long issues = 0;
		Concept typeOfInterest = gl.getConcept(targetAttributeStr);
		Set<Concept> typesEncountered = new HashSet<Concept>();
		
		Set<Relationship> statedRelationshipsOfInterest = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, typeOfInterest, ActiveState.ACTIVE);
		Set<Relationship> inferredRelationshipsOfInterest = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, typeOfInterest, ActiveState.ACTIVE);
		if (statedRelationshipsOfInterest.size() != inferredRelationshipsOfInterest.size()) {
			String msg = "Cardinality mismatch between stated and inferred relationships - (S: " + statedRelationshipsOfInterest.size() + " I: " + inferredRelationshipsOfInterest.size() + ")";
			report(c, msg);
			issues++;
		}
		
		for (Relationship r : statedRelationshipsOfInterest) {
			//Have we seen this value for the target attribute type before?
			Concept type = r.getType();
			if (typesEncountered.contains(type)) {
				String msg = "Multiple Stated instances of attribute " + type;
				report(c, msg);
				issues++;
			}
			
			//Check that we have an inferred relationship that matches this value
			boolean matchFound = false;
			for (Relationship rInf : inferredRelationshipsOfInterest) {
				if (rInf.getTarget().equals(r.getTarget())) {
					matchFound = true;
				}
			}
			if (!matchFound) {
				String msg = "Stated relationship has no inferred counterpart: " + r;
				report(c, msg);
				issues++;
			}
			typesEncountered.add(type);
		}
		return issues;
	}

	protected void report(Concept c, String issue) throws TermServerScriptException {
		String line =	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA_QUOTE + 
						issue + QUOTE;
		writeToReportFile(line);
	}
}
