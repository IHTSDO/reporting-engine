package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * ISRS-1267 Based on some known component ids, I want to create a delta with the 
 * state of those components from some prior state.
 * In this case setting -p SnomedCT_InternationalRF2_PRODUCTION_20220331T120000Z.zip 
 */
public class CreateReversionDeltaPatch extends DeltaGenerator {
	
	private String[] componentIds = new String[] 
	{
		"2573280016","2576552011","2579706018", "3013784017",
		"071a1f98-3d6f-52e2-b58f-1cf2704c9cab",
		"437fbd1b-a203-59f1-9a99-7e17f368a848",
		"b5e2d3b1-66b1-51ac-8e97-4ba57ff6f2ca",
		"c7e25721-c5ac-5eef-866d-5aeeabfd92a6",
		"d0bbd6c1-1d0d-53f6-af2c-4cbe872ad0d0",
		"ea89068e-06e1-5508-b816-77b52b4125d8",
		"f0dc60d0-b122-5b9b-8406-a259f92aae67",
	};
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CreateReversionDeltaPatch delta = new CreateReversionDeltaPatch();
		try {
			delta.runStandAlone=true;
			delta.newIdsRequired=false;
			delta.additionalReportColumns = "FSN, SemTag, Severity, ChangeType, Detail, Additional Detail, , ";
			delta.init(args);
			delta.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			delta.postInit();
			delta.createReversionDeltaPatch();
			delta.flushFiles(false, true); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}
	
	private void createReversionDeltaPatch() throws TermServerScriptException {
		//Work through all components before outputting incase we make multiple changes
		//to the same concept.  Don't want duplicate rows output
		for (String componentId : componentIds) {
			Component c = gl.getComponent(componentId);
			if (c == null) {
				warn("Did not find component: " + componentId);
			} else {
				c.setDirty();
				Concept concept = gl.getComponentOwner(componentId);
				concept.setModified();
				report(concept, Severity.LOW, ReportActionType.COMPONENT_REVERTED,c.getEffectiveTime(), c);
			}
		}
		
		for (Concept c : gl.getAllConcepts()) {
			if (c.isModified()) {
				outputRF2(c);
			}
		}
	}

}
