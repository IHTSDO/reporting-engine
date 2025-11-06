package org.ihtsdo.termserver.scripting.delta;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.zip.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 * Compare a delta against the base release and filter out any rows that do 
 * not represent an actual change.
 * 
 * This is useful when exporting a delta from an unversioned branch where
 * MAIN has in fact been versioned eg recovering QI2019 into QIJUL19
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveNoChangeRows extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveNoChangeRows.class);

	File deltaToFilter;
	//Concept hierarchyOfInterest = CLINICAL_FINDING;
	Concept hierarchyOfInterest = BODY_STRUCTURE;
	List<String> semTagsOfInterest; 
	
	public static void main(String[] args) throws TermServerScriptException {
		RemoveNoChangeRows app = new RemoveNoChangeRows();
		try {
			app.newIdsRequired = false;
			app.runStandAlone = true;
			app.additionalReportColumns="ComponentType, ComponentId, Info, Data";
			app.init(args);
			app.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			app.postInit(GFOLDER_ADHOC_UPDATES);
			app.filterNoChangeDelta();
			app.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(app.outputDirName));
		} finally {
			app.finish();
		}
	}
	
	@Override
	public String getReportName() {
		return "RemoveNoChangeRows";
	}
	
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		
		for (int x=0; x<args.length; x++) {
			if (args[x].equals("-f2")) {
				deltaToFilter = new File(args[++x]);
			}
		}
		
		semTagsOfInterest = new ArrayList<>();
		//semTagsOfInterest.add("(disorder)");
		//semTagsOfInterest.add("(finding)");
		semTagsOfInterest.add("(morphologic abnormality)");
		semTagsOfInterest.add("(body structure)");
	}
	
	private void filterNoChangeDelta() throws TermServerScriptException{
		try {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(deltaToFilter));
			ZipEntry ze = zis.getNextEntry();
			try {
				while (ze != null) {
					if (!ze.isDirectory()) {
						Path p = Paths.get(ze.getName());
						String fileName = p.getFileName().toString();
						ComponentType componentType = Rf2File.getComponentType(fileName, FileType.DELTA);
						if (componentType != null && !fileName.startsWith("._")) {
							LOGGER.info("Processing " + fileName);
							processFixDeltaFile(zis, componentType);
						} else {
							LOGGER.info("Skipping unrecognised file: " + fileName);
						}
					}
					ze = zis.getNextEntry();
				}
			} finally {
				zis.closeEntry();
				zis.close();
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to process fix delta " + deltaToFilter.getName(), e);
		}
	}

	private void processFixDeltaFile(InputStream is, ComponentType componentType) throws Exception {
		//Not putting this in a try resource block otherwise it will close the stream on completion and we've got more to read!
		BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
		String line;
		boolean isHeader = true;
		while ((line = br.readLine()) != null) {
			if (!isHeader) {
				String[] lineItems = line.split(FIELD_DELIMITER);
				
				//Filter out LOINC rows
				if (lineItems[IDX_MODULEID].equals(SCTID_LOINC_PROJECT_MODULE)) {
					continue;
				}
				String[] output = processDeltaLine(componentType, lineItems);
				if (output != null) {
					outputRF2(componentType, output);
				}
			} else {
				isHeader = false;
			}
		}
	}

	private String[] processDeltaLine(ComponentType componentType, String[] lineItems) throws TermServerScriptException {
		String id = lineItems[IDX_ID];
		
		/*if (id.equals("10011096023") || id.equals("3727472012")) {
			LOGGER.debug("Check released unchanged component");
		}*/
		
		//Do we know about this component in the release?
		Component existingComponent = gl.getComponent(id);
	
		//Filter out components not relevant to this project
		if (filterNotRelevant(existingComponent, componentType, lineItems)) {
			return null;
		}
		
		if (existingComponent == null) {
			LOGGER.debug("New " + componentType + ": " + id);
			incrementSummaryInformation("New Component - " + componentType);
			return lineItems;
		}
		
		String[] releasedFields;
		try {
			releasedFields = existingComponent.toRF2();
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to express existing component in RF2",e);
		}
		if (!GraphLoader.differsOtherThanEffectiveTime(releasedFields, lineItems)) {
			incrementSummaryInformation("Filtered no change - " + componentType);
			return null;
		}
		incrementSummaryInformation("Valid change - " + componentType);
		return lineItems;
	}

	private boolean filterNotRelevant(Component existingComponent, ComponentType componentType, String[] lineItems) throws TermServerScriptException {
		
		//Filter any new but inactive components
		if (existingComponent == null && lineItems[IDX_ACTIVE].equals(INACTIVE_FLAG)) {
			incrementSummaryInformation("Filtered inactive but previously unknown inferred component");
			return true;
		}
		
		//If it's an inferred relationship, we're only interested in inactivations to avoid validation errors due to inactive concepts
		if (componentType.equals(ComponentType.INFERRED_RELATIONSHIP) && lineItems[IDX_ACTIVE].equals(ACTIVE_FLAG)) {
			incrementSummaryInformation("Filtered active inferred relationship");
			return true;
		}
		
		Concept owner = null;
		if (existingComponent != null) {
			owner = gl.getComponentOwner(componentType, existingComponent);
		} else {
			Component newComponent = gl.createComponent(componentType, lineItems);
			owner = gl.getComponentOwner(componentType, newComponent);
		}
		
		if (owner != null) {
			//Can we work out what hierarchy we're in?
			Concept topLevel = null;
			try {
				topLevel = SnomedUtils.getHighestAncestorBefore(owner, ROOT_CONCEPT);
			} catch (Exception e) {
				if (lineItems[IDX_ACTIVE].equals(ACTIVE_FLAG)) {
					LOGGER.warn ("Failed to find top level of active " + owner);
				}
			}
			if (topLevel != null) {
				if (!topLevel.equals(hierarchyOfInterest) ) {
					incrementSummaryInformation("Filtered wrong hierarchy - " + componentType);
					return true;
				}
			} else {
				//Can we work out the semantic tag and check that?
				if (owner.getFsn() == null) {
					LOGGER.warn ("No FSN available for " + owner);
					return true;
				}
				String semTag = SnomedUtils.deconstructFSN(owner.getFsn())[1];
				if (semTag != null) {
					if (!semTagsOfInterest.contains(semTag)) {
						incrementSummaryInformation("Filtered wrong semtag - " + semTag);
						return true;
					}
				} else {
					LOGGER.warn ("Failed to find topLevel or semtag for " + owner + ": " + String.join(", ", lineItems));
				}
			}
		} else {
			LOGGER.warn ("Failed to find owner of " + componentType + ": " + String.join(", ", lineItems));
		}
		return false;
	}

}
