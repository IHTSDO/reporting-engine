package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 *INFRA-6637 Re-terming and remodel of AIDS concepts
 */
public class INFRA9106_RetermSecondaryTumour extends BatchFix {
	
	String semTag = " (disorder)";
	String ecl = "< 128462008 |Secondary malignant neoplastic disease (disorder)| ";
	InactivationIndicator inactivationIndicator = InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
	
	enum Pattern { SECONDARY_MN_OF_X, SECONDARY_X }
	Map<Pattern, String> fromPrefixMap = new HashMap<>();
	{
		fromPrefixMap.put(Pattern.SECONDARY_MN_OF_X, "Secondary malignant neoplasm of");
		fromPrefixMap.put(Pattern.SECONDARY_X, "Secondary");
	}
	
	Map<Pattern, String> toTemplateMap = new HashMap<>();
	{
		toTemplateMap.put(Pattern.SECONDARY_MN_OF_X, "Metastatic malignant neoplasm to #x#");
		toTemplateMap.put(Pattern.SECONDARY_X, "Metastatic #x#");
	}

	protected INFRA9106_RetermSecondaryTumour(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA9106_RetermSecondaryTumour fix = new INFRA9106_RetermSecondaryTumour(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.runStandAlone = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail, Additional Detail";
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
		PatternOfX patternOfX = identifyPattern(c.getFSNDescription());
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			String replacement = replaceDescription(patternOfX, d);
			if (!replacement.equals(d.getTerm())) {
				replaceDescription(t, c, d, replacement, inactivationIndicator, true);
				changesMade++;
			}
		}
		return changesMade;
	}

	private PatternOfX identifyPattern(Description d) throws TermServerScriptException {
		String term = SnomedUtils.deconstructFSN(d.getTerm())[0];
		for (Map.Entry<Pattern, String> entry : fromPrefixMap.entrySet()) {
			String prefix = entry.getValue();
			if (term.startsWith(prefix)) {
				String x = term.substring(prefix.length() + 1);
				return new PatternOfX(entry.getKey(), x);
			}
		}
		throw new TermServerScriptException("Unable to identify term pattern in " + term);
	}
	

	private String replaceDescription(PatternOfX patternOfX, Description d) {
		String replacement = d.getTerm();
		//We only need to change preferred terms (PT or FSN)
		if (d.isPreferred()) {
			String template = toTemplateMap.get(patternOfX.pattern);
			replacement = template.replace("#x#", patternOfX.x);
		}
		
		if (d.getType().equals(DescriptionType.FSN)) {
			replacement += semTag;
		}
		return replacement;
	}


	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		setQuiet(true);
		for (Concept c : SnomedUtils.sort(findConcepts(ecl))) {
			try {
				if (modifyDescriptions(null, c.cloneWithIds()) > 0) {
					process.add(c);
				}
			} catch (Exception e) {
				report ((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, e);
			}
		}
		setQuiet(false);
		return process;
	}
	
	class PatternOfX {
		String x;
		Pattern pattern;
		
		PatternOfX(Pattern pattern, String x) {
			this.pattern = pattern;
			this.x = x;
		}
		
		public String toString() {
			return pattern.toString() + " x='" + x + "'";
		}
	}
}
