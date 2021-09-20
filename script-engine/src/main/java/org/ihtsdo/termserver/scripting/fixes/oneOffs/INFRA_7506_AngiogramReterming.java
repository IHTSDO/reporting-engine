package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

public class INFRA_7506_AngiogramReterming extends BatchFix {

	String ecl = "<< 71388002 |Procedure (procedure)|";
	Concept[] types = new Concept[] { FINDING_SITE, PROCEDURE_SITE, PROCEDURE_SITE_DIRECT, PROCEDURE_SITE_INDIRECT };
	String[] targetTexts = new String[] { "angiography", "angiogram", "arteriography", "arteriogram"};
	String[] exceptionText = new String[] { "anesthesia", "follow-up", "cholangiography", "cholangiogram", "lymphangiography", "lymphangiogram"};
	
	//Map<String, String> termTranslation arteriogram -> arteriography of X 
	//arteriography -> angiography (keep as synonym)
	
	// Add aertieograph as Synonym.
	
	//Check for abbrv in FSN
	
	//Remove imaging 
	
	protected INFRA_7506_AngiogramReterming(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA_7506_AngiogramReterming fix = new INFRA_7506_AngiogramReterming(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.inputFileHasHeaderRow = true;
			fix.expectNullConcepts = true;
			fix.validateConceptOnUpdate = false;
			fix.init(args);
			fix.getArchiveManager().setPopulateReleasedFlag(true);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Task, Desc, SCTID,FSN,ConceptType,Severity,Action, Detail,Details,Sites",
				"Id, FSN, SemTag, Has 'Artery' Site, Sites, Detail"};
		String[] tabNames = new String[] {
				"Processed",
				"Not Processed"};
		super.postInit(tabNames, columnHeadings, false);
		
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
		
		//Does our finding site include some sort of artery?
		boolean hasArtery = false;
		Set<Concept> sites = SnomedUtils.getTargets(c, types, CharacteristicType.INFERRED_RELATIONSHIP);
		String sitesStr = sites.stream()
				.map(f -> f.getFsn())
				.collect(Collectors.joining(",\n"));
		String descriptionsStr = c.getDescriptions(ActiveState.ACTIVE)
				.stream().map(d -> d.toString())
				.collect(Collectors.joining(",\n"));
		for (Concept findingSite : sites) {
			if (identifiesAsArtery(findingSite.getFSNDescription())) {
				hasArtery = true;
			}
		}
		
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!hasArtery && identifiesAsArtery(d) ) {
				if (d.isPreferred()) {
					//report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Description contains 'arteri' but is preferred. Advise?", d);
					//Only report in first pass
					if (t == null) {
						report (SECONDARY_REPORT, c, hasArtery?"Y":"N", sitesStr, "Description contains 'arter(i/y)|aorta' without artery site, but is preferred?", descriptionsStr);
					}
				} else {
					if (t != null) {
						report (t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, "Description contains 'arter(i/y)|aorta' without artery finding site", descriptionsStr, sitesStr);
						removeDescription(t, c, d, InactivationIndicator.INAPPROPRIATE);
					}
					changesMade++;
				}
			} else {
				//Only report in first pass
				if (t == null) {
					report (SECONDARY_REPORT, c,  hasArtery?"Y":"N", sitesStr, "No issues detected", descriptionsStr);
				}
			}
		}
		return changesMade;
	}

	private boolean identifiesAsArtery(Description d) {
		String term = d.getTerm().toLowerCase();
		return (term.contains("arteri") 
				|| term.contains("artery")
				|| term.contains("aorta")
				|| term.contains("aortic"));
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> process = new ArrayList<>();
		List<Concept> conceptsSorted = PROCEDURE.getDescendents(NOT_SET).stream()
				.sorted(Comparator.comparing(Concept::getSemTag)
						.thenComparing(Comparator.comparing(Concept::getFsn)))
				.collect(Collectors.toList());
		
		for (Concept c : conceptsSorted) {
			if (containsTargetText(c)
					&& modifyDescriptions(null, c) > NO_CHANGES_MADE) {
				process.add(c);
			}
		}
		return process;
	}

	private boolean containsTargetText(Concept c) {
		String fsn = c.getFsn().toLowerCase();
		for (String targetText : targetTexts) {
			if (fsn.contains(targetText)) {
				return true;
			}
		}
		return false;
	}
}
