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
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */
public class FdParentsReport extends TermServerScript{
	
	Set<Concept> modifiedConcepts = new HashSet<Concept>();
	List<String> criticalErrors = new ArrayList<String>();
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	Concept filterOnType = null; 
	CharacteristicType filterOnCharacteristicType = null;
	ActiveState filterOnActiveState = null;
	Multiset<String> allSemanticTags= HashMultiset.create();
	Multiset<String> semanticTagsReported= HashMultiset.create();
	Concept subHierarchy = null;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		FdParentsReport fix = new FdParentsReport();
		try {
			fix.init(args);
			fix.loadProjectSnapshot();  //Load FSNs only
			fix.reportFdParents();
			fix.reportStats();
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
	
	
	
	private void reportStats() {
		println ("FD Parents per semantic tag\n========================");
		int totalConcepts = 0;
		for (Multiset.Entry<String> entry : semanticTagsReported.entrySet()) {
			String thisSemanticTag = entry.getElement();
			int reported = entry.getCount();
			totalConcepts += reported;
			int total = allSemanticTags.count(thisSemanticTag);
			float percentage = ((float)reported / (float)total ) * 100;
			String percStr = String.format("%1.1f", percentage);
			println (thisSemanticTag + " - " + reported + " / " + total + " = " + percStr + "%.");
		}
		println ("Total concepts reported: " + totalConcepts);
		
	}



	private void reportFdParents() throws TermServerScriptException {
		Collection<Concept> concepts = null;
		if (subHierarchy != null) {
			concepts = subHierarchy.getDescendents(NOT_SET);
		} else {
			concepts = gl.getAllConcepts();
		}
		
		for (Concept c : concepts) {
			if (c.isActive()) {
				String semanticTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				allSemanticTags.add(semanticTag);
				for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
					if (p.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
						semanticTagsReported.add(semanticTag);
						report(c, semanticTag);
						break;
					}
				}
			}
		}
	}

	protected void report (Concept c, String semanticTag) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn() + QUOTE_COMMA + 
						c.getEffectiveTime() + COMMA_QUOTE +
						c.getDefinitionStatus() + QUOTE_COMMA_QUOTE +
						semanticTag + QUOTE;
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		print ("Filter for a particular sub-hierarchy? (eg 373873005 or return for none): ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			subHierarchy = gl.getConcept(response);
		}
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String filter = (subHierarchy == null) ? "" : "_" + subHierarchy.getConceptId();
		String reportFilename = getScriptName() + filter + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
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
		return null;
	}
}
