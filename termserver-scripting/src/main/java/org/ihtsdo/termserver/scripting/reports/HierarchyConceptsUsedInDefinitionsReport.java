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

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.IdGenerator;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExportType;
import org.ihtsdo.termserver.scripting.client.SnowOwlClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts in a hierarchy that are used in the definition of other concepts.
 */
public class HierarchyConceptsUsedInDefinitionsReport extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	String publishedArchive;
	String hierarchy = "49062001"; // |Device (physical object)|
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		HierarchyConceptsUsedInDefinitionsReport fix = new HierarchyConceptsUsedInDefinitionsReport();
		try {
			fix.init(args);
			fix.loadProjectSnapshot();  //Load FSNs only
			fix.reportConceptsUsedInDefinition();
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			fix.finish();
		}
	}

	private void reportConceptsUsedInDefinition() throws TermServerScriptException {
		//For all concepts, if it is lateralized, check that concept is listed in our
		//set of lateralizable concepts.
		Concept sourceHierarchy = gl.getConcept(hierarchy);
		Set<Concept> sourceConcepts = filterActive(sourceHierarchy.getDescendents(NOT_SET));
		println ("Active source concepts number " + sourceConcepts.size());
		Multiset<String> tags = HashMultiset.create();
		for (Concept thisConcept : gl.getAllConcepts()) {
			if (sourceConcepts.contains(thisConcept) || !thisConcept.isActive()) {
				continue;
			}
			for (Relationship thisRelationship : thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)){
				//Does this relationship use one of our source concepts as a target?
				if (sourceConcepts.contains(thisRelationship.getTarget())) {
					report (thisRelationship.getTarget(), thisConcept);
					tags.add(SnomedUtils.deconstructFSN(thisConcept.getFsn())[1]);
					break;
				}
			}
		}
		
		for (String tag : tags.elementSet()) {
			println ("\t" + tag + ": " + tags.count(tag));
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

	protected void report (Concept c, Concept usedIn) {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						usedIn + QUOTE_COMMA +
						usedIn.getDefinitionStatus();
		
		writeToFile(line);
	}
	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-z")) {
				publishedArchive = args[++x];
			}
		}
		
		print ("Concepts in which Hierarchy? [" + hierarchy + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			hierarchy = response;
		}
		
		SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
		//String reportFilename = "changed_relationships_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		String reportFilename = getScriptName() + "_" + project.toLowerCase() + "_" + df.format(new Date()) + "_" + env  + ".csv";
		reportFile = new File(outputDir, reportFilename);
		reportFile.createNewFile();
		println ("Outputting Report to " + reportFile.getAbsolutePath());
		writeToFile ("Concept, FSN, UsedToDefine, Defn_Status");
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
