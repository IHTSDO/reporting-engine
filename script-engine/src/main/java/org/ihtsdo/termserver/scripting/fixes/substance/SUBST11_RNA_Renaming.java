package org.ihtsdo.termserver.scripting.fixes.substance;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * SUSBT-11 Rename descendants of 118251005 | Microbial ribosomal ribonucleic acid (substance)
 * as per terming guidelines:  https://confluence.ihtsdotools.org/display/IAP/4.5+Deoxyribonucleic+acid+and+ribonucleic+acid
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SUBST11_RNA_Renaming extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(SUBST11_RNA_Renaming.class);

	Concept subHierarchy;
	Map<String, Concept> organisms = new HashMap<>();
	
	protected SUBST11_RNA_Renaming(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		SUBST11_RNA_Renaming fix = new SUBST11_RNA_Renaming(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		//Populate Organisms by PT
		for (Concept c : ORGANISM.getDescendants(NOT_SET)) {
			for (Description d : c.getDescriptions(Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
				String term = d.getTerm().toLowerCase().replaceAll(" of ", "");
				organisms.put(term, c);
			}
		}
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = remodel(task, loadedConcept);
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

	private int remodel(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//Firstly, what is the organism?
		String term = c.getPreferredSynonym().toLowerCase();
		
		//Remove "Ribosomal ribonucleic acid" and see what we're left with
		term = term.replaceAll("ribosomal", "")
				.replaceAll("ribonucleic", "")
				.replaceAll("acid", "")
				.replaceAll(" of ", "")
				.replaceAll("rrna", "")
				.replaceAll("  ", " ")
				.trim();
		
		Concept organismX = organisms.get(term);
		if (organismX == null) {
			//Are we being quiet?  Report this even so as it won't make the 2nd pass
			Boolean quiet = this.quiet;
			setQuiet(false);
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Could not determine organism", term);
			setQuiet(quiet);
			return NO_CHANGES_MADE;
		}
		
		//FSN pattern is "Ribosomal ribonucleic acid of X organism (substance)"
		//FSN will use the us PT.  Check if it's case sensitive
		Description organismUsPt = organismX.getPreferredSynonym(US_ENG_LANG_REFSET);
	
		if (!term.equals(organismUsPt.getTerm().toLowerCase().replaceAll(" of ", ""))) {
			report(t, c, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Existing organism term is not the PT", organismUsPt, term);
		}
		
		String organismUsPtStr = organismUsPt.getTerm();
		if (organismUsPt.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
			organismUsPtStr = organismUsPtStr.toLowerCase();
		}
		String replacementFSNCounterpart = "Ribosomal ribonucleic acid of " + organismUsPtStr;
		String replacmenetFSN = replacementFSNCounterpart + " (substance)";
		
		//Now we might have one or two PTs depending
		Description organismGbPt = organismX.getPreferredSynonym(GB_ENG_LANG_REFSET);
		String organismGbPTStr = organismGbPt.getTerm();
		if (organismGbPt.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
			organismGbPTStr = organismGbPt.getTerm().toLowerCase();
		}
		boolean dialectSplit = false;
		if (!organismUsPt.equals(organismGbPt)) {
			dialectSplit = true;
		}
		
		String usPT = organismUsPtStr + " rRNA";
		String gbPT = organismGbPTStr + " rRNA";
		
		//Now work through the exist descriptions and change as required.
		List<Description> originalDescriptions = c.getDescriptions(ActiveState.ACTIVE);
		boolean fsnCounterpartEncountered = false;
		for (Description d : originalDescriptions) {
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			boolean isGbPT = d.isPreferred(GB_ENG_LANG_REFSET);
			boolean isUsPT = d.isPreferred(US_ENG_LANG_REFSET);
			boolean isPT = (isGbPT || isUsPT);
			boolean hasUsGbVariance = !(isGbPT && isUsPT);
			Description replacement = null;
			if (isFSN) {
				replacement = replaceDescription(t, c, d, replacmenetFSN, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			} else if (isPT) {
				//Do we need UsGb variance or to remove it?
				if (dialectSplit) {
					if (hasUsGbVariance) {
						//Easy case - we've already got the variance in effect
						String replacementPT = isGbPT ? gbPT : usPT;
						replacement = replaceDescription(t, c, d, replacementPT, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					} else {
						//We need to create variance that doesn't currently exist
						replacement = replaceDescription(t, c, d, usPT, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
						replacement.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET));
						
						Map<String, Acceptability> acceptablity = SnomedUtils.createPreferredAcceptableMap(GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET);
						Description replacementGb = Description.withDefaults(gbPT, DescriptionType.SYNONYM, acceptablity);
						addDescription(t, c, replacementGb);
						changesMade++;
					}
				} else {
					if (!hasUsGbVariance) {
						//Easy case - we don't need variance and there is none
						replacement = replaceDescription(t, c, d, usPT, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					} else {
						//We need to remove existing variance and patch up the acceptability
						if (isGbPT) {
							removeDescription(t, c, d, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
						} else {
							replacement = replaceDescription(t, c, d, usPT, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
							replacement.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(Acceptability.PREFERRED, ENGLISH_DIALECTS));
						}
						changesMade++;
					}
				}
			} else {
				//If this is not the FSN Counterpart, remove it
				if (!d.getTerm().equals(replacementFSNCounterpart)) {
					removeDescription(t, c, d, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					changesMade++;
				} else {
					fsnCounterpartEncountered = true;
				}
			}
			if (replacement != null) {
				changesMade++;
			}
		}
		
		if (!fsnCounterpartEncountered) {
			Description fsnCounterpart = Description.withDefaults(replacementFSNCounterpart, DescriptionType.SYNONYM, Acceptability.PREFERRED);
			addDescription(t, c, fsnCounterpart);
			changesMade++;
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Quietly attempt to remodel all concepts in a first pass, to determine
		//which ones to collect into a batch
		List<Concept> conceptsToProcess = new ArrayList<>();
		setQuiet(true);
		subHierarchy = gl.getConcept("118251005 | Microbial ribosomal ribonucleic acid (substance)");
		for (Concept c : subHierarchy.getDescendants(NOT_SET)) {
			if (remodel(null, c.cloneWithIds()) > 0) {
				conceptsToProcess.add(c);
			} else {
				report((Task)null, c, Severity.NONE, ReportActionType.NO_CHANGE, "Correctly modelled", c.getPreferredSynonym());
			}
				
		}
		setQuiet(false);
		LOGGER.info("Remodelling required for " + conceptsToProcess.size() + " concepts");
		return asComponents(conceptsToProcess);
	}

}
