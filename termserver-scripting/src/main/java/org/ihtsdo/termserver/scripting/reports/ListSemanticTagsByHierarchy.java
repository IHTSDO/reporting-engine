package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
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
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts that have been defined (stated) using one or more 
 * Fully Defined Parents
 */
public class ListSemanticTagsByHierarchy extends TermServerScript{
	
	String transientEffectiveDate = new SimpleDateFormat("yyyyMMdd").format(new Date());
	GraphLoader gl = GraphLoader.getGraphLoader();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ListSemanticTagsByHierarchy report = new ListSemanticTagsByHierarchy();
		try {
			report.init(args);
			report.loadProjectSnapshot();  //Load FSNs only
			report.listSemanticTags();
		} catch (Exception e) {
			println("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void listSemanticTags() throws TermServerScriptException {
		//Work through all top level hierarchies and list semantic tags along with their counts
		Concept rootConcept = gl.getConcept(SCTID_ROOT_CONCEPT.toString());
		for (Concept topLevel : rootConcept.getDescendents(IMMEDIATE_CHILD)) {
			Set<Concept> descendents = topLevel.getDescendents(NOT_SET);
			println (topLevel.toString() + " - total: " + descendents.size());
			Multiset<String> tags = HashMultiset.create();
			for (Concept thisDescendent : descendents) {
				tags.add(SnomedUtils.deconstructFSN(thisDescendent.getFsn())[1]);
			}
			for (String tag : tags.elementSet()) {
				println ("\t" + tag + ": " + tags.count(tag));
			}
		}
	}

	
	protected void init(String[] args) throws IOException, TermServerScriptException {
		super.init(args);
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
