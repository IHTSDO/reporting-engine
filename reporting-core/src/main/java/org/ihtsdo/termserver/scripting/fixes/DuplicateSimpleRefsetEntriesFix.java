package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateSimpleRefsetEntriesFix extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateSimpleRefsetEntriesFix.class);

	private static String simpleRefsetId = "723264001"; // Lateralisable body structure reference set
	Map<Concept, Set<RefsetMember>> duplicateMembers = new HashMap<>();
	Map<Concept, RefsetMember> conceptsSeen = new HashMap<>(); //These are our first encounter, which we'll retain
	
	protected DuplicateSimpleRefsetEntriesFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DuplicateSimpleRefsetEntriesFix fix = new DuplicateSimpleRefsetEntriesFix(null);
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
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			//Find all refsetIds to be deleted for this concept
			for (RefsetMember rm : duplicateMembers.get(c)) {
				RefsetMember retained = conceptsSeen.get(c);
				if (!rm.isReleased()) {
					changesMade += deleteRefsetMember(t, rm.getId());
					report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_DELETED, rm.getId(), "Retained", retained.getId());
				} else {
					rm.setActive(false);
					changesMade += updateRefsetMember(t, rm, info);
					report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, rm.toString());
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
		//We're only recovering Refset Members with no effective time
		//So this class won't work with new members duplicating with historic ones.
		for (RefsetMember rm : tsClient.findRefsetMembers(project.getBranchPath(), simpleRefsetId, true)) {
			Concept c = gl.getConcept(rm.getReferencedComponentId());
			if (conceptsSeen.containsKey(c)) {
				Set<RefsetMember> duplicates = duplicateMembers.get(c);
				if (duplicates == null) {
					duplicates = new HashSet<>();
					duplicateMembers.put(c, duplicates);
				}
				duplicates.add(rm);
			} else {
				conceptsSeen.put(c, rm);
			}
		}
		
		return new ArrayList<Component>(SnomedUtils.sortFSN(duplicateMembers.keySet()));
	}

}
