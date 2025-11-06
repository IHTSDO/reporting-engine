package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MismatchedRelationships extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(MismatchedRelationships.class);

	String targetAttributeType = "246075003"; // | Causative agent (attribute) |;
	
	public static void main(String[] args) throws TermServerScriptException {
		MismatchedRelationships report = new MismatchedRelationships();
		try {
			report.additionalReportColumns = "Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, Type, Target";
			report.init(args);
			report.loadProjectSnapshot(false);
			report.detectMismatchedRelationships();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report ", e);
		} finally {
			report.finish();
		}
	}
	
	private void detectMismatchedRelationships() throws TermServerScriptException {
		//Work through the snapshot of stated relationships and - for the target
		//attribute type, report if the inferred relationship does not
		//match the inferred one.
		Concept targetAttribute = gl.getConcept(targetAttributeType);
		LOGGER.info("Checking " + gl.getAllConcepts().size() + " concepts for mismatched " + targetAttribute);
		int mismatchedRelationships = 0;
		for (Concept thisConcept : gl.getAllConcepts()) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				LOGGER.warn(msg);
			}
			Set<Relationship> statedRelationships = thisConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, targetAttribute, ActiveState.ACTIVE);
			Set<Relationship> inferredRelationships = thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, targetAttribute, ActiveState.ACTIVE);
			
			if (statedRelationships.size() == 0) {
				//Nothing to do here, concept not relevant
				continue; //consider next concept
			} else if (statedRelationships.size() > 1) {
				report(thisConcept, null, "multiple stated attributes of specified type detected");
			} else {
				Relationship stated = statedRelationships.iterator().next();
				if (inferredRelationships.size() != 1) {
					report(thisConcept, stated, "Stated relationship has " + inferredRelationships.size() + " inferred counterparts");
					mismatchedRelationships++;
				} else {
					Relationship inferred = inferredRelationships.iterator().next();
					if (!stated.getTarget().equals(inferred.getTarget())) {
						String msg = "Stated target does not equal inferred target " + inferred.getTarget();
						report(thisConcept, stated, msg);
					}
				}
			}
		}
		LOGGER.info("Detected " + mismatchedRelationships + " mismatched Relationships");
	}
	
	@Override
	public String getScriptName() {
		return "Lost Relationships";
	}
}
