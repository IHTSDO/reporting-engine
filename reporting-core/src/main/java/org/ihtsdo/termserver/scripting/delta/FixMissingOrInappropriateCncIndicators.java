package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
	Where a concept is inactive, it's active descriptions should have the inactivation indicator 
	900000000000495008 |Concept non-current (foundation metadata concept)| applied against them.
	See INFRA-1407 and ISRS-225
	
	This class replaces the "fix" version so that we are sure of having new UUIDs for these new
	indicators
	
	MAINT-489
*/

public class FixMissingOrInappropriateCncIndicators extends DeltaGenerator implements ScriptConstants{

	private static final int MUT_IDX_ACTIVE = 0;
	private static final int MUT_IDX_VALUE = 4;

	public static void main(String[] args) throws TermServerScriptException {
		FixMissingOrInappropriateCncIndicators delta = new FixMissingOrInappropriateCncIndicators();
		try {
			delta.newIdsRequired = false;
			delta.init(args);

			delta.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			delta.getGraphLoader().setRecordPreviousState(true);

			delta.loadProjectSnapshot();
			delta.additionalReportColumns = "Description ET, Details";
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.process();
			delta.createOutputArchive(false);
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				if (inScope(d)) {
					processDescription(c, d);
				}
			}
			outputRF2(c);
		}
	}

	private void processDescription(Concept c, Description d) throws TermServerScriptException {
		if (d.isActiveSafely()) {
			if (!c.isActiveSafely() && d.getInactivationIndicator() == null && inScope(d)) {
				d.setInactivationIndicator(InactivationIndicator.CONCEPT_NON_CURRENT);
				InactivationIndicatorEntry iie = d.getFirstActiveInactivationIndicatorEntry();
				report(c, Severity.LOW, ReportActionType.INACT_IND_ADDED, d, iie);
				incrementSummaryInformation("Inactivation indicators added");
			} else if (c.isActiveSafely()) {
				checkForCncInidicatorAndInactivate(c, d, "active");
			}
		} else if (!d.isActiveSafely()) {
			checkForCncInidicatorAndInactivate(c, d, "inactive");
		}
	}

	private void checkForCncInidicatorAndInactivate(Concept c, Description d, String activeStateStr) throws TermServerScriptException {
		InactivationIndicatorEntry iie = d.getFirstActiveInactivationIndicatorEntry();
		if (iie != null && iie.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
			iie.setActive(false);
			//Now if this inactivation indicator was previously inactive with a different value, we should return it to that
			//state, otherwise we're going to see a change to already inactive components
			String[] previousState = iie.getPreviousState();
			if (previousState != null && previousState[MUT_IDX_ACTIVE].equals("0")) {
				iie.setInactivationReasonId(previousState[MUT_IDX_VALUE]);
				report(c, Severity.LOW, ReportActionType.INACT_IND_MODIFIED, "Inactivation indicator reset to previous inactive value", iie);
				incrementSummaryInformation("Inactivation indicators reset to previous state for "+ activeStateStr + " description");
			} else {
				report(c, Severity.LOW, ReportActionType.INACT_IND_INACTIVATED, d, iie);
				incrementSummaryInformation("Inactivation indicators inactivated for "+ activeStateStr + " description");
			}
			iie.setDirty();
		}
	}

}
