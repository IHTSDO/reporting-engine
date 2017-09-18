package org.ihtsdo.termserver.scripting.fixes.refset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Project;
import org.ihtsdo.termserver.scripting.domain.Refset;
import org.ihtsdo.termserver.scripting.domain.RefsetEntry;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class ModuleCorrection extends TermServerScript/*extends RefsetFixer*/ {

	String wrongModule = "900000000000207008";
	String rightModule = "554471000005108";  //DK
	String refsetId = "900000000000490003"; //Description Inactivations
	String fileToProcess = "";
	
	List<String> descIds = new ArrayList<String>();
	
	public static void main (String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ModuleCorrection app = new ModuleCorrection();
		app.init(args);
		app.loadEntriesToFix();
		app.doFix();
		println ("Processing complete. ");
	}
	
	private void loadEntriesToFix() throws IOException {
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		println ("Loading affected description ids from " + inputFile);
		for (String line : lines) {
			descIds.add(line);
		}
	}
	
	public void doFix() {
		for (String descId : descIds) {
			try {
				fixRefsetEntry(descId);
			} catch (Exception e) {
				println ("Unable to fix refsetEntry " + descId + " due to " + e);
			}
		}
	}
	
	void fixRefsetEntry(String descId) throws SnowOwlClientException, TermServerScriptException {
		//Load the refset entry
		Refset refset = tsClient.loadRefsetEntries(project.getBranchPath(), refsetId, descId);
		
		//Modify - ensure that there is only one entry
		if (refset.getItems().size() != 1) {
			throw new TermServerScriptException("Unable to fix " + descId + " as has " + refset.getItems().size() + " refset entries");
		}
		
		RefsetEntry refsetEntry = refset.getItems().get(0);
		if (refsetEntry.getModuleId().equals(wrongModule)) {
			refsetEntry.setModuleId(rightModule);
		} else {
			println ("No change required - "+ refsetEntry.getId() + " for " + descId);
			return;
		}
		
		//Save
		tsClient.updateRefsetMember(project.getBranchPath(), refsetEntry);
		println ("Fixed " + refsetEntry.getId() + " for " + descId);
	}

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

/*
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

	@Override
	protected Batch formIntoBatch(String fileName,
			java.util.List<Concept> allConcepts, String branchPath)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
	*/
}

