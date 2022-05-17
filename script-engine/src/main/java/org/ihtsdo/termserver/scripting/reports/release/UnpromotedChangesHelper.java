package org.ihtsdo.termserver.scripting.reports.release;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Project;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

public class UnpromotedChangesHelper implements ScriptConstants {
	
	TermServerScript ts;
	Map<String, String> unpromotedChangesMap;

	public UnpromotedChangesHelper(TermServerScript ts) {
		this.ts = ts;
	}
	
	public void populateUnpromotedChangesMap(Project project) throws TermServerScriptException {
		//Re-query our current task/project to obtain just those components which 
		//haven't been promoted
		try {
			File delta = ts.getArchiveManager().generateDelta(project, true);
			unpromotedChangesMap = new HashMap<>();
			loadDeltaZip(delta);
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to obtain unpromoted changes map", e);
		}
	}
	
	private void loadDeltaZip(File archive) throws IOException, TermServerScriptException {
		ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
		ZipEntry ze = zis.getNextEntry();
		try {
			while (ze != null) {
				if (!ze.isDirectory()) {
					Path path = Paths.get(ze.getName());
					loadFile(path, zis);
				}
				ze = zis.getNextEntry();
			}
		}  finally {
			try{
				zis.closeEntry();
				zis.close();
			} catch (Exception e){} //Well, we tried.
		}
	}
	
	private void loadFile(Path path, InputStream is)  {
		try {
			String fileName = path.getFileName().toString();
			
			if (fileName.contains("._")) {
				//TermServerScript.info("Skipping " + fileName);
				return;
			}
			
			if (fileName.contains("sct2_Concept_" )) {
				TermServerScript.info("Loading Concept unpromoted delta file.");
				loadFile(is, IDX_ID);
			} else if (fileName.contains("sct2_Relationship_" )) {
				TermServerScript.info("Loading Relationship unpromoted delta file.");
				loadFile(is, REL_IDX_SOURCEID);
			} else if (fileName.contains("sct2_StatedRelationship_" )) {
				TermServerScript.info("Loading StatedRelationship unpromoted delta file.");
				loadFile(is, REL_IDX_SOURCEID);
			} else if (fileName.contains("sct2_RelationshipConcrete" )) {
				TermServerScript.info("Loading Concrete Relationship unpromoted delta file.");
				loadFile(is, REL_IDX_SOURCEID);
			} else if (fileName.contains("sct2_sRefset_OWLAxiom" )) {
				TermServerScript.info("Loading Axiom unpromoted delta refset file.");
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("sct2_Description_" )) {
				TermServerScript.info("Loading Description unpromoted delta file.");
				loadFile(is, DES_IDX_CONCEPTID);
			} else if (fileName.contains("sct2_TextDefinition_" )) {
				TermServerScript.info("Loading Text Definition unpromoted delta file.");
				loadFile(is, DES_IDX_CONCEPTID);
			} else if (fileName.contains("der2_cRefset_ConceptInactivationIndicatorReferenceSet" )) {
				TermServerScript.info("Loading Concept Inactivation Indicator unpromoted delta file.");
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("der2_cRefset_DescriptionInactivationIndicatorReferenceSet" )) {
				TermServerScript.info("Loading Description Inactivation Indicator unpromoted delta file.");
				loadFile(is, NOT_SET);
			} else if (fileName.contains("der2_cRefset_AttributeValue" )) {
				TermServerScript.info("Loading Concept/Description Inactivation Indicators unpromoted delta file.");
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("Association" ) || fileName.contains("AssociationReferenceSet" )) {
				TermServerScript.info("Loading Historical Association File: " + fileName);
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("Language")) {
				TermServerScript.info("Loading unpromoted delta Language Reference Set File - " + fileName);
				loadFile(is, REF_IDX_REFCOMPID);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load " + path + " due to " + e.getMessage(), e);
		}
	}

	private void loadFile(InputStream is, int conceptIdx) throws IOException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String componentId = lineItems[IDX_ID];
				String owningConcept = null;
				if (conceptIdx != NOT_SET) {
					owningConcept = lineItems[conceptIdx];
				}
				unpromotedChangesMap.put(componentId, owningConcept);
			} else {
				isHeaderLine = false;
			}
		}
	}

	public boolean hasUnpromotedChange(Component c) {
		return unpromotedChangesMap.containsKey(c.getId());
	}

}
