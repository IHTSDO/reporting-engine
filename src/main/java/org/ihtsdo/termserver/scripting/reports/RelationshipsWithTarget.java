package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
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
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class RelationshipsWithTarget extends TermServerScript{
	
	Set<Concept> modifiedConcepts = new HashSet<Concept>();
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	Set<Concept> filterOnTarget = new HashSet<Concept>();
	CharacteristicType filterOnCharacteristicType = null;
	ActiveState filterOnActiveState = null;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		RelationshipsWithTarget report = new RelationshipsWithTarget();
		try {
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.init2(); //Setup needed after data loaded
			report.reportRelationshipsWithTarget();
		} catch (Exception e) {
			println("Failed to produce Changed Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
			for (String err : report.criticalErrors) {
				println (err);
			}
		}
	}
	
	private void reportRelationshipsWithTarget() {
		Collection<Concept> allConcepts =  gl.getAllConcepts();
		println("Examining " + allConcepts.size() + " concepts");
		int reportedRelationships = 0;
		for (Concept thisConcept : allConcepts) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				criticalErrors.add(msg);
				println(msg);
			}
			
			List<Relationship> allConceptRelationships = thisConcept.getRelationships(filterOnCharacteristicType, filterOnActiveState);
			for(Relationship thisRel : allConceptRelationships) {
				if (filterOnTarget.contains(thisRel.getTarget())) {
					report (thisConcept, thisRel);
					reportedRelationships++;
				}
			}
		}
		println("Reported " + reportedRelationships + " active Stated Relationships");
		println("Graph loader log: \n" + gl.log);
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
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String reportFilename = "relationships_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, SemTag, Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, TypeId, TypeFsn, TargetId, TargetFsn");
	}
	
	public void init2() throws TermServerScriptException {
		String response = null;
		println ("Filter target example: 105590001 |Substance (substance)|");
		println ("Filter target example: 373873005 |Pharmaceutical / biologic product (product)|");
				
		while (response == null) {
			print ("Filter for attribute target descendent or self of: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				Concept hierarchy = gl.getConcept(response);
				Set<Concept> filteringTargets = hierarchy.getDescendents(NOT_SET,CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
				filterOnTarget.addAll(filteringTargets); //descendant
				filterOnTarget.add(hierarchy);  //and self
				println ("\nFiltering for target descendents of " + hierarchy + " - " + filteringTargets.size());
				response = null;
			}
		}
	}

	@Override
	public String getScriptName() {
		return "Relationships_with_Target";
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
