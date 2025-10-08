package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.snapshot.ArchiveManager;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

/* ISRS-1257
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SwitchDescInactivationIndicators extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(SwitchDescInactivationIndicators.class);

	protected SwitchDescInactivationIndicators(final BatchFix clone) {
		super(clone);
	}

	public static void main(final String[] args) throws TermServerScriptException, IOException, InterruptedException {
		final SwitchDescInactivationIndicators fix = new SwitchDescInactivationIndicators(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.runStandAlone = false;  //We need to look up the project path for MS projects
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.getArchiveManager().setRunIntegrityChecks(false);
			fix.init(args);
			fix.loadProjectSnapshot(false); // Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	@Override
	public void init(String[] args) throws TermServerScriptException {
		ArchiveManager mgr = getArchiveManager();
		mgr.setEnsureSnapshotPlusDeltaLoad(true);
		//mgr.setRunIntegrityChecks(false);  //MSSP-1087
		super.init(args);
	}

	@Override
	public int doFix(final Task task, final Concept concept, final String info)
			throws TermServerScriptException, ValidationFailure {
		// We will not load the concept because the Browser endpoint does not populate
		// the full array of inactivation indicators
		int changesMade = 0;
		try {
			changesMade = fixIssues(task, concept, false);
		} catch (final TermServerScriptException e) {
			throw new TermServerScriptException("Failed to remove duplicate inactivation indicator on " + concept, e);
		}
		return changesMade;
	}

	private int fixIssues(final Task t, final Concept c, final boolean trialRun)
			throws TermServerScriptException {
		int changesMade = 0;
			
		for (final Description d : c.getDescriptions()) {
			if (!inScope(d)) {
				continue;
			}
			
			InactivationIndicatorEntry ii = getIndicator(d, SCTID_INACT_GRAMMATICAL_DESCRIPTION_ERROR);
			if (ii != null) {
				ii.setInactivationReasonId(SCTID_INACT_ERRONEOUS);
				changesMade = updateRefsetMember(t, ii, null);
				report(t, c, Severity.LOW, ReportActionType.INACT_IND_MODIFIED, "SCTID_INACT_GRAMMATICAL_DESCRIPTION_ERROR -->", ii);
			}
		}
		return changesMade;
	}


	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		final List<Component> processMe = new ArrayList<Component>();
		
		nextConcept:
		for (final Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				if (inScope(d) && getIndicator(d, SCTID_INACT_GRAMMATICAL_DESCRIPTION_ERROR) != null) {
					processMe.add(c);
					continue nextConcept;
				}
			}
		}
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}
	

	private InactivationIndicatorEntry getIndicator(Description d, String iiSctId) {
		for (InactivationIndicatorEntry entry : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
			if (entry.getInactivationReasonId().equals(iiSctId)) {
				return entry;
			}
		}
		return null;
	}
	
}
