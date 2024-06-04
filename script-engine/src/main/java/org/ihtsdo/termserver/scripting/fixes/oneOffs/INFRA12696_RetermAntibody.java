package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;

public class INFRA12696_RetermAntibody extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA12696_RetermAntibody.class);

	InactivationIndicator inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;

	private Set<String> enforceCapitalization = Set.of("avian", "bovine", "porcine", "equine", "human");

	enum Pattern { ANTIBODY_TO_X, X_ANTIBODY, X_AB }

	protected INFRA12696_RetermAntibody(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA12696_RetermAntibody fix = new INFRA12696_RetermAntibody(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.additionalReportColumns = "Before, After";
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.runStandAlone = true;
			fix.selfDetermining = false;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Before, After";
			fix.init(args);
			fix.getArchiveManager().setPopulateReleasedFlag(true);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = modifyDescriptions(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;

		/*if (c.getId().equals("35021000087108")) {
			LOGGER.info("Debug Captialization");
		}*/

		PatternOfX pattern = identifyPattern(c.getFSNDescription());

		if (pattern.x.contains(" species")) {
			pattern.x = pattern.x.replace(" species", "");
		}

		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		String origDescString = SnomedUtils.getDescriptionsToString(c);

		//FSN will be Antibody to X
		String fsn = "Antibody to " + pattern.x + " (substance)";
		String pt = StringUtils.capitalizeFirstLetter(pattern.x) + " antibody";

		Description fsnDescription = c.getFSNDescription();
		if (!fsnDescription.getTerm().equals(fsn)) {
			Description newFSN = replaceDescription(t, c, fsnDescription, fsn, inactivationIndicator, true);
			if (pattern.isCaseSensitive()) {
				//Because we've started this term with 'Antibody' which is not case sensitive, we can only ever be initial letter case insensitive
				newFSN.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
			} else {
				newFSN.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
			}
			changesMade++;
		}

		Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
		if (!usPT.equals(gbPT)) {
			PatternOfX patternGB = identifyPattern(gbPT);
			String gbPTStr = StringUtils.capitalizeFirstLetter(patternGB.x) + " antibody";
			if (!gbPT.getTerm().equals(gbPTStr)) {
				Description newGBPT = replaceDescription(t, c, gbPT, gbPTStr, inactivationIndicator, true);
				if (patternGB.isCaseSensitive()) {
					//Because we're starting with X, we need to mark entirely case senstitive
					newGBPT.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				}
				addAcceptableDescriptionsIfRequired(t, c, patternGB);
			}
			report(t, c, Severity.MEDIUM, ReportActionType.INFO, "GB US Variance");
		}
		addAcceptableDescriptionsIfRequired(t, c, pattern);

		if (!usPT.getTerm().equals(pt)) {
			Description newUSPT = replaceDescription(t, c, usPT, pt, inactivationIndicator, true);
			if (pattern.isCaseSensitive()) {
				//Because we're starting with X, we need to mark entirely case senstitive
				newUSPT.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			}
			changesMade++;
		}

		for (Description d : originalDescriptions) {
			if (d.getTerm().startsWith("Anti-") || d.getTerm().startsWith("Anti ") || d.getTerm().contains(" species")) {
				removeDescription(t, c, d, null, inactivationIndicator);
				changesMade++;
			}
		}

		int csChanges = normalizeCaseSignificance(c);

		if (csChanges > 0) {
			changesMade += csChanges;
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Case signifiance normalized");
		}

		if (changesMade > 0) {
			report(t, c, Severity.LOW, ReportActionType.INFO, origDescString, SnomedUtils.getDescriptionsToString(c));
		}
		return changesMade;
	}

	private int normalizeCaseSignificance(Concept c) {
		int changesMade = 0;
		Map<String, Set<Description>> mapOfFirstWords = new HashMap<>();
		Set<String> firstWordsThatAreCaseSensitive = new HashSet<>();
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String firstWord = d.getTerm().split(" ")[0];
			mapOfFirstWords.computeIfAbsent(firstWord, k -> new HashSet<>()).add(d);
			if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
				firstWordsThatAreCaseSensitive.add(firstWord);
			}
			//Also any description that starts with a word that is case sensitive, should be marked as case sensitive
			if (enforceCapitalization.contains(firstWord.toLowerCase())) {
				if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
					changesMade++;
				}
			}
		}
		//Make all descriptions that start with a word that is case sensitive, case sensitive
		for (String firstWord : firstWordsThatAreCaseSensitive) {
			for (Description d : mapOfFirstWords.get(firstWord)) {
				if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private void addAcceptableDescriptionsIfRequired(Task t, Concept c, PatternOfX pattern) throws TermServerScriptException {
		String fsnCounterpartStr = "Antibody to " + pattern.x;
		Description fsnCounterpart = Description.withDefaults(fsnCounterpartStr, DescriptionType.SYNONYM,  Acceptability.ACCEPTABLE);
		if (pattern.isCaseSensitive()) {
			fsnCounterpart.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		}
		addDescription(t, c, fsnCounterpart, false);

		String AbStr =  pattern.x + " Ab";
		Description Ab = Description.withDefaults(AbStr, DescriptionType.SYNONYM,  Acceptability.ACCEPTABLE);
		if (pattern.isCaseSensitive()) {
			AbStr = StringUtils.capitalizeFirstLetter(AbStr);
			Ab.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
		}
		addDescription(t, c, Ab, false);
	}

	private PatternOfX identifyPattern(Description d) throws TermServerScriptException {
		String term = SnomedUtils.deconstructFSN(d.getTerm())[0];
		if (term.startsWith("Antibody")) {
			if (term.startsWith("Antibody to")) {
				return new PatternOfX(Pattern.ANTIBODY_TO_X, term.substring(12), d.getCaseSignificance());
			} else {
				return new PatternOfX(Pattern.ANTIBODY_TO_X, term.substring(9), d.getCaseSignificance());
			}
		} else if (term.contains("antibody")) {
			String[] parts = term.split(" antibody");
			if (parts.length > 2) {
				LOGGER.info("Check term: " + term);
			}
			term = parts[0];
			//Do we want to keep that capital letter?
			if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
				if (d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
					term = StringUtils.decapitalizeFirstLetter(term);
				} else {
					term = term.toLowerCase();
				}
			}
			return new PatternOfX(Pattern.X_ANTIBODY, term, d.getCaseSignificance());
		} else if (term.contains(" Ab ")) {
			String[] parts = term.split(" Ab ");
			if (parts.length == 2) {
				return new PatternOfX(Pattern.X_AB, parts[0], d.getCaseSignificance());
			}
		}
		throw new TermServerScriptException("Unable to identify term pattern in " + term);
	}

	class PatternOfX {
		String x;
		Pattern pattern;
		CaseSignificance cs;

		PatternOfX(Pattern pattern, String x, CaseSignificance cs) {
			this.pattern = pattern;
			this.x = x;
			this.cs = cs;

			//Do we need to enforce capitalization?
			String firstWord = x.split(" ")[0];
			if (enforceCapitalization.contains(firstWord.toLowerCase())) {
				this.x = StringUtils.capitalizeFirstLetter(x);
				this.cs = CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			}
		}

		boolean isCaseSensitive() {
			return cs.equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) || cs.equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		}

		public String toString() {
			return pattern.toString() + " x='" + x + "'";
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		Concept c = gl.getConcept(lineItems[0], false, true);
		return new ArrayList<>(Collections.singletonList(c));
	}

}
