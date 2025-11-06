package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.io.Files;

/**
 * ISRS-1267 Based on some known component ids, I want to create a delta with the 
 * state of those components from some prior state.
 * In this case setting -p SnomedCT_InternationalRF2_PRODUCTION_20220331T120000Z.zip 
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateReversionDeltaPatch extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(CreateReversionDeltaPatch.class);

	//If this array is empty, we'll attempt to load from file specified in -f parameter
	private String[] componentIds; /* = new String[] 
	{
			 "1104071000146111",
			 "834111000146117",
			 "9265561000146118",
			 "1678911000146117",
			 "1678881000146117",
			 "1840671000146114",
			 "1678891000146115",
			 "67970631000146123",
			 "68014291000146124",
			 "68006301000146129",
			 "67996161000146129",
			 "68052521000146123"
	};*/
	
	public static void main(String[] args) throws TermServerScriptException {
		CreateReversionDeltaPatch delta = new CreateReversionDeltaPatch();
		try {
			delta.runStandAlone=true;
			delta.newIdsRequired=false;
			delta.additionalReportColumns = "FSN, SemTag, Severity, ChangeType, Detail, Additional Detail, , ";
			delta.init(args);
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.createReversionDeltaPatch();
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	private void createReversionDeltaPatch() throws TermServerScriptException {
		if (componentIds == null) {
			loadComponentsToProcess();
		}
		//Work through all components before outputting incase we make multiple changes
		//to the same concept.  Don't want duplicate rows output
		for (String componentId : componentIds) {
			Component c = gl.getComponent(componentId);
			if (c == null) {
				LOGGER.warn("Did not find component: {}", componentId);
			} else {
				c.setDirty();
				Concept concept = gl.getComponentOwner(componentId);
				if (concept == null) {
					String msg = "No concept could be found as the owner of " + c + " : id=" + componentId;
					report(null, Severity.HIGH, ReportActionType.COMPONENT_REVERTED,c.getEffectiveTime(), c);
					LOGGER.warn(msg);
				} else {
					concept.setModified();
					report(concept, Severity.LOW, ReportActionType.COMPONENT_REVERTED,c.getEffectiveTime(), c);
				}

			}
		}
		
		for (Concept c : gl.getAllConcepts()) {
			if (c.isModified()) {
				outputRF2(c);
			}
		}
	}

	private void loadComponentsToProcess() throws TermServerScriptException {
		LOGGER.debug("Loading {}", getInputFile() );
		try {
			componentIds = Files.readLines(getInputFile(), StandardCharsets.UTF_8).toArray(new String[0]);
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read " + getInputFile(), e);
		}
		
	}

}
