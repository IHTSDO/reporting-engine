package org.ihtsdo.termserver.scripting.delta;

import java.io.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
/**
 * INFRA-5000 We have rows appearing in the delta which do not represent actual changes
 * since the last release.   We're going to work through the delta and re-assert
 * the snapshot effective time where this occurs so as to produce a delta that can
 * be imported to reset the delta back to its previous state
 * 
 * TODO Check refset components also.
 * TODO Create a report of the "dirty" components output
 */
public class RevertNoChangeDelta extends DeltaGenerator {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RevertNoChangeDelta app = new RevertNoChangeDelta();
		try {
			app.getArchiveManager().setPopulateReleasedFlag(true);
			app.newIdsRequired = false;
			app.runStandAlone = true;
			app.additionalReportColumns="ComponentType, ComponentId, Info, Data";
			app.init(args);
			app.getGraphLoader().setDetectNoChangeDelta(true);
			app.loadProjectSnapshot(false);  //Not just FSN, load all terms with lang refset also
			app.postInit();
			app.outputModifiedComponents(true);
			app.flushFiles(false, true); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(app.outputDirName));
		} finally {
			app.finish();
		}
	}
	
}
