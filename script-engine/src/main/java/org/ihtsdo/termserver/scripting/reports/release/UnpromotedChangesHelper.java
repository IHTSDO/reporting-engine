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
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UnpromotedChangesHelper implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(UnpromotedChangesHelper.class);

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
	
	private void loadFile(Path path, InputStream is) throws TermServerScriptException  {
		try {
			String fileName = path.getFileName().toString();
			
			if (fileName.contains("._")) {
				//LOGGER.info("Skipping " + fileName);
				return;
			}
			
			if (fileName.contains("sct2_Concept_" )) {
				LOGGER.info("Loading Concept unpromoted delta file.");
				loadFile(is, IDX_ID);
			} else if (fileName.contains("sct2_Relationship_" )) {
				LOGGER.info("Loading Relationship unpromoted delta file.");
				loadFile(is, REL_IDX_SOURCEID);
			} else if (fileName.contains("sct2_StatedRelationship_" )) {
				LOGGER.info("Loading StatedRelationship unpromoted delta file.");
				loadFile(is, REL_IDX_SOURCEID);
			} else if (fileName.contains("sct2_RelationshipConcrete" )) {
				LOGGER.info("Loading Concrete Relationship unpromoted delta file.");
				loadFile(is, REL_IDX_SOURCEID);
			} else if (fileName.contains("sct2_sRefset_OWLAxiom" )) {
				LOGGER.info("Loading Axiom unpromoted delta refset file.");
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("sct2_Description_" )) {
				LOGGER.info("Loading Description unpromoted delta file.");
				loadFile(is, DES_IDX_CONCEPTID);
			} else if (fileName.contains("sct2_TextDefinition_" )) {
				LOGGER.info("Loading Text Definition unpromoted delta file.");
				loadFile(is, DES_IDX_CONCEPTID);
			} else if (fileName.contains("der2_cRefset_ConceptInactivationIndicatorReferenceSet" )) {
				LOGGER.info("Loading Concept Inactivation Indicator unpromoted delta file.");
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("der2_cRefset_DescriptionInactivationIndicatorReferenceSet" )) {
				LOGGER.info("Loading Description Inactivation Indicator unpromoted delta file.");
				loadFile(is, NOT_SET);
			} else if (fileName.contains("der2_cRefset_AttributeValue" )) {
				LOGGER.info("Loading Concept/Description Inactivation Indicators unpromoted delta file.");
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("Association" ) || fileName.contains("AssociationReferenceSet" )) {
				LOGGER.info("Loading Historical Association File: " + fileName);
				loadFile(is, REF_IDX_REFCOMPID);
			} else if (fileName.contains("Language")) {
				LOGGER.info("Loading unpromoted delta Language Reference Set File - " + fileName);
				loadFile(is, REF_IDX_REFCOMPID);
			}
		} catch (IOException e) {
			throw new IllegalStateException("Unable to load " + path + " due to " + e.getMessage(), e);
		}
	}

	private void loadFile(InputStream is, int conceptIdx) throws IOException, TermServerScriptException {
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeaderLine = true;
		while ((line = br.readLine()) != null) {
			if (!isHeaderLine) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				String componentId = lineItems[IDX_ID];
				String parentComponentId = null;
				if (conceptIdx != NOT_SET) {
					parentComponentId = lineItems[conceptIdx];
				}
				unpromotedChangesMap.put(componentId, parentComponentId);
				
				//If the parent component is a description, then also add that as a component in it's own right.
				if (SnomedUtils.isDescriptionSctid(parentComponentId)) {
					Description d = ts.getGraphLoader().getDescription(parentComponentId);
					unpromotedChangesMap.put(parentComponentId, d.getConceptId());
				}
			} else {
				isHeaderLine = false;
			}
		}
	}

	public boolean hasUnpromotedChange(Component c) {
		return unpromotedChangesMap.containsKey(c.getId()) || unpromotedChangesMap.containsValue(c.getId());
	}

}
