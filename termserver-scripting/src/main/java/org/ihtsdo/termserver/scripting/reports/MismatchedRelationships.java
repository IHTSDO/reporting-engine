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
import java.util.List;
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

public class MismatchedRelationships extends TermServerScript{
	
	GraphLoader gl = GraphLoader.getGraphLoader();
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	String targetAttributeType = "246075003"; // | Causative agent (attribute) |;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		MismatchedRelationships report = new MismatchedRelationships();
		try {
			report.init(args);
			report.loadProjectSnapshot();
			report.detectMismatchedRelationships();
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
	
	private void detectMismatchedRelationships() throws TermServerScriptException {
		//Work through the snapshot of stated relationships and - for the target
		//attribute type, report if the inferred relationship does not
		//match the inferred one.
		Concept targetAttribute = gl.getConcept(targetAttributeType);
		println("Checking " + gl.getAllConcepts().size() + " concepts for mismatched " + targetAttribute);
		int mismatchedRelationships = 0;
		for (Concept thisConcept : gl.getAllConcepts()) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				criticalErrors.add(msg);
				println(msg);
			}
			List<Relationship> statedRelationships = thisConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, targetAttribute, ActiveState.ACTIVE);
			List<Relationship> inferredRelationships = thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, targetAttribute, ActiveState.ACTIVE);
			
			if (statedRelationships.size() == 0) {
				//Nothing to do here, concept not relevant
				continue; //consider next concept
			} else if (statedRelationships.size() > 1) {
				report (thisConcept, null, "multiple stated attributes of specified type detected");
			} else {
				Relationship stated = statedRelationships.get(0);
				if (inferredRelationships.size() != 1) {
					report (thisConcept, stated, "Stated relationship has " + inferredRelationships.size() + " inferred counterparts");
					mismatchedRelationships++;
				} else {
					Relationship inferred = inferredRelationships.get(0);
					if (!stated.getTarget().equals(inferred.getTarget())) {
						String msg = "Stated target does not equal inferred target " + inferred.getTarget();
						report (thisConcept, stated, msg);
					}
				}
			}
		}
		println("Detected " + mismatchedRelationships + " mismatched Relationships");
	}
	
	protected void report (Concept c, Relationship r, String msg) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.isActive() + COMMA + 
						c.getEffectiveTime().equals(transientEffectiveDate) + COMMA; 
		if (r != null) {
			line += QUOTE + r.getCharacteristicType().toString() + QUOTE_COMMA +
				r.isActive() + COMMA +
				r.getGroupId() + COMMA_QUOTE +
				r.getType().toString() + QUOTE_COMMA_QUOTE +
				r.getTarget().toString() + QUOTE_COMMA;
		} else {
			line += COMMA + COMMA + COMMA + COMMA;
		}
		line += QUOTE + msg + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "mismatched_relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, Type, Target");
	}

	protected void loadProjectSnapshot() throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		int SNAPSHOT = 0;
		File[] archives = new File[] { new File (project + "_snapshot_" + env + ".zip") };

		//Do we already have a copy of the project locally?  If not, recover it.
		if (!archives[SNAPSHOT].exists()) {
			println ("Recovering snapshot state of " + project + " from TS (" + env + ")");
			String branchPath = project.equals("MAIN")?"MAIN":"MAIN/" + project;
			tsClient.export(branchPath, null, ExportType.MIXED, ExtractType.SNAPSHOT, archives[SNAPSHOT]);
			initialiseSnowOwlClient();  //re-initialise client to avoid HttpMediaTypeNotAcceptableException.  Cause unknown.
		}
		
		//No need for a delta for this report.  We can tell if the relationship has
		//changed by the null effective time.
		
		println ("Loading snapshot data into memory...");
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
								gl.loadRelationshipDelta(CharacteristicType.INFERRED_RELATIONSHIP,zis);
							}
							
							if (fileName.contains("sct2_StatedRelationship_Snapshot")) {
								println("Loading Stated Relationship Snapshot File.");
								gl.loadRelationshipDelta(CharacteristicType.STATED_RELATIONSHIP,zis);
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
	public String getScriptName() {
		return "Lost Relationships";
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
