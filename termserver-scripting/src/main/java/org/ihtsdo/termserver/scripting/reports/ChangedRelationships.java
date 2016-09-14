package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
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

public class ChangedRelationships extends TermServerScript{
	
	Set<Concept> modifiedConcepts = new HashSet<Concept>();
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ChangedRelationships fix = new ChangedRelationships();
		try {
			fix.init(args);
			fix.loadProjectSnapshotAndDelta();
			fix.detectChangedRelationships();
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
		println("Examining " + modifiedConcepts.size() + " modified concepts");
		int changedRelationships = 0;
		for (Concept thisConcept : modifiedConcepts) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				criticalErrors.add(msg);
				println(msg);
			}
			List<Relationship> allConceptRelationships = thisConcept.getRelationships(CHARACTERISTIC_TYPE.ALL, ACTIVE_STATE.BOTH);
			
			for(Relationship thisRel : allConceptRelationships) {
				//Has this relationship changed in the this release?
				if (thisRel.getEffectiveTime().equals(transientEffectiveDate)) {
					report (thisConcept, thisRel);
					changedRelationships++;
				}
			}
		}
		println("Detected " + changedRelationships + " changed Relationships");
	}
	
	protected void report (Concept c, Relationship r) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.isActive() + COMMA + 
						c.getEffectiveTime().equals(transientEffectiveDate) + COMMA_QUOTE + 
						r.getCharacteristicType().toString() + QUOTE_COMMA +
						r.isActive() + COMMA +
						r.getGroupId() + COMMA_QUOTE +
						r.getType().toString() + QUOTE_COMMA_QUOTE +
						r.getTarget().toString() + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "changed_relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, Type, Target");
	}

	private void loadProjectSnapshotAndDelta() throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		int SNAPSHOT = 0;
		int DELTA = 1;
		File[] archives = new File[] { new File (project + "_snapshot_" + env + ".zip"), new File (project + "_delta_" + env + "_" + transientEffectiveDate + ".zip") };

		//Do we already have a copy of the project locally?  If not, recover it.
		if (!archives[SNAPSHOT].exists()) {
			println ("Recovering snapshot state of " + project + " from TS (" + env + ")");
			tsClient.export("MAIN/" + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, archives[SNAPSHOT]);
			initialiseSnowOwlClient();  //re-initialise client to avoid HttpMediaTypeNotAcceptableException.  Cause unknown.
		}
		
		if (!archives[DELTA].exists()) {
			println ("Recovering delta state of " + project + " from TS (" + env + ") for " + transientEffectiveDate);
			tsClient.export("MAIN/" + project, transientEffectiveDate, ExportType.UNPUBLISHED, ExtractType.DELTA, archives[DELTA]);
		}
		
		GraphLoader gl = GraphLoader.getGraphLoader();
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
								gl.loadDescriptionFile(zis);
							}
							
							if (fileName.contains("sct2_Concept_Snapshot")) {
								println("Loading Concept File.");
								gl.loadConceptFile(zis);
							}
							
							if (fileName.contains("sct2_Relationship_Delta")) {
								println("Loading Relationship Delta File.");
								modifiedConcepts.addAll(gl.loadRelationshipDelta(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP,zis));
							}
							
							if (fileName.contains("sct2_StatedRelationship_Delta")) {
								println("Loading Stated Relationship Delta File.");
								modifiedConcepts.addAll(gl.loadRelationshipDelta(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP,zis));
							}
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
	public String getFixName() {
		return "Lost Relationships";
	}
}
