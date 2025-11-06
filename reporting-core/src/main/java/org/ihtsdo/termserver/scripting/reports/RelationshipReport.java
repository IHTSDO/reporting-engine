package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelationshipReport extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(RelationshipReport.class);

	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	Concept filterOnType = null; 
	CharacteristicType filterOnCharacteristicType = null;
	ActiveState filterOnActiveState = null;
	
	public static void main(String[] args) throws TermServerScriptException {
		RelationshipReport report = new RelationshipReport();
		try {
			report.additionalReportColumns = "Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, TypeId, TypeFsn, TargetId, TargetFsn";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportActiveRelationships();
		} catch (Exception e) {
			LOGGER.info("Failed to produce Changed Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void reportActiveRelationships() throws TermServerScriptException {
		Collection<Concept> conceptsToExamine =  gl.getAllConcepts();  //modifiedConcepts
		LOGGER.info("Examining " + conceptsToExamine.size() + " concepts");
		int reportedRelationships = 0;
		for (Concept thisConcept : conceptsToExamine) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				LOGGER.warn(msg);
			}
			Set<Relationship> allConceptRelationships = thisConcept.getRelationships(filterOnCharacteristicType, filterOnActiveState);
			
			for(Relationship thisRel : allConceptRelationships) {
				if (filterOnType == null || thisRel.getType().equals(filterOnType)){
					report(thisConcept, thisRel);
					reportedRelationships++;
				}
			}
		}
		LOGGER.info("Reported " + reportedRelationships + " active Stated Relationships");
		LOGGER.info("Graph loader log: \n" + gl.log);
	}
	
	protected void report(Concept c, Relationship r) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.isActive() + COMMA + 
						c.getEffectiveTime().equals(transientEffectiveDate) + COMMA_QUOTE + 
						r.getCharacteristicType().toString() + QUOTE_COMMA +
						r.isActive() + COMMA +
						r.getGroupId() + COMMA_QUOTE +
						r.getType().getConceptId() + QUOTE_COMMA_QUOTE +
						r.getType().getFsn() + QUOTE_COMMA_QUOTE +
						r.getTarget().getConceptId() + QUOTE_COMMA_QUOTE +
						r.getTarget().getFsn() + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		print ("What date identifies 'new' relationships? [" + transientEffectiveDate + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			long dateAsNumber = Long.parseLong(response);
			if (dateAsNumber < 2000000L || dateAsNumber > 30000000) {
				throw new TermServerScriptException("Invalid date: "  + response);
			}
			transientEffectiveDate = response;
		}
		
		print ("Filter for a particular attribute type? (return for none): ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			filterOnType = gl.getConcept(response);
		}
		
		while (filterOnCharacteristicType == null) {
			print ("Report which characteristic type(s)? [S,I,B]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				switch (response.toUpperCase()) {
					case "S" : filterOnCharacteristicType = CharacteristicType.STATED_RELATIONSHIP;
															break;
					case "I" : filterOnCharacteristicType = CharacteristicType.INFERRED_RELATIONSHIP;
															break;
					case "B" : filterOnCharacteristicType = CharacteristicType.ALL;
				default:
				}
			} 
		}
		
		while (filterOnActiveState == null) {
			print ("Report which active state(s)? [A,I,B]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				switch (response.toUpperCase()) {
					case "A" : filterOnActiveState = ActiveState.ACTIVE;
															break;
					case "I" : filterOnActiveState = ActiveState.INACTIVE;
															break;
					case "B" : filterOnActiveState = ActiveState.BOTH;
				default:
				}
			} 
		}
	}

	@Override
	public String getScriptName() {
		return "Active Relationships";
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
