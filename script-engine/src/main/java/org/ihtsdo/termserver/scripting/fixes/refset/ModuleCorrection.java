package org.ihtsdo.termserver.scripting.fixes.refset;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Refset;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModuleCorrection extends TermServerScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModuleCorrection.class);

	String wrongModule = "900000000000207008";
	String rightModule = "554471000005108";  //DK
	String refsetId = "900000000000490003"; //Description Inactivations

	//Set this to null when working with pre-versioned content
	String forceEffectiveTime = "20170930";
	
	List<String> descIds = new ArrayList<>();
	
	public static void main (String[] args) throws TermServerScriptException, IOException {
		ModuleCorrection app = new ModuleCorrection();
		app.init(args);
		app.loadEntriesToFix();
		app.doFix();
		LOGGER.info("Processing complete.");
	}
	
	private void loadEntriesToFix() throws IOException {
		List<String> lines = Files.readLines(getInputFile(), Charsets.UTF_8);
		LOGGER.info("Loading affected description ids from {}", getInputFile());
		for (String line : lines) {
			descIds.add(line);
		}
	}
	
	public void doFix() {
		for (String descId : descIds) {
			try {
				fixRefsetEntry(descId);
			} catch (Exception e) {
				LOGGER.info("Unable to fix refsetEntry " + descId + " due to " + e);
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
		
		RefsetMember r = refset.getItems().iterator().next();
		if (forceEffectiveTime != null || r.getModuleId().equals(wrongModule)) {
			if (forceEffectiveTime != null) {
				r.setEffectiveTime(forceEffectiveTime);
			}
			r.setModuleId(rightModule);
		} else {
			LOGGER.info("No change required - "+ r.getId() + " for " + descId);
			return;
		}
		
		//Save
		tsClient.updateRefsetMember(project.getBranchPath(), r, (forceEffectiveTime != null));
		LOGGER.info("Fixed " + r.getId() + " for " + descId);
	}
}

