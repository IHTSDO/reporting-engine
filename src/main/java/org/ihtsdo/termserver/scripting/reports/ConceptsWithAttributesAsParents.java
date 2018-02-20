package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

/**
 * SUBST-235 A report to identify any concepts which have the same concept as both
 * a parent and the target value of some other attribute 
 */
public class ConceptsWithAttributesAsParents extends TermServerReport {
	
	Concept attributeType;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ConceptsWithAttributesAsParents report = new ConceptsWithAttributesAsParents();
		try {
			report.additionalReportColumns = "CharacteristicType, Attribute";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runConceptsWithAttributesAsParentsReport();
		} catch (Exception e) {
			println("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}


	private void postInit() throws TermServerScriptException {
		attributeType = gl.getConcept("738774007"); // |Is modification of (attribute)|)
	}


	private void runConceptsWithAttributesAsParentsReport() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (!checkforAttributesAsParents(c, CharacteristicType.STATED_RELATIONSHIP)) {
				checkforAttributesAsParents(c, CharacteristicType.INFERRED_RELATIONSHIP);
			}
			incrementSummaryInformation("Concepts checked");
		}
	}


	private boolean checkforAttributesAsParents(Concept c, CharacteristicType type) {
		List<Concept> parents = c.getParents(type);
		boolean issueFound = false;
		//Now work through the attribute values checking for parents
		for (Relationship r : c.getRelationships(type, ActiveState.ACTIVE)) {
			if (r.getType().equals(attributeType)) {
				if (parents.contains(r.getTarget())) {
					report (c, type.toString(), r.toString());
					incrementSummaryInformation("Issues found - " + type.toString());
					issueFound = true;
				}
			}
		}
		return issueFound;
	}

}


