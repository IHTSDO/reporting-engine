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
 * Reports all concepts using Prox Prim modelling that have more groups in the 
 * inferred form, than the stated
 */
public class IncreasedProxPrimInferredComplexityReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String publishedArchive;
	String[] hierarchies = {"64572001"}; //Disease (disorder)
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		IncreasedProxPrimInferredComplexityReport report = new IncreasedProxPrimInferredComplexityReport();
		try {
			report.init(args);
			report.loadProjectSnapshot();  //Load FSNs only
			report.reportIncreasedComplexity();
		} catch (Exception e) {
			println("Report failed due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportIncreasedComplexity() throws TermServerScriptException {
		for (String hiearchySCTID : hierarchies) {
			Concept hierarchy = gl.getConcept(hiearchySCTID);
			Set<Concept> allHierarchy = hierarchy.getDescendents(NOT_SET, CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
			Set<Concept> allActiveFD = filterActiveFD(allHierarchy);
			println (hierarchy + " - " + allActiveFD.size() + "(FD) / " + allHierarchy.size() + "(Active)");
			
			for (Concept thisConcept : allActiveFD) {
				List<Concept>parents = thisConcept.getParents(CharacteristicType.STATED_RELATIONSHIP); 
				//If we have a single stated parent of disease, then we're modelled correctly
				if (parents.size() == 1 && parents.get(0).getConceptId().equals(hiearchySCTID)) {
					//How many groups do we have in the stated and inferred forms?
					int statedGroups = countGroups(thisConcept, CharacteristicType.STATED_RELATIONSHIP);
					int inferredGroups = countGroups(thisConcept, CharacteristicType.INFERRED_RELATIONSHIP);
					println (thisConcept + ":  s=" + statedGroups + ", i=" + inferredGroups);
					if (inferredGroups > statedGroups) {
						report(thisConcept, SnomedUtils.deconstructFSN(thisConcept.getFsn())[1]);
					}
				}
			}
		}
		
	}

	private int countGroups(Concept c, CharacteristicType cType) {
		Set<Integer> groupNumbersActive = new HashSet<Integer>();
		List<Relationship> attributes = c.getRelationships(cType, ActiveState.ACTIVE) ;
		for (Relationship r : attributes){
			Integer groupNum = new Integer((int)r.getGroupId());
			if (!groupNumbersActive.contains(groupNum)) {
				groupNumbersActive.add(groupNum);
			}
		}
		return groupNumbersActive.size();
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

	protected void report (Concept c, String semtag) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						semtag + QUOTE;
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException, SnowOwlClientException {
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
		String reportFilename = getScriptName() + "_" + project.getKey().toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToReportFile ("Concept, FSN, Sem_Tag");
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
