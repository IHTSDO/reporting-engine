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
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */
public class HierarchyConceptsNotInFileReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String publishedArchive;
	String file2;
	String file2Purpose = "UsedToDefineConcepts";
	String hierarchy = "49062001"; // |Device (physical object)|
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		HierarchyConceptsNotInFileReport report = new HierarchyConceptsNotInFileReport();
		try {
			report.init(args);
			report.loadProjectSnapshot();  //Load FSNs only
			List<Concept> conceptsInFile = report.processFile();
			List<Concept> conceptsInFile2 = new ArrayList<Concept>();
			if (report.file2 != null) {
				conceptsInFile2 = report.processFile(new File(report.file2));
			}
			report.inHierarchyAndNotFile(conceptsInFile, conceptsInFile2);
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void inHierarchyAndNotFile(List<Concept> conceptsInFile, List<Concept> conceptsInFile2) throws TermServerScriptException {
		//For all active concepts in the report hierarchy, report if the concept is not
		//also in the supplied file
		Concept sourceHierarchy = gl.getConcept(hierarchy);
		Set<Concept> sourceConcepts = filterActive(sourceHierarchy.getDescendents(NOT_SET));
		println ("Active source concepts number " + sourceConcepts.size());
		
		for (Concept c : sourceConcepts) {
			if (!conceptsInFile.contains(c)) {
				report (c, conceptsInFile2.contains(c));
			}
		}
	}
	

	private Set<Concept> filterActive(Set<Concept> fullSet) {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : fullSet ) {
			if (thisConcept.isActive()) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}


	protected void report (Concept c, boolean inFile2) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE_COMMA +
						(inFile2 ? "YES":"NO") ;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-z")) {
				publishedArchive = args[++x];
			}
			
			if (args[x].equals("-f2")) {
				file2 = args[++x];
			}
		}
		
		print ("Concepts in which Hierarchy? [" + hierarchy + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			hierarchy = response;
		}
		
		print ("What header for existing in 2nd file? [" + file2Purpose + "]: ");
		response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			file2Purpose = response;
		}
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, EffectiveTime, DefinitionStatus," + file2Purpose);
	}

	private void loadProjectSnapshot() throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		int SNAPSHOT = 0;
		File[] archives;
		if (publishedArchive != null && !publishedArchive.isEmpty()) {
			archives = new File[] { new File(publishedArchive)};
		} else {
			archives = new File[] { new File (project + "_snapshot_" + env + ".zip")};
		}
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
