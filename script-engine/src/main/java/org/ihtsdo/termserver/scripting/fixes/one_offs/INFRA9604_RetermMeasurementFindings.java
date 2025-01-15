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
 *INFRA-9604
 */
public class INFRA9604_RetermMeasurementFindings extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(INFRA9604_RetermMeasurementFindings.class);
	
	String semTag = " (finding)";
	String ecl = "((< 118245000 |Measurement finding (finding)|  : {  363713009 |Has interpretation (attribute)| = 281301001 |Within reference range (qualifier value)|, 363714003 |Interprets (attribute)| = (<<122869004 |Measurement procedure (procedure)| OR 363787002 |Observable entity (observable entity)|) } ) OR ( < 118245000 |Measurement finding (finding)| {{ term = wild:\"*normal*\", type = fsn }}  OR   < 118245000 |Measurement finding (finding)| {{ term = wild:\"*within reference range*\", type = fsn }} ))  MINUS ( < 118245000 |Measurement finding (finding)| {{ term = wild:\"*abnormal*\", type = fsn }} OR < 64572001 |Disease| )";
	InactivationIndicator inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
	List<String> includeFsnContaining = new ArrayList<>();
	{
		includeFsnContaining.add(" within reference range");
		includeFsnContaining.add(" normal");
	}
	
	enum Pattern { NORMAL_X, Y_WITH_X_NORMAL, X_NORMAL, ALL, NO_CHANGE_REQUIRED, SKIP_AND_REPORT }
	
	//The order we check in is important because we check for the most specific pattern first.  So use LinkedHashMap, rather than HashMap
	Map<Pattern, String> fromPatternMap = new LinkedHashMap<>();
	{
		fromPatternMap.put(Pattern.NORMAL_X, "Normal #x#");
		fromPatternMap.put(Pattern.Y_WITH_X_NORMAL, "#y# with #x# normal");
		fromPatternMap.put(Pattern.X_NORMAL, "#x# normal");
	}
	
	Map<Pattern, String> toTemplateMap = new HashMap<>();
	{
		toTemplateMap.put(Pattern.ALL, "#x# within reference range");
	}

	Map<Pattern, String> additionalAcceptableSynonyms = new HashMap<>();
	{
		//additionalAcceptableSynonyms.put(Pattern.SECONDARY_MN_TO_X, "Metastatic malignant neoplasm of #x#");
	}
	
	Set<String> exclusions = new HashSet<>();

	CaseSensitivityUtils nounHelper;
	
	protected INFRA9604_RetermMeasurementFindings(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA9604_RetermMeasurementFindings fix = new INFRA9604_RetermMeasurementFindings(null);
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
			String originalPT = loadedConcept.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			changesMade = modifyDescriptions(task, loadedConcept);
			reportAdditionalSynonyms(task, loadedConcept, originalPT);
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

	private int modifyDescriptions(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		PatternOfX patternOfX = identifyPattern(c.getFSNDescription());
		
		if (patternOfX.equals(Pattern.NO_CHANGE_REQUIRED)) {
			checkPtMatchesFSN(t, c);
			return NO_CHANGES_MADE;
		} else if (patternOfX.equals(Pattern.NO_CHANGE_REQUIRED)) {
			return NO_CHANGES_MADE;
		} else if (patternOfX.equals(Pattern.SKIP_AND_REPORT)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Pattern Y with X requires manual intervention");
			return NO_CHANGES_MADE;
		}
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			String replacement = replaceDescription(patternOfX, d);
			if (!replacement.equals(d.getTerm())) {
				replaceDescription(t, c, d, replacement, inactivationIndicator, true);
				changesMade++;
			}
		}
		
		//Are we adding any additional synonyms?
		if (additionalAcceptableSynonyms.size() > 0) {
			String additionalTemplate = additionalAcceptableSynonyms.get(patternOfX.pattern);
			if (additionalTemplate != null) {
				String additionalSynonym = additionalTemplate.replace("#x#", patternOfX.x);
				Description d = Description.withDefaults(additionalSynonym, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
				Description added = addDescription(t, c, d);
				if (StringUtils.isEmpty(added.getEffectiveTime())) {
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private void checkPtMatchesFSN(Task t, Concept c) throws TermServerScriptException {
		String term = SnomedUtils.deconstructFSN(c.getFsn())[0];
		if (!c.getPreferredSynonym(US_ENG_LANG_REFSET).equals(term)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "PT / FSN variation", c.getPreferredSynonym(US_ENG_LANG_REFSET));
		}
	}

	private PatternOfX identifyPattern(Description d) throws TermServerScriptException {
		String term = SnomedUtils.deconstructFSN(d.getTerm())[0];
		
		/*for (Map.Entry<Pattern, String> entry : fromPatternMap.entrySet()) {
			String prefix = entry.getValue();
			if (term.startsWith(prefix)) {
				String x = term.substring(prefix.length() + 1);
				return new PatternOfX(entry.getKey(), x);
			}
		}*/
		if (term.endsWith("within reference range")) {
			return new PatternOfX(Pattern.NO_CHANGE_REQUIRED, null);
		} else if (term.startsWith("Normal")) {
			String x = term.substring(7);
			return new PatternOfX(Pattern.NORMAL_X, x);
		} else if (term.contains("with")) {
			return new PatternOfX(Pattern.SKIP_AND_REPORT, null);
		} else if (term.endsWith("normal")) {
			int cut = term.indexOf("normal");
			String x = term.substring(0, cut);
			return new PatternOfX(Pattern.X_NORMAL, x);
		}
		throw new TermServerScriptException("Unable to identify term pattern in " + term);
	}
	

	private String replaceDescription(PatternOfX patternOfX, Description d) {
		String replacement = d.getTerm();
		//We only need to change preferred terms (PT or FSN)
		if (d.isPreferred()) {
			//String template = toTemplateMap.get(patternOfX.pattern);
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
					if (modifyDescriptions(null, c.cloneWithIds()) > 0) {
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
	
	/*private List<Concept> findQualifyingConcepts() throws TermServerScriptException {
		List<Concept> conceptsOfPotentialInterest = SnomedUtils.sort(findConcepts(ecl));
		List<Concept> conceptsOfInterest = new ArrayList<>();
		RelationshipTemplate targetTemplate = new RelationshipTemplate(gl.getConcept("363713009 |Has interpretation (attribute)|"), gl.getConcept(""));
		Collection<Concept> targetAttributes = findConcepts("<<122869004 |Measurement procedure (procedure)| OR 363787002 |Observable entity (observable entity)|");
		//We're interested in concepts that have an appropriate group, or with expected text
		nextConcept:
		for (Concept c : conceptsOfPotentialInterest) {
			for (String targetText : includeFsnContaining) {
				String normalisedFSN = (" " + c.getFsn()).toLowerCase();
				if (normalisedFSN.contains(targetText)) {
					conceptsOfInterest.add(c);
					continue nextConcept;
				}
			}
			
			//otherwise does it have a group containing a matching role group?
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (g.containsTypeValue(r1))
			}
			
		}
	}*/

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
