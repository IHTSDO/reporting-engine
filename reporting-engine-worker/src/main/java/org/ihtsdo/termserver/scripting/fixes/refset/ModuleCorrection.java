package org.ihtsdo.termserver.scripting.fixes.refset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Refset;
import org.ihtsdo.termserver.scripting.domain.RefsetEntry;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

public class ModuleCorrection extends TermServerScript/*extends RefsetFixer*/ {

	String wrongModule = "900000000000207008";
	String rightModule = "554471000005108";  //DK
	String refsetId = "900000000000490003"; //Description Inactivations
	String fileToProcess = "";
	
	//Set this to null when working with pre-versioned content
	String forceEffectiveTime = "20170930";
	
	List<String> descIds = new ArrayList<String>();
	
	public static void main (String[] args) throws TermServerScriptException, IOException {
		ModuleCorrection app = new ModuleCorrection();
		app.init(args);
		app.loadEntriesToFix();
		app.doFix();
		info ("Processing complete.");
	}
	
	private void loadEntriesToFix() throws IOException {
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		info ("Loading affected description ids from " + inputFile);
		for (String line : lines) {
			descIds.add(line);
		}
	}
	
	public void doFix() {
		for (String descId : descIds) {
			try {
				fixRefsetEntry(descId);
			} catch (Exception e) {
				info ("Unable to fix refsetEntry " + descId + " due to " + e);
			}
		}
	}
	
	void fixRefsetEntry(String descId) throws TermServerScriptException {
		//Load the refset entry
		Refset refset = tsClient.loadRefsetEntries(project.getBranchPath(), refsetId, descId);
		
		//Modify - ensure that there is only one entry
		if (refset.getItems().size() != 1) {
			throw new TermServerScriptException("Unable to fix " + descId + " as has " + refset.getItems().size() + " refset entries");
		}
		
		RefsetEntry refsetEntry = refset.getItems().get(0);
		if (forceEffectiveTime != null || refsetEntry.getModuleId().equals(wrongModule)) {
			if (forceEffectiveTime != null) {
				refsetEntry.setEffectiveTime(forceEffectiveTime);
			}
			refsetEntry.setModuleId(rightModule);
		} else {
			info ("No change required - "+ refsetEntry.getId() + " for " + descId);
			return;
		}
		
		//Save
		tsClient.updateRefsetMember(project.getBranchPath(), refsetEntry, (forceEffectiveTime != null));
		info ("Fixed " + refsetEntry.getId() + " for " + descId);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}

