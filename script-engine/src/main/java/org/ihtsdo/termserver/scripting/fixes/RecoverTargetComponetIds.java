package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RecoverTargetComponetIds extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(RecoverTargetComponetIds.class);

	String previousReleaseBranch;
	
	protected RecoverTargetComponetIds(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RecoverTargetComponetIds fix = new RecoverTargetComponetIds(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.runStandAlone = false;  //Need to look up the project for MS extensions
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.init(args);
			fix.loadProjectSnapshot(false);  //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		String prevRelease = project.getMetadata().getPreviousRelease();
		previousReleaseBranch = "MAIN/" + SnomedUtils.formatReleaseDate(prevRelease);
		LOGGER.debug("Copying missing data from " + previousReleaseBranch);
		super.postInit();
	}

	@Override
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			for (AssociationEntry a : c.getAssociationEntries()) {
				if (a.getTargetComponentId() == null) {
					//Recover the refset member from the previous release
					RefsetMember previousState = loadRefsetMember(a.getId(), previousReleaseBranch);
					a.setTargetComponentId(previousState.getField("targetComponentId"));
					report(t,c, Severity.LOW, ReportActionType.ASSOCIATION_CHANGED, "Restored missing TargetComponetId",a);
					updateRefsetMember(t, a, info);
					changesMade++;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException("Failed to update refset entry for " + c, e);
		}
		return changesMade;
	}
	
	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		LOGGER.info("Identifying concepts to process");
		List<Component> componentsToProcess = new ArrayList<>();
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			for (AssociationEntry a : c.getAssociationEntries()) {
				if (a.getTargetComponentId() == null) {
					componentsToProcess.add(c);
					continue nextConcept;
				}
			}
		}
		return componentsToProcess;
	}

}
