package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class InactivateMembersOnInactiveRefsets extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateMembersOnInactiveRefsets.class);

	protected InactivateMembersOnInactiveRefsets(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		InactivateMembersOnInactiveRefsets fix = new InactivateMembersOnInactiveRefsets(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.runStandAlone = false;  //Need to look up the project for MS extensions
			fix.getArchiveManager().setLoadOtherReferenceSets(true);
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
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				Concept refset = gl.getConcept(rm.getRefsetId());
				if (!refset.isActive()) {
					rm.setActive(false);
					changesMade += updateRefsetMember(t, rm, info);
					report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, refset, rm);
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
		List<Component> processMe = new ArrayList<>();

		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			for (RefsetMember rm : c.getOtherRefsetMembers()) {
				Concept refset = gl.getConcept(rm.getRefsetId());
				if (!refset.isActive() && inScope(refset)) {
					processMe.add(c);
					continue nextConcept;
				}
			}
		}
		return processMe;
	}

}
