package org.ihtsdo.termserver.scripting.reports;

import java.text.SimpleDateFormat;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RelationshipsWithTarget extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(RelationshipsWithTarget.class);

	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	Set<Concept> filterOnTarget = new HashSet<>();
	CharacteristicType filterOnCharacteristicType = null;
	ActiveState filterOnActiveState = null;
	
	public static void main(String[] args) throws TermServerScriptException {
		RelationshipsWithTarget report = new RelationshipsWithTarget();
		try {
			report.additionalReportColumns = "SemTag, Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, TypeId, TypeFsn, TargetId, TargetFsn";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.init2(); //Setup needed after data loaded
			report.reportRelationshipsWithTarget();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}
	
	private void reportRelationshipsWithTarget() throws TermServerScriptException {
		Collection<Concept> allConcepts =  gl.getAllConcepts();
		LOGGER.info("Examining {} concepts", allConcepts.size());
		int reportedRelationships = 0;
		for (Concept thisConcept : allConcepts) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				LOGGER.warn(msg);
			}
			
			Set<Relationship> allConceptRelationships = thisConcept.getRelationships(filterOnCharacteristicType, filterOnActiveState);
			for(Relationship thisRel : allConceptRelationships) {
				if (filterOnTarget.contains(thisRel.getTarget())) {
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
		LOGGER.info("Filter target example: 105590001 |Substance (substance)|");
		LOGGER.info("Filter target example: 373873005 |Pharmaceutical / biologic product (product)|");
				
		while (response == null) {
			print ("Filter for attribute target descendant or self of: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				Concept hierarchy = gl.getConcept(response);
				Set<Concept> filteringTargets = hierarchy.getDescendants(NOT_SET,CharacteristicType.INFERRED_RELATIONSHIP);
				filterOnTarget.addAll(filteringTargets); //descendant
				filterOnTarget.add(hierarchy);  //and self
				LOGGER.info("\nFiltering for target descendants of " + hierarchy + " - " + filteringTargets.size());
				response = null;
			}
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
