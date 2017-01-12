package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Reports all concepts in a hierarchy that are used in the definition of other concepts.
 */
public class RequiringProxPrimModellingReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String publishedArchive;
	//String[] hierarchies = {"404684003", "71388002", "243796009"};
	String[] hierarchies = {"64572001"}; //Disease (disorder)
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		RequiringProxPrimModellingReport report = new RequiringProxPrimModellingReport();
		try {
			report.init(args);
			report.loadProjectSnapshot();  //Load FSNs only
			boolean reportAll = true; //Report all concepts whether they require remodelling or not
			report.reportRequiringProxPrimModelling(reportAll);
		} catch (Exception e) {
			println("Report failed due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportRequiringProxPrimModelling(boolean reportAll) throws TermServerScriptException {
		for (String hiearchySCTID : hierarchies) {
			int ok = 0;
			int multipleParentsCount = 0;
			int noDifferentiaCount = 0;
			int fdParentCount = 0;
			int requireProxPrimModellingCount = 0;
			Concept hierarchy = gl.getConcept(hiearchySCTID);
			Set<Concept> allHierarchy = hierarchy.getDescendents(NOT_SET, CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
			Set<Concept> allActiveFD = filterActiveFD(allHierarchy);
			println (hierarchy + " - " + allActiveFD.size() + "(FD) / " + allHierarchy.size() + "(Active)");
			for (Concept thisConcept : allActiveFD) {
				List<Concept> parents = thisConcept.getParents(CharacteristicType.STATED_RELATIONSHIP);
				boolean hasFDParent = false;
				boolean noDifferentia = false;
				boolean multipleParents = false;
				for (Concept thisParent : parents) {
					if (thisParent.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
						hasFDParent = true;
						fdParentCount++;
						break;
					}
				}
				
				List<Relationship> attributes = thisConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
				if (attributes.size() == 0) {
					noDifferentia = true;
					noDifferentiaCount++;
				}
				
				if (parents.size() > 1) {
					multipleParents = true;
					multipleParentsCount++;
				}
				
				if (reportAll || hasFDParent || noDifferentia || multipleParents) {
					requireProxPrimModellingCount++;
					report(thisConcept, SnomedUtils.deconstructFSN(thisConcept.getFsn())[1],hasFDParent,noDifferentia,multipleParents);
				} else {
					ok++;
				}
			}
			println ("\tHas FD Parent: " + fdParentCount);
			println ("\tHas no differentia: " + noDifferentiaCount);
			println ("\tHas multiple parents: " + multipleParentsCount);
			println ("\tRequires remodelling: " + requireProxPrimModellingCount);
			println ("\tIs OK: " + ok);
		}
		
	}

	private Set<Concept> filterActiveFD(Set<Concept> fullSet) {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : fullSet ) {
			if (thisConcept.isActive() && thisConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}

	protected void report (Concept c, String semtag, boolean hasFDParent, boolean noDifferentia, boolean multipleParents) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						semtag + QUOTE_COMMA +
						hasFDParent + COMMA + 
						noDifferentia + COMMA +
						multipleParents;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-z")) {
				publishedArchive = args[++x];
			}
		}
		String hierarchiesStr = StringUtils.join(hierarchies,",");
		print ("Concepts in which Hierarchies? [" + hierarchiesStr + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			hierarchies = response.split(",");
		}
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, Sem_Tag, hasFDParent,noDifferentia,multipleParents");
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
