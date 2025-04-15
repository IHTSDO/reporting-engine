package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.delta.DeltaGeneratorWithAutoImport;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class RP1013_CS_terms_starting_with_number_should_be_cI extends DeltaGeneratorWithAutoImport implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(RP1013_CS_terms_starting_with_number_should_be_cI.class);

	private CaseSensitivityUtils csUtils;

	enum FixType { STARTING_WITH_NUMBER, ALL_NUMBERS_OR_SYMBOLS}

	private FixType fixType = FixType.ALL_NUMBERS_OR_SYMBOLS;

	public static void main(String[] args) throws TermServerScriptException {
		RP1013_CS_terms_starting_with_number_should_be_cI delta = new RP1013_CS_terms_starting_with_number_should_be_cI();
		try {
			delta.newIdsRequired = false;
			delta.taskPrefix = "RP-1013";
			delta.init(args);
			delta.loadProjectSnapshot(false);
			delta.postInit();
			delta.process();
			delta.flushFilesWithWait(false);
			File archive = SnomedUtils.createArchive(new File(delta.outputDirName));
			delta.importArchiveToNewTask(archive);
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		csUtils = CaseSensitivityUtils.get();
		int conceptsProcessed = 0;
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (conceptsProcessed++%10000==0) {
				LOGGER.info("Concepts processed: {}", (conceptsProcessed-1));
				getRF2Manager().flushFiles(false);
			}

			if (c.getId().equals("277268008")) {
				LOGGER.debug("here");
			}

			if (c.isActiveSafely()) {
				if (fixType == FixType.ALL_NUMBERS_OR_SYMBOLS) {
					processConceptAllNumbersOrSymbols(c);
				} else {
					processCSConceptStartingWithNumber(c);
				}
			}
		}
	}

	private void processConceptAllNumbersOrSymbols(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			if (d.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtilsBase.deconstructFSN(d.getTerm())[0];
			}
			if (!d.getType().equals(DescriptionType.TEXT_DEFINITION)
					&& !d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)
					&& csUtils.isAllNumbersOrSymbols(term)) {
				String currentCase = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				//These are entirely case-insensitive
				d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
				d.setEffectiveTime(null);
				d.setDirty();
				outputRF2(d);
				report(c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, currentCase +" -> ci", d);
			}
		}
	}

	private void processCSConceptStartingWithNumber(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)
					&& !d.getType().equals(DescriptionType.TEXT_DEFINITION)
					&& csUtils.startsWithNumber(d.getTerm())) {
				//Change to initial character case
				d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
				d.setEffectiveTime(null);
				outputRF2(d);
				report(c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, "CS -> cI", d);
			}
		}
	}
}
