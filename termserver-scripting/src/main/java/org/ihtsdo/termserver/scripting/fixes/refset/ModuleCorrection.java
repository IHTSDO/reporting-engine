package org.ihtsdo.termserver.scripting.fixes.refset;

import java.awt.List;
import java.io.IOException;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Refset;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public class ModuleCorrection extends RefsetFixer {
	


	String wrongModule = "900000000000207008";
	String rightModule = "554471000005108";  //DK
	String refsetId = "900000000000490003"; //Description Inactivations

	protected ModuleCorrection(BatchFix clone) {
		super(clone);
	}
	
	public static void main (String[] args) throws TermServerScriptException, IOException {
		ModuleCorrection fix = new ModuleCorrection(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = false;
			fix.init(args);
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	void FixRefsetEntry(Task task, String referencedComponentId) {
		//Load the refset entry
		Refset refset = tsClient.loadRefsetEntries(task, refsetId, referencedComponentId);
		
		//Modify - ensure that there is only one entry
		if (refset.getItems().size() != 1) {
			println("Unable to fix " + referencedComponentId + " as has " + refset.getItems().size() + " refset entries")
			return;
		}
		
		//Save
		
	}


	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		return 1;
	}





	protected List<Concept> identifyConceptsToProcess() throws TermServerScriptException {

	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

	@Override
	protected Batch formIntoBatch(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerScriptException {
		throw new NotImplementedException();
	}
}

