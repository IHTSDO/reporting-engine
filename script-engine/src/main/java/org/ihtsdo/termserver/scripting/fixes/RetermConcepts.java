package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

public class RetermConcepts extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(RetermConcepts.class);
	
	private String identifyingText = null;
	private String excludeText = null;
	private Map<String, String> replacementMap = new HashMap<>();
	private String prefix = null;
	private static final boolean NORMALIZE_PT = true;  //
	private boolean forcePTAlignment = false;
	private Collection<Concept> requiredAttributeValue = null;
	Map<String, Concept> currentFSNs = new HashMap<>();
	List<DescriptionType> typesOfInterest = List.of(DescriptionType.FSN, DescriptionType.SYNONYM);
	
	protected RetermConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RetermConcepts fix = new RetermConcepts(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_UPDATES);
		subsetECL = "<< " + CLINICAL_FINDING.getConceptId();

		//text should be lower case.
		replacementMap.put("tumor", "neoplasm");
		replacementMap.put("tumour", "neoplasm");
		
		requiredAttributeValue = null;

		LOGGER.info("Obtaining all current FSNs to avoid creating a new duplicate...");
		currentFSNs = gl.getAllConcepts().stream()
				.collect(Collectors.toMap(Concept::getFsn, Function.identity(), (existing, replacement) -> existing));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = reterm(task, loadedConcept);
			if (changesMade > 0 && forcePTAlignment) {
				changesMade += forcePTAlignment(task, loadedConcept);
			}
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

	private int reterm(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
			String find = entry.getKey();
			String replace = entry.getValue();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, typesOfInterest)) {
				if (d.isPreferred()) {
					changesMade += retermDescriptionIfRequired(t, c, d, find, replace);
				}
			}
		}
		return changesMade;
	}

	private int retermDescriptionIfRequired(Task t, Concept c, Description d, String find, String replace) throws TermServerScriptException {
		if ((d.getTerm().contains(find)
				|| d.getTerm().contains(StringUtils.capitalizeFirstLetter(find)))
				&& (excludeText == null || !d.getTerm().contains(excludeText))) {
			return retermDescription(t, c, d, find, replace);
		} else if (replacementMap.size() == 1) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "FSN did not contain " + find, d);
		}
		return NO_CHANGES_MADE;
	}

	private int retermDescription(Task t, Concept c, Description d, String find, String replace) throws TermServerScriptException {
		if (!d.isReleasedSafely()) {
			report(t, c, Severity.MEDIUM, ReportActionType.INFO, "New description this cycle");
		}
		String replacement = determineReplacementTerm(d, find, replace);

		//If the replacement is a known FSN, we'll skip
		if (currentFSNs.containsKey(replacement)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Replacement is a known FSN: " + replacement);
			return NO_CHANGES_MADE;
		}

		replaceDescription(t, c, d, replacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, NORMALIZE_PT, "", null);

		if (forcePTAlignment) {
			forcePTAlignment(t, c);
		}

		return CHANGE_MADE;
	}

	private String determineReplacementTerm(Description d, String find, String replace) {
		String replacement = d.getTerm().replaceAll(find, replace);

		if (identifyingText != null && d.getTerm().toLowerCase().contains(identifyingText)) {
			if (d.getTerm().toLowerCase().startsWith(identifyingText)) {
				replacement = prefix + " " + StringUtils.decapitalizeFirstLetter(d.getTerm());
			} else {
				replacement = d.getTerm().replaceAll(identifyingText, prefix.toLowerCase() + " " + identifyingText);
			}
		}


		//If the term is unchanged, try an upper case replacement
		if (replacement.equals(d.getTerm())) {
			replacement = d.getTerm().replaceAll(StringUtils.capitalizeFirstLetter(find), StringUtils.capitalizeFirstLetter(replace));
		}
		return replacement;
	}


	private int forcePTAlignment(Task t, Concept c) throws TermServerScriptException {
		String alignedPT = SnomedUtilsBase.deconstructFSN(c.getFsn())[0];
		Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
		
		if (!usPT.equals(gbPT)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "US/GB Variance!", gbPT);
		}
		
		//We've already deleted the unwanted terms, so we'll demote this PT rather than inactivate
		if (!alignedPT.equals(usPT.getTerm())) {
			replaceDescription(t, c, usPT, alignedPT, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, true, null);
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		for (Concept c : SnomedUtils.sort(findConcepts(subsetECL, true, true))) {
			if (!containsRequiredAttributeValue(c)) {
				continue;
			}
			//Flag up any descriptions that have both the find AND the replace in the same term.
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (checkDescriptionForInclusion(c, d, toProcess)) {
					break;
				}
			}
		}
		return toProcess;
	}

	private boolean checkDescriptionForInclusion(Concept c, Description d, List<Component> toProcess) throws TermServerScriptException {
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION) || !d.isPreferred()) {
			return false;
		}

		String termLower = d.getTerm().toLowerCase();
		if (identifyingText != null && termLower.contains(identifyingText)
				&& (excludeText == null  || !termLower.contains(excludeText))
				&& !termLower.contains(prefix.toLowerCase())) {
			toProcess.add(c);
			return true;
		}

		for (Map.Entry<String,String> entry : replacementMap.entrySet()) {
			String find = entry.getKey();
			if (termLower.contains(find)) {
				toProcess.add(c);
				String replace = entry.getValue();
				if (termLower.contains(replace)) {
					report((Task) null, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Term contains both '" + find + "' and '" + replace + "'", d);
					return false;
				}
				return true;
			}
		}
		return false;
	}

	private boolean containsRequiredAttributeValue(Concept c) {
		if (requiredAttributeValue == null) {
			return true;
		}
		return c.getRelationships().stream()
				.filter(r -> r.isActiveSafely())
				.filter(r -> !r.getType().equals(IS_A))
				.filter(r -> !r.isConcrete())
				.map(Relationship::getTarget)
				.anyMatch(t -> requiredAttributeValue.contains(t));
	}
}
