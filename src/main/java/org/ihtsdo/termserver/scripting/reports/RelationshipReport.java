package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public class RelationshipReport extends TermServerScript{
	
	Set<Concept> modifiedConcepts = new HashSet<Concept>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	Concept filterOnType = null; 
	CharacteristicType filterOnCharacteristicType = null;
	ActiveState filterOnActiveState = null;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		RelationshipReport report = new RelationshipReport();
		try {
			report.additionalReportColumns = "Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, TypeId, TypeFsn, TargetId, TargetFsn";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportActiveRelationships();
		} catch (Exception e) {
			info("Failed to produce Changed Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void reportActiveRelationships() {
		Collection<Concept> conceptsToExamine =  gl.getAllConcepts();  //modifiedConcepts
		info("Examining " + conceptsToExamine.size() + " concepts");
		int reportedRelationships = 0;
		for (Concept thisConcept : conceptsToExamine) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				warn(msg);
			}
			List<Relationship> allConceptRelationships = thisConcept.getRelationships(filterOnCharacteristicType, filterOnActiveState);
			
			for(Relationship thisRel : allConceptRelationships) {
				if (filterOnType == null || thisRel.getType().equals(filterOnType)){
					report (thisConcept, thisRel);
					reportedRelationships++;
				}
			}
		}
		info("Reported " + reportedRelationships + " active Stated Relationships");
		info("Graph loader log: \n" + gl.log);
	}
	
	protected void report (Concept c, Relationship r) {
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
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
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
