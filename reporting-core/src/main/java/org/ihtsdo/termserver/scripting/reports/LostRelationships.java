package org.ihtsdo.termserver.scripting.reports;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LostRelationships extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(LostRelationships.class);

	Set<Concept> modifiedConcepts;
	Set<Concept> descendantOfProductRole;
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());;
	
	public static void main(String[] args) throws TermServerScriptException {
		LostRelationships report = new LostRelationships();
		try {
			report.additionalReportColumns = "Active, Not Replaced Relationship, ValueIsProdRoleDesc";
			report.init(args);
			report.loadProjectSnapshot(true);
			report.populateProdRoleDesc();
			report.detectLostRelationships();
		} catch (Exception e) {
			LOGGER.info("Failed to produce Lost Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void populateProdRoleDesc() throws TermServerScriptException {
		Concept productRole = gl.getConcept("718566004"); // |Product role (product))
		descendantOfProductRole = productRole.getDescendants(NOT_SET);
	}

	private void detectLostRelationships() throws TermServerScriptException {
		//Work through our set of modified concepts and if a relationship of a type has 
		//been inactivated, ensure that we have another relationship of the same time 
		//that replaces it.
		LOGGER.info("Examining " + modifiedConcepts.size() + " modified concepts");
		nextConcept:
		for (Concept thisConcept : modifiedConcepts) {
			//Only working with product concepts
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				LOGGER.warn(msg);
			} else if (!thisConcept.getFsn().contains("(product)")) {
				LOGGER.debug("Skipping " + thisConcept);
				continue;
			}
			//Only looking at relationships that have changed in this release, so pass current effective time
			for(Relationship thisRel : thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.INACTIVE, transientEffectiveDate)) {
				Set<Relationship> replacements = thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, thisRel.getType(), ActiveState.ACTIVE);
				if (replacements.size() == 0) {
					String msg = thisConcept + " has no replacement for lost relationship " + thisRel;
					if (!thisConcept.isActive()) {
						msg += " but is inactive.";
						LOGGER.debug (msg);
						report(thisConcept, thisRel);
						continue nextConcept;
					}
					LOGGER.warn (msg);
					report(thisConcept, thisRel);
				}
			}
		}
		
	}
	
	protected void report(Concept c, Relationship r) throws TermServerScriptException {
		//Adding a column to indicate if the relationship value is a descendant of Product Role
		boolean isProdRoleDesc = descendantOfProductRole.contains(r.getTarget());
		String line = c.getConceptId() + COMMA_QUOTE + c.getFsn() + QUOTE_COMMA + c.isActive() + COMMA_QUOTE + r + QUOTE_COMMA + isProdRoleDesc;
		writeToReportFile(line);
	}

	@Override
	public String getScriptName() {
		return "Lost Relationships";
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
