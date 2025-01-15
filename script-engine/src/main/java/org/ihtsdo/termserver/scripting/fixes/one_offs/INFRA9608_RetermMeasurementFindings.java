package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 *INFRA-9606
 */
public class INFRA9608_RetermMeasurementFindings extends BatchFix {

	private static Logger LOGGER = LoggerFactory.getLogger(INFRA9608_RetermMeasurementFindings.class);
	
	private String semTag = " (finding)";
	private String ecl = "((< 118245000 |Measurement finding (finding)| : { 363713009 |Has interpretation (attribute)| = 394844007 |Outside reference range|, 363714003 |Interprets (attribute)| = (<<122869004 |Measurement procedure (procedure)| OR 363787002 |Observable entity (observable entity)|) } )  OR < 118245000 |Measurement finding (finding)| {{ term = wild:\"*ABNORMAL*\", type = fsn }}  OR   < 118245000 |Measurement finding (finding)| {{ term = wild:\"*outside reference range*\", type = fsn }} ) MINUS < 64572001 |Disease| ";

	private InactivationIndicator inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
	
	enum Pattern { ABNORMAL_X, Y_WITH_X, X_ABNORMAL, ALL, CHECK_PT_ONLY, SKIP_AND_REPORT }
	
	Set<String> abnormalSynonyms = new HashSet<>();
	{
		abnormalSynonyms.add("abnormal");
		abnormalSynonyms.add("outside reference range");
	}

	Map<Pattern, String> toTemplateMap = new HashMap<>();
	{
		toTemplateMap.put(Pattern.ALL, "#x# outside reference range");
	}

	Set<String> exclusions = new HashSet<>();

	CaseSensitivityUtils nounHelper;
	
	protected INFRA9608_RetermMeasurementFindings(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA9608_RetermMeasurementFindings fix = new INFRA9608_RetermMeasurementFindings(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.runStandAlone = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail, Additional Detail";
			fix.nounHelper = CaseSensitivityUtils.get();
			fix.init(args);
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
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
			String originalUSPT = loadedConcept.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			String originalGBPT = loadedConcept.getPreferredSynonym(GB_ENG_LANG_REFSET).getTerm();
			boolean usgbVariance = false;
			if (!originalUSPT.contentEquals(originalGBPT)) {
				usgbVariance = true;
				report(task, concept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Check gb/us variance");
			}
			changesMade = modifyDescriptions(task, loadedConcept, usgbVariance);
			reportAdditionalSynonyms(task, loadedConcept, originalUSPT);
			if (changesMade > 0) {
				tweakCsIfRequired(task, loadedConcept);
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private void tweakCsIfRequired(Task t, Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) 
					&& !StringUtils.isCaseSensitive(d.getTerm())
					&& !nounHelper.startsWithProperNounPhrase(d.getTerm())) {
				String before = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
				report(t, c, Severity.MEDIUM, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, before + " -> ci", d);
			}
		}
	}

	private void reportAdditionalSynonyms(Task t, Concept c, String originalPT) throws TermServerScriptException {
		String additionalSynonyms = c.getDescriptions(Acceptability.ACCEPTABLE, DescriptionType.SYNONYM, ActiveState.ACTIVE)
				.stream()
				.map(d -> d.getTerm())
				.filter(s -> !s.equals(originalPT))
				.collect(Collectors.joining(",\n"));
		if (!StringUtils.isEmpty(additionalSynonyms)) {
			report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Additional Synonyms", additionalSynonyms);
		}
	}

	private int modifyDescriptions(Task t, Concept c, boolean usgbVariance) throws TermServerScriptException {
		int changesMade = 0;
		PatternOfX patternOfX = identifyPattern(c.getFSNDescription(), true);
		
		if (patternOfX.equals(Pattern.CHECK_PT_ONLY)) {
			checkPtMatchesFSN(t, c);
			return NO_CHANGES_MADE;
		} else if (patternOfX.equals(Pattern.CHECK_PT_ONLY)) {
			return NO_CHANGES_MADE;
		} else if (patternOfX.equals(Pattern.SKIP_AND_REPORT)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Pattern Y with/and X, or 'borderline' requires manual intervention");
			return NO_CHANGES_MADE;
		}
		
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			PatternOfX thisPatternOfX = patternOfX;
			if (usgbVariance && d.isPreferred(GB_ENG_LANG_REFSET) && d.getType().equals(DescriptionType.SYNONYM) ) {
				thisPatternOfX = identifyPattern(d, false);
			}
			String replacement = replaceDescription(thisPatternOfX, d);
			if (!replacement.equals(d.getTerm())) {
				if (StringUtils.initialLetterLowerCase(replacement)) {
					replacement = StringUtils.capitalizeFirstLetter(replacement);
				}
				replaceDescription(t, c, d, replacement, inactivationIndicator, true);
				changesMade++;
			}
		}
		
		return changesMade;
	}

	private void checkPtMatchesFSN(Task t, Concept c) throws TermServerScriptException {
		String term = SnomedUtils.deconstructFSN(c.getFsn())[0];
		if (!c.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm().equals(term)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "PT / FSN variation", c.getPreferredSynonym(US_ENG_LANG_REFSET));
		}
	}

	private PatternOfX identifyPattern(Description d, boolean isFSN) throws TermServerScriptException {
		String term = d.getTerm();
		if (isFSN) {
			term = SnomedUtils.deconstructFSN(d.getTerm())[0];
		}
		String termLower = term.toLowerCase();
		
		if (term.endsWith("outside reference range")) {
			return new PatternOfX(Pattern.CHECK_PT_ONLY, null);
		}
		
		for (String synonym : abnormalSynonyms) {
			if (termLower.startsWith(synonym)) {
				String x = term.substring(synonym.length() + 1);
				return new PatternOfX(Pattern.ABNORMAL_X, x);
			} else if (term.contains(" with ") || term.contains(" and ") || term.contains("orderline")) {
				return new PatternOfX(Pattern.SKIP_AND_REPORT, null);
			} else if (termLower.endsWith(synonym)) {
				int cut = termLower.indexOf(synonym);
				String x = term.substring(0, cut);
				return new PatternOfX(Pattern.X_ABNORMAL, x);
			}
		}
		
		throw new TermServerScriptException("Unable to identify term pattern in " + term);
	}
	

	private String replaceDescription(PatternOfX patternOfX, Description d) {
		String replacement = d.getTerm();
		//We only need to change preferred terms (PT or FSN)
		if (d.isPreferred()) {
			String template = toTemplateMap.get(Pattern.ALL);
			replacement = template.replace("#x#", patternOfX.x);
		}
		
		if (d.getType().equals(DescriptionType.FSN)) {
			replacement += semTag;
		}
		replacement = replacement.replaceAll("  ", " ");
		return replacement;
	}


	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		setQuiet(true);
		for (Concept c : SnomedUtils.sort(findConcepts(ecl))) {
			if (isExcluded(c)) {
				/*if (c.getFsn().startsWith("Secondary")) {
					reportLoud((Task)null, c, Severity.LOW, ReportActionType.SKIPPING, "Excluded due to lexical match");
				}*/
			} else {
				try {
					if (modifyDescriptions(null, c.cloneWithIds(), false) > 0) {
						process.add(c);
					}
				} catch (Exception e) {
					//reportLoud((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, e);
					LOGGER.info("{} : {}", e.getMessage(), c);
				}
			}
		}
		setQuiet(false);
		return process;
	}
	
	private boolean isExcluded(Concept c) {
		for (String exclusion : exclusions) {
			if (c.getFsn().contains(exclusion)) {
				return true;
			}
		}
		return false;
	}

	class PatternOfX {
		String x;
		String y;
		Pattern pattern;
		
		PatternOfX(Pattern pattern, String x) {
			this.pattern = pattern;
			this.x = x;
		}
		
		public boolean equals(Pattern pattern) {
			return this.pattern.equals(pattern);
		}

		PatternOfX(Pattern pattern, String y, String x) {
			this.pattern = pattern;
			this.x = x;
			this.y = y;
		}
		
		public String toString() {
			return pattern.toString() + " x='" + x + "'"  + (y == null? "" : " y='" + y + "'");
		}
	}
}
