package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * SUBST-235 A report to identify any concepts which have the same concept as both
 * a parent and the target value of some other attribute 
 * Update: Added column to say what was inferred - the parent, attribute or both
 */
public class ConceptsWithAttributesAsParents extends TermServerReport {
	
	Concept attributeType;
	List<Concept> ignoreTypes;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ConceptsWithAttributesAsParents report = new ConceptsWithAttributesAsParents();
		try {
			report.additionalReportColumns = "Semtag, CharacteristicType, Attribute, WhatWasInferred?";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runConceptsWithAttributesAsParentsReport();
		} catch (Exception e) {
			info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}


	private void postInit() throws TermServerScriptException {
		attributeType = gl.getConcept("738774007"); // |Is modification of (attribute)|)
		ignoreTypes = new ArrayList<>();
		ignoreTypes.add(DUE_TO);
		ignoreTypes.add(PART_OF);
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
			if (!r.getType().equals(IS_A) && isOfInterest(r.getType())) {
				if (parents.contains(r.getTarget())) {
					String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
					if (type.equals(CharacteristicType.STATED_RELATIONSHIP)) {
						report (c, semTag, type.toString(), r.toString());
					} else {
						String whatWasInferred = determineWhatWasInferred(c, r.getTarget());
						report (c, semTag, type.toString(), r.toString(), whatWasInferred);
					}
					incrementSummaryInformation("Issues found - " + type.toString());
					issueFound = true;
				}
			}
		}
		return issueFound;
	}


	private boolean isOfInterest(Concept type) {
		if (type.equals(attributeType)) {
			return true;
		} else if (ignoreTypes.contains(type)) {
			return false;
		}
		return true;
	}


	private String determineWhatWasInferred(Concept c, Concept value) {
		String whatWasInferred = "Undetermined";
		boolean parentStated = false;
		boolean attributeStated = false;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getTarget().equals(value)) {
				if (r.getType().equals(IS_A)) {
					parentStated = true;
				} else {
					attributeStated = true;
				}
			}
		}
		if (parentStated == false && attributeStated == false) {
			whatWasInferred = "both";
		} else {
			if (parentStated) {
				whatWasInferred = "attribute";
			} else if (attributeStated) {
				whatWasInferred = "parent";
			}
		}
		return whatWasInferred;
	}

}


