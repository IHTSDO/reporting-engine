package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

/**
 * INFRA-8273 Need to reassert various components back to their previously published state
 * NOTE: Run this script against a release archive
 */
public class ReassertPublishedState extends DeltaGenerator {

	String processMe = "372440000, 384786009";

	public static void main(String[] args) throws TermServerScriptException {
		ReassertPublishedState delta = new ReassertPublishedState();
		delta.standardExecution(args);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleaseFlag(true);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Component Reasserted"};
		String[] tabNames = new String[] {	
				"Reassertions"};
		super.postInit(GFOLDER_MS, tabNames, columnHeadings);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (String sctId : processMe.split(",")) {
			Concept c = gl.getConcept(sctId.trim());
			c.setDirty();
			for (Component component : SnomedUtils.getAllComponents(c)) {
				report(c, component);
				component.setDirty();
			}
			outputRF2(c, true);  //Will only output dirty fields.
		}
	}

}
