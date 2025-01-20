package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * SUBST-235 A report to identify any concepts which have the same concept as both
 * a parent and the target value of some other attribute 
 * Update: Added column to say what was inferred - the parent, attribute or both
 * SUBST-260
 */
public class ConceptsWithAttributesAsParents extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptsWithAttributesAsParents.class);

	Concept attributeType;
	List<Concept> ignoreTypes;
	
	public static void main(String[] args) throws TermServerScriptException {
		ConceptsWithAttributesAsParents report = new ConceptsWithAttributesAsParents();
		try {
			ReportSheetManager.setTargetFolderId("1bwgl8BkUSdNDfXHoL__ENMPQy_EdEP7d");
			report.additionalReportColumns = "FSN, Semtag, CharacteristicType, Attribute, WhatWasInferred?";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runConceptsWithAttributesAsParentsReport();
		} catch (Exception e) {
			LOGGER.error("Failed to produce ConceptsWithOrTargetsOfAttribute Report", e);
		} finally {
			report.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		attributeType = gl.getConcept("738774007"); // |Is modification of (attribute)|)
		ignoreTypes = new ArrayList<>();
		ignoreTypes.add(DUE_TO);
		ignoreTypes.add(PART_OF);
		super.postInit();
	}

	private void runConceptsWithAttributesAsParentsReport() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (!checkforAttributesAsParents(c, CharacteristicType.STATED_RELATIONSHIP)) {
				checkforAttributesAsParents(c, CharacteristicType.INFERRED_RELATIONSHIP);
			}
			incrementSummaryInformation("Concepts checked");
		}
	}

	private boolean checkforAttributesAsParents(Concept c, CharacteristicType type) throws TermServerScriptException {
		Set<Concept> parents = c.getParents(type);
		boolean issueFound = false;
		//Now work through the attribute values checking for parents
		for (Relationship r : c.getRelationships(type, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A) && isOfInterest(r.getType())) {
				if (parents.contains(r.getTarget())) {
					String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
					if (type.equals(CharacteristicType.STATED_RELATIONSHIP)) {
						report(c, semTag, type.toString(), r.toString());
					} else {
						String whatWasInferred = determineWhatWasInferred(c, r.getTarget());
						report(c, semTag, type.toString(), r.toString(), whatWasInferred);
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
		return false;
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
