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

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */
public class ValidateLateralityReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ValidateLateralityReport fix = new ValidateLateralityReport();
		try {
			fix.init(args);
			fix.loadProjectSnapshot();  //Load FSNs only
			List<Concept> lateralizable = fix.processFile();
			fix.validateLaterality(lateralizable);
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			fix.finish();
		}
	}

	private void validateLaterality(List<Concept> lateralizable) throws TermServerScriptException {
		//For all concepts, if it is lateralized, check that concept is listed in our
		//set of lateralizable concepts.
		Concept laterality = gl.getConcept("272741003");
		Concept side = gl.getConcept("182353008");
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				List<Relationship> lateralized = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP,  laterality, ActiveState.ACTIVE);
				if (lateralized.size() > 0 && lateralized.get(0).getTarget().equals(side)) {
					if (!lateralizable.contains(c)) {
						report (c);
					}
				}
			}
		}
	}

	protected void report (Concept c) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, EffectiveTime, Definition_Status,SemanticTag");
	}

	private void loadProjectSnapshot() throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		int SNAPSHOT = 0;
		File[] archives = new File[] { new File (project + "_snapshot_" + env + ".zip")};

		//Do we already have a copy of the project locally?  If not, recover it.
		if (!archives[SNAPSHOT].exists()) {
			println ("Recovering snapshot state of " + project + " from TS (" + env + ")");
			tsClient.export("MAIN/" + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, archives[SNAPSHOT]);
			initialiseSnowOwlClient();  //re-initialise client to avoid HttpMediaTypeNotAcceptableException.  Cause unknown.
		}
		
		println ("Loading snapshot into memory...");
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
								gl.loadRelationships(CharacteristicType.INFERRED_RELATIONSHIP,zis, true);
							}
							
							if (fileName.contains("sct2_StatedRelationship_Snapshot")) {
								println("Loading Stated Relationship Snapshot File.");
								gl.loadRelationships(CharacteristicType.STATED_RELATIONSHIP,zis, true);
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
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return gl.getConcept(lineItems[0]);
	}
}
