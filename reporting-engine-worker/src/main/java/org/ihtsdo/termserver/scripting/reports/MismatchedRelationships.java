package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public class MismatchedRelationships extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	String targetAttributeType = "246075003"; // | Causative agent (attribute) |;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		MismatchedRelationships report = new MismatchedRelationships();
		try {
			report.additionalReportColumns = "Concept_Active, Concept_Modified, Stated_or_Inferred, Relationship_Active, GroupNum, Type, Target";
			report.init(args);
			report.loadProjectSnapshot();
			report.detectMismatchedRelationships();
		} catch (Exception e) {
			info("Failed to produce Changed Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void detectMismatchedRelationships() throws TermServerScriptException {
		//Work through the snapshot of stated relationships and - for the target
		//attribute type, report if the inferred relationship does not
		//match the inferred one.
		Concept targetAttribute = gl.getConcept(targetAttributeType);
		info("Checking " + gl.getAllConcepts().size() + " concepts for mismatched " + targetAttribute);
		int mismatchedRelationships = 0;
		for (Concept thisConcept : gl.getAllConcepts()) {
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				warn(msg);
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
		info("Detected " + mismatchedRelationships + " mismatched Relationships");
	}
	
	protected void report (Concept c, Relationship r, String msg) throws TermServerScriptException {
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
		writeToReportFile(line);
	}
	
	protected void loadProjectSnapshot() throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		int SNAPSHOT = 0;
		File[] archives = new File[] { new File (project + "_snapshot_" + env + ".zip") };

		//Do we already have a copy of the project locally?  If not, recover it.
		if (!archives[SNAPSHOT].exists()) {
			info ("Recovering snapshot state of " + project + " from TS (" + env + ")");
			String branchPath = project.equals("MAIN")?"MAIN":"MAIN/" + project;
			tsClient.export(branchPath, null, ExportType.MIXED, ExtractType.SNAPSHOT, archives[SNAPSHOT]);
			initialiseSnowOwlClient();  //re-initialise client to avoid HttpMediaTypeNotAcceptableException.  Cause unknown.
		}
		
		//No need for a delta for this report.  We can tell if the relationship has
		//changed by the null effective time.
		
		info ("Loading snapshot data into memory...");
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
								info("Loading Description File.");
								gl.loadDescriptionFile(zis, true);  //Load FSNs only
							}
							
							if (fileName.contains("sct2_Concept_Snapshot")) {
								info("Loading Concept File.");
								gl.loadConceptFile(zis);
							}
							
							if (fileName.contains("sct2_Relationship_Snapshot")) {
								info("Loading Relationship Snapshot File.");
								gl.loadRelationshipDelta(CharacteristicType.INFERRED_RELATIONSHIP,zis);
							}
							
							if (fileName.contains("sct2_StatedRelationship_Snapshot")) {
								info("Loading Stated Relationship Snapshot File.");
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
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}
}
