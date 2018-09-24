package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

/**
 * DRUGS-468
 * Report of concepts which use a particular attribute type, but where the value
 * is outside of a particular range
 */
public class AttributeValueOutsideRange extends TermServerReport {
	
	Concept attributeType;
	Set<Concept> acceptableRange;
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		AttributeValueOutsideRange report = new AttributeValueOutsideRange();
		try {
			report.additionalReportColumns = "CharacteristicType, Attribute, WhatWasInferred?";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runAttributeValueOutsideRangeReport();
		} catch (Exception e) {
			info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postInit() throws TermServerScriptException {
		attributeType = gl.getConcept("732947008"); // |Has presentation strength denominator unit (attribute)|
		acceptableRange = gl.getConcept("732935002").getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, true); // | Unit of presentation (unit of presentation).")
		subHierarchy = gl.getConcept("373873005"); // |Pharmaceutical / biologic product (product)|"
		initialiseSummaryInformation("Issues reported");
	}

	private void runAttributeValueOutsideRangeReport() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			//If our Attribute type is present, report if the value is outside of the range
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE)) {
				if (!acceptableRange.contains(r.getTarget())) {
					report (c, "Unacceptable target value", r.toString());
					incrementSummaryInformation("Issues reported");
				} else {
					debug ("Acceptable: " + r);
					incrementSummaryInformation(r.getTarget().toString());
					incrementSummaryInformation("Attributes within range");
				}
			}
			incrementSummaryInformation("Concepts checked");
		}
	}

}
