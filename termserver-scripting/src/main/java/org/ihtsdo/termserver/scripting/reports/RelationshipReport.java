package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public class RelationshipReport extends TermServerScript{
	
	Set<Concept> modifiedConcepts = new HashSet<Concept>();
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	Concept filterOnType = null; 
	CHARACTERISTIC_TYPE filterOnCharacteristicType = null;
	ActiveState filterOnActiveState = null;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		RelationshipReport fix = new RelationshipReport();
		try {
			fix.init(args);
			fix.loadProjectSnapshotAndDelta();  //Load FSNs only
			//fix.detectChangedRelationships();
			fix.reportActiveRelationships();
		} catch (Exception e) {
			println("Failed to produce Changed Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			fix.finish();
			for (String err : fix.criticalErrors) {
				println (err);
			}
		}
	}
	
	private void detectChangedRelationships() {
		//Work through our set of modified concepts and if a relationship of a type has 
		//been inactivated, ensure that we have another relationship of the same time 
		//that replaces it.
		Collection<Concept> conceptsToExamine =  gl.getAllConcepts();  //modifiedConcepts
		println("Examining " + conceptsToExamine.size() + " concepts");
		int changedRelationships = 0;
		for (Concept thisConcept : conceptsToExamine) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				criticalErrors.add(msg);
				println(msg);
			}
			List<Relationship> allConceptRelationships = thisConcept.getRelationships(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP, ActiveState.BOTH);
			
			for(Relationship thisRel : allConceptRelationships) {
				//Has this relationship changed in the this release?
				if (thisRel.getEffectiveTime().equals(transientEffectiveDate)) {
					report (thisConcept, thisRel);
					changedRelationships++;
				}
			}
		}
		println("Detected " + changedRelationships + " changed Stated Relationships");
		println("Graph loader log: \n" + gl.log);
	}
	
	private void reportActiveRelationships() {
		Collection<Concept> conceptsToExamine =  gl.getAllConcepts();  //modifiedConcepts
		println("Examining " + conceptsToExamine.size() + " concepts");
		int reportedRelationships = 0;
		for (Concept thisConcept : conceptsToExamine) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				criticalErrors.add(msg);
				println(msg);
			}
			List<Relationship> allConceptRelationships = thisConcept.getRelationships(filterOnCharacteristicType, filterOnActiveState);
			
			for(Relationship thisRel : allConceptRelationships) {
				if (filterOnType == null || thisRel.getType().equals(filterOnType)){
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
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
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
					case "S" : filterOnCharacteristicType = CHARACTERISTIC_TYPE.STATED_RELATIONSHIP;
															break;
					case "I" : filterOnCharacteristicType = CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP;
															break;
					case "B" : filterOnCharacteristicType = CHARACTERISTIC_TYPE.ALL;
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
		//String reportFilename = "changed_relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String reportFilename = "relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, TypeId, TypeFsn, TargetId, TargetFsn");
	}

	private void loadProjectSnapshotAndDelta() throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		int SNAPSHOT = 0;
		int DELTA = 1;
		File[] archives = new File[] { new File (project + "_snapshot_" + env + ".zip")};//, new File (project + "_delta_" + env + "_" + transientEffectiveDate + ".zip") };

		//Do we already have a copy of the project locally?  If not, recover it.
		if (!archives[SNAPSHOT].exists()) {
			println ("Recovering snapshot state of " + project + " from TS (" + env + ")");
			tsClient.export("MAIN/" + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, archives[SNAPSHOT]);
			initialiseSnowOwlClient();  //re-initialise client to avoid HttpMediaTypeNotAcceptableException.  Cause unknown.
		}
		
		/*if (!archives[DELTA].exists()) {
			println ("Recovering delta state of " + project + " from TS (" + env + ") for " + transientEffectiveDate);
			tsClient.export("MAIN/" + project, transientEffectiveDate, ExportType.UNPUBLISHED, ExtractType.DELTA, archives[DELTA]);
		}*/
		
		println ("Loading snapshot terms and delta relationships into memory...");
		for (File archive : archives) {
			try {
				ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
				ZipEntry ze = zis.getNextEntry();
				try {
					while (ze != null) {
						if (!ze.isDirectory()) {
							Path p = Paths.get(ze.getName());
							String fileName = p.getFileName().toString();
							if (fileName.contains("sct2_Description_Snapshot")) {
								println("Loading Description File.");
								gl.loadDescriptionFile(zis, true);  //Load FSNs only
							}
							
							if (fileName.contains("sct2_Concept_Snapshot")) {
								println("Loading Concept File.");
								gl.loadConceptFile(zis);
							}
							
							if (fileName.contains("sct2_Relationship_Snapshot")) {
								println("Loading Relationship Snapshot File.");
								gl.loadRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP,zis, true);
							}
							
							if (fileName.contains("sct2_StatedRelationship_Snapshot")) {
								println("Loading Stated Relationship Snapshot File.");
								gl.loadRelationships(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP,zis, true);
							}
							/*
							if (fileName.contains("sct2_Relationship_Delta")) {
								println("Loading Relationship Delta File.");
								modifiedConcepts.addAll(gl.loadRelationshipDelta(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP,zis));
							}
							
							if (fileName.contains("sct2_StatedRelationship_Delta")) {
								println("Loading Stated Relationship Delta File.");
								modifiedConcepts.addAll(gl.loadRelationshipDelta(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP,zis));
							}*/
						}
						ze = zis.getNextEntry();
					}
				} finally {
					try{
						zis.closeEntry();
						zis.close();
					} catch (Exception e){} //Well, we tried.
				}
			} catch (IOException e) {
				throw new TermServerScriptException("Failed to extract project state from archive " + archive.getName(), e);
			}
		}
	}

	@Override
	public String getScriptName() {
		return "Lost Relationships";
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
