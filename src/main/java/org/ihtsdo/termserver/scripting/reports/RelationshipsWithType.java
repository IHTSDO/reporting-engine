package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.text.SimpleDateFormat;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class RelationshipsWithType extends TermServerScript{
	
	Set<Concept> modifiedConcepts = new HashSet<Concept>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	Set<Concept> filterOnType = new HashSet<Concept>();
	boolean reverse = false;
	CharacteristicType filterOnCharacteristicType = null;
	ActiveState filterOnActiveState = null;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		RelationshipsWithType report = new RelationshipsWithType();
		try {
			report.additionalReportColumns = " SemTag, Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, TypeId, TypeFsn, TargetId, TargetFsn";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.init2(); //Setup needed after data loaded
			report.reportRelationshipsWithType();
		} catch (Exception e) {
			info("Failed to produce Changed Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void reportRelationshipsWithType() {
		Collection<Concept> allConcepts =  gl.getAllConcepts();
		info("Examining " + allConcepts.size() + " concepts");
		int reportedRelationships = 0;
		for (Concept thisConcept : allConcepts) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				warn(msg);
			}
			
			List<Relationship> allConceptRelationships = thisConcept.getRelationships(filterOnCharacteristicType, filterOnActiveState);
			for(Relationship thisRel : allConceptRelationships) {
				if ((filterOnType.contains(thisRel.getType()) && !reverse) || 
					(!filterOnType.contains(thisRel.getType()) && reverse)){
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
						c.getFsn() + QUOTE_COMMA_QUOTE +
						SnomedUtils.deconstructFSN(c.getFsn())[1] + QUOTE_COMMA +
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
	
	public void init2() throws TermServerScriptException {
		String response = null;
		info ("Filter type example: 106237007 |Linkage concept (linkage concept)|");
				
		while (response == null) {
			print ("Filter for attribute type descendent or self of: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				Concept hierarchy = gl.getConcept(response);
				Set<Concept> filteringTargets = hierarchy.getDescendents(NOT_SET,CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
				filterOnType.addAll(filteringTargets); //descendant
				filterOnType.add(hierarchy);  //and self
				info ("\nFiltering for type descendents of " + hierarchy + " - " + filteringTargets.size());
				response = null;
			}
		}
		
		print ("Reverse results?");
		response = STDIN.nextLine().trim();
		if (response.toUpperCase().equals("Y")) {
			reverse = true;
		}
	}

	@Override
	public String getScriptName() {
		return "Relationships_with_Target";
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
