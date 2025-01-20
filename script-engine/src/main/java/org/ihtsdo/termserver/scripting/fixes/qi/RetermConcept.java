package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * SCTQA-321 replace teletherapy with "external beam radiation therapy" in FSNs
 * INFRA-6454 replace "anulus fibrosus" with "annulus fibrosus" and retain original as synonym
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RetermConcept extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(RetermConcept.class);

	private Map<String, String> termMap = new HashMap<>();
	
	protected RetermConcept(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RetermConcept fix = new RetermConcept(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//termMap.put("teletherapy", "external beam radiation therapy");
		//termMap.put("teleradiotherapy", "external beam radiation therapy");
		termMap.put("anulus fibrosus","annulus fibrosus");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			/*if (concept.getId().equals("399116007")) {
				LOGGER.debug("here");
			}*/
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
		
		//Validate that PT conforms to pattern of FSN minus SemTag
		String expectedOrigPT = SnomedUtils.deconstructFSN(c.getFsn())[0];
		if (!c.getPreferredSynonym().equals(expectedOrigPT)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Existing PT does not fit expected pattern", c.getPreferredSynonym());
		}
		
		List<Description> originalDescriptions = new ArrayList<>(c.getDescriptions(ActiveState.ACTIVE));
		for (Description d : originalDescriptions) {
			if (d.isPreferred() && !d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				changesMade += modifyDescription(t,c,d);
			}
		}
		return changesMade;
	}

	private int modifyDescription(Task t, Concept c, Description d) throws TermServerScriptException {
		String origTerm = d.getTerm();
		String replacement = origTerm;
		
		//We're not going to modify the PT, we're going to replace it with the modified FSN minus semtag
		if (d.getType().equals(DescriptionType.SYNONYM)) {
			origTerm = SnomedUtils.deconstructFSN(c.getFsn())[0];
			replacement = origTerm;
			if (d.getAcceptabilityMap().size() < 2) {
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "PT marked for replacment has dialect variation");
			}
		}
		
		for (String term : termMap.keySet()) {
			if (origTerm.contains(term)) {
				replacement = origTerm.replace(term, termMap.get(term));
				break;
			} else {
				//Try a capitalized version
				String capTerm = StringUtils.capitalizeFirstLetter(term);
				if (origTerm.contains(capTerm)) {
					String capReplacement =  StringUtils.capitalizeFirstLetter(termMap.get(term));
					replacement = origTerm.replace(capTerm, capReplacement);
					break;
				}
			}
		}
		
		if (replacement.equals(d.getTerm())) {
			LOGGER.debug("Failed to replace term in " + c);
		} else {
			replaceDescription(t, c, d, replacement, null, true, "");  //Yes demote PTs
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return gl.getAllConcepts()
				.stream()
				.filter(c -> c.isActive())
				.filter(c -> containsMatchingTerm(c))
				.collect(Collectors.toList());
	}

	private boolean containsMatchingTerm(Concept c) {
		String fsn = c.getFsn().toLowerCase();
		String pt = c.getPreferredSynonym().toLowerCase();
		for (String term : termMap.keySet()) {
			if (fsn.contains(term)) {
				return true;
			}
			if (pt.contains(term)) {
				return true;
			}
		}
		return false;
	}
}
