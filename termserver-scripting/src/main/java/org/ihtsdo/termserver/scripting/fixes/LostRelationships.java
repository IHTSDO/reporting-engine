package org.ihtsdo.termserver.scripting.fixes;

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
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public class LostRelationships extends TermServerFix{
	
	Set<Concept> modifiedConcepts;
	List<String> criticalErrors = new ArrayList<String>();
	
	public static void main(String[] args) throws TermServerFixException, IOException, SnowOwlClientException {
		LostRelationships fix = new LostRelationships();
		try {
			fix.init(args);
			fix.loadProjectSnapshotAndDelta();
			fix.detectLostRelationships();
		} catch (Exception e) {
			println("Failed to produce Lost Relationship Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			fix.finish();
			for (String err : fix.criticalErrors) {
				println (err);
			}
		}
	}
	
	private void detectLostRelationships() {
		//Work through our set of modified concepts and if a relationship of a type has 
		//been inactivated, ensure that we have another relationship of the same time 
		//that replaces it.
		println("Examining " + modifiedConcepts.size() + " modified concepts");
		nextConcept:
		for (Concept thisConcept : modifiedConcepts) {
			//Only working with product concepts
			if (thisConcept.getFsn() == null) {
				String msg = "Concept " + thisConcept.getConceptId() + " has no FSN";
				criticalErrors.add(msg);
				println(msg);
			} else if (!thisConcept.getFsn().contains("(product)")) {
				debug ("Skipping " + thisConcept);
				continue;
			}
			for(Relationship thisRel : thisConcept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, ACTIVE_STATE.INACTIVE)) {
				List<Relationship> replacements = thisConcept.getRelationships(CHARACTERISTIC_TYPE.INFERRED_RELATIONSHIP, thisRel.getType(), ACTIVE_STATE.ACTIVE);
				if (replacements.size() == 0) {
					String msg = thisConcept + " has no replacement for lost relationship " + thisRel;
					if (!thisConcept.isActive()) {
						msg += " but is inactive.";
						debug (msg);
						report (thisConcept, thisRel);
						continue nextConcept;
					}
					warn (msg);
					report (thisConcept, thisRel);
				}
			}
		}
		
	}
	
	protected void report (Concept c, Relationship r) {
		String line = c.getConceptId() + COMMA_QUOTE + c.getFsn() + QUOTE_COMMA + c.isActive() + COMMA_QUOTE + r + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerFixException {
		super.init(args);
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = "lost_relationships_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, Active, Not Replaced Relationship");
	}

	private void loadProjectSnapshotAndDelta() throws SnowOwlClientException, TermServerFixException, InterruptedException {
		int SNAPSHOT = 0;
		int DELTA = 1;
		File[] archives = new File[] { new File (project + "_snapshot_" + env + ".zip"), new File (project + "_delta_" + env + ".zip") };

		//Do we already have a copy of the project locally?  If not, recover it.
		if (!archives[SNAPSHOT].exists()) {
			println ("Recovering snapshot state of " + project + " from TS (" + env + ")");
			tsClient.export("MAIN/" + project, null, ExportType.MIXED, ExtractType.SNAPSHOT, archives[SNAPSHOT]);
			initialiseSnowOwlClient();  //re-initialise client to avoid HttpMediaTypeNotAcceptableException.  Cause unknown.
		}
		
		if (!archives[DELTA].exists()) {
			println ("Recovering delta state of " + project + " from TS (" + env + ")");
			String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
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
								modifiedConcepts = gl.getConcepts(zis);
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
				throw new TermServerFixException("Failed to extract project state from archive " + archive.getName(), e);
			}
		}
	}

	@Override
	public String getFixName() {
		return "Lost Relationships";
	}
}
