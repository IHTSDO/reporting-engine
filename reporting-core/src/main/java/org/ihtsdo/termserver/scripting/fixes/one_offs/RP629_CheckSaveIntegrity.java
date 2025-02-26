package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Save a concept that has description inactivation indicator and association
 */
public class RP629_CheckSaveIntegrity extends BatchFix {
	
	private static Logger LOGGER = LoggerFactory.getLogger(RP629_CheckSaveIntegrity.class);
	
	protected RP629_CheckSaveIntegrity(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		RP629_CheckSaveIntegrity fix = new RP629_CheckSaveIntegrity(null);
		try {
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.populateTaskDescription = false;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(true);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		LOGGER.info("Imported View");
		outputInactiveDescriptions(c);
		
		LOGGER.info("Loaded View");
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		outputInactiveDescriptions(loadedConcept);
		updateConcept(t, loadedConcept, info);
		
		LOGGER.info("Saved View");
		loadedConcept = loadConcept(c, t.getBranchPath());
		outputInactiveDescriptions(loadedConcept);
		return changesMade;
	}

	private void outputInactiveDescriptions(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
			LOGGER.debug(d + " : " + d.getInactivationIndicator());
			LOGGER.debug("   " + SnomedUtils.prettyPrintHistoricalAssociations(d, gl));
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> components = new ArrayList<>();
		//components.add(gl.getConcept("90534006"));
		components.add(gl.getConcept("418249008"));
		return components;
	}

}
