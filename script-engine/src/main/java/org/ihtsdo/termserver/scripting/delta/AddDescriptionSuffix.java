package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

public class AddDescriptionSuffix extends DeltaGenerator implements ScriptConstants{

	private static final String SUFFIX = " (AJCC)";
	private static final String EXCLUDE = "American Joint Committee on Cancer";
	private static final String BRAKETED_TEXT_REGEX = "\\([^\\)]*\\)";
	private static final int BATCH_SIZE = 100;

	private Concept startingPoint;
	private int lastBatchSize;

	public static void main(String[] args) throws TermServerScriptException {
		AddDescriptionSuffix delta = new AddDescriptionSuffix();
		try {
			delta.init(args);
			delta.loadProjectSnapshot(false); //Need all descriptions loaded.
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.process();
			delta.createOutputArchive(false, delta.lastBatchSize);
		} finally {
			delta.finish();
		}
	}

	@Override
	public void postInit(String googleFolder) throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"SCTID, FSN, SemTag, Severity, Action, Info, detail, , ",
				"SCTID, FSN, SemTag, Reason, detail, detail,"
		};

		String[] tabNames = new String[]{
				"Records Processed",
				"Records Skipped"
		};
		super.postInit(googleFolder, tabNames, columnHeadings);
		startingPoint = gl.getConcept("1222584008 |American Joint Committee on Cancer allowable value (qualifier value)| ");
	}

	@Override
	protected void process() throws TermServerScriptException {
		int conceptsInThisBatch = 0;
		for (Concept c : startingPoint.getDescendants(NOT_SET)) {
			boolean changesMade = false;
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				changesMade |= replaceDescriptionIfRequired(c, d);
			}

			if (changesMade) {
				outputRF2(c);
				conceptsInThisBatch++;
			}

			if (conceptsInThisBatch >= BATCH_SIZE) {
				if (!dryRun) {
					createOutputArchive(false, conceptsInThisBatch);
					outputDirName = "output"; //Reset so we don't end up with _1_1_1
					initialiseOutputDirectory();
					initialiseFileHeaders();
				}
				gl.setAllComponentsClean();
				conceptsInThisBatch = 0;
			}
		}
		lastBatchSize = conceptsInThisBatch;
	}

	private boolean replaceDescriptionIfRequired(Concept c, Description d) throws TermServerScriptException {
		boolean changeMade = false;
		String term = d.getTerm();
		String textSansBrackets = term.replaceAll(BRAKETED_TEXT_REGEX, "");
		if (textSansBrackets.length() < 10 && !term.contains(EXCLUDE)) {
			replaceDescriptionAppendingSuffix(c, d);
			changeMade = true;
		}
		return changeMade;
	}

	private void replaceDescriptionAppendingSuffix(Concept c, Description d) throws TermServerScriptException {
		String newTerm = d.getTerm() + SUFFIX;
		replaceDescription(c, d, newTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
	}

	protected void setCaseSignificance(Description newDescription) {
		//We've added capital letters, so set the case sigificance to CS unless we started with a number
		newDescription.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		if (Character.isDigit(newDescription.getTerm().charAt(0))) {
			newDescription.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		}
	}

	private Description replaceDescription(Concept c, Description d, String newTerm, InactivationIndicator ii) throws TermServerScriptException {
		Description newDescription = d.clone(descIdGenerator.getSCTID());
		newDescription.setTerm(newTerm);
		c.addDescription(newDescription);
		//In a delta, we can't 'delete' anything, so we'll always say we've inactivated it, and let the TS decide if that results in a deletion
		d.setActive(false);
		d.setInactivationIndicator(ii);
		setCaseSignificance(newDescription);
		report(c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, d, ii);
		report(c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, newDescription);
		return newDescription;
	}

}
