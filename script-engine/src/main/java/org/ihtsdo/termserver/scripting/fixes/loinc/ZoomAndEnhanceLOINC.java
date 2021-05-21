package org.ihtsdo.termserver.scripting.fixes.loinc;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * Look through all LOINC expressions and fix whatever needs worked on
 */
public class ZoomAndEnhanceLOINC extends BatchFix {
	
	enum REL_PART {Type, Target};
	
	private static String TARGET_BRANCH = "MAIN/SNOMEDCT-LOINC/LOINC2020";
	
	protected ZoomAndEnhanceLOINC(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ZoomAndEnhanceLOINC fix = new ZoomAndEnhanceLOINC(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.selfDetermining = false;
			fix.reportNoChange = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
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
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			//changesMade += addAttribute(task, loadedConcept);
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
	
	public void upgradeLOINCConcept(Concept c) throws TermServerScriptException {
		Set<Relationship> origRels = new HashSet<>(c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE));
		for (Relationship r : origRels) {
			Concept localType = gl.getConcept(r.getType().getId());
			Concept replaceType = replaceIfRequired(c, r, localType, REL_PART.Type);
			if (replaceType == null) {
				report((Task)null, (Concept)r.getSource(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace " + localType + " due to lack of historical association");
				return;
			}
			
			Concept localTarget = gl.getConcept(r.getTarget().getId());
			Concept replaceTarget = replaceIfRequired(c, r, localTarget, REL_PART.Target);
			if (replaceTarget == null) {
				report((Task)null, (Concept)r.getSource(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace " + localTarget + " due to lack of historical association");
				return;
			}
			
			if (!localType.equals(replaceType) || !localTarget.equals(replaceTarget)) {
				Relationship replaceRel = r.clone();
				replaceRel.setType(replaceType);
				replaceRel.setTarget(replaceTarget);
			}
		}
	}


	private Concept replaceIfRequired(Concept c, Relationship r, Concept local, REL_PART relPart) throws TermServerScriptException {
		Concept replacement = local;
		if (!local.isActive()) {
			replacement = getReplacement(local);
			if (local != null) {
				report((Task)null, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, relPart.toString() + " " + local + " replaced by " + replacement);
			}
		}
		return replacement;
	}
	
	private Concept getReplacement(Concept inactiveConcept) throws TermServerScriptException {
		Set<String> assocs = inactiveConcept.getAssociationTargets().getSameAs();
		if (assocs.size() == 1) {
			return gl.getConcept(assocs.iterator().next());
		}
		
		assocs = inactiveConcept.getAssociationTargets().getReplacedBy();
		if (assocs.size() == 0) {
			return null;
		} else {
			return gl.getConcept(assocs.iterator().next());
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		//Don't use local copies of concepts, they might not exist
		for (Concept c : findConceptsByCriteria("module=715515008", TARGET_BRANCH, false)) {
			Concept loadedConcept = loadConcept(c, TARGET_BRANCH);
			upgradeLOINCConcept(loadedConcept);
		}
		return componentsToProcess;
	}
}
