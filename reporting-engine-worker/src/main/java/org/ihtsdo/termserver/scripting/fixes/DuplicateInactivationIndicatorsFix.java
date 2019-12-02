package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/*
 * INFRA-2480 Finding concept and description inactivation indicators that are duplicated
 * and remove the unpublished version
*/
public class DuplicateInactivationIndicatorsFix extends BatchFix implements RF2Constants{
	
	protected DuplicateInactivationIndicatorsFix(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		DuplicateInactivationIndicatorsFix fix = new DuplicateInactivationIndicatorsFix(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		//We will not load the concept because the Browser endpoint does not populate the full array of inactivation indicators
		int changesMade = 0;
		try {
			changesMade = fixDuplicateInactivationIndicators(task, concept, false);
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException ("Failed to remove duplicate inactivation indicator on " + concept, e);
		}
		return changesMade;
	}

	private int fixDuplicateInactivationIndicators(Task task, Concept c, boolean trialRun) throws TermServerScriptException {
		int changesMade = 0;
			InactivationIndicatorEntry[] ciis = getDuplicateInactivationIndicators(c, c.getInactivationIndicatorEntries());
			for (InactivationIndicatorEntry cii : ciis) {
				debug ("Removing duplicate: " + cii);
				report (task, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, cii);
				tsClient.deleteRefsetMember(cii.getId(), task.getBranchPath(), false);
				changesMade++;
			}
			for (Description d : c.getDescriptions()) {
				InactivationIndicatorEntry[] diis = getDuplicateInactivationIndicators(d, d.getInactivationIndicatorEntries());
				for (InactivationIndicatorEntry dii : diis) {
					debug ("Removing duplicate: " + dii);
					report (task, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, dii);
					tsClient.deleteRefsetMember(dii.getId(), task.getBranchPath(), false);
					changesMade ++;
				}
			}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() {
		//Work through all inactive concepts and check the inactivation indicator on all
		//active descriptions
		info ("Identifying concepts to process");
		List<Component> processMe = new ArrayList<Component>();
		for (Concept c : gl.getAllConcepts()) {
			/*if (c.getConceptId().equals("28979006")) {
				debug ("Check me");
			}*/
			if (!c.isActive()) {
				InactivationIndicatorEntry[] ciis = getDuplicateInactivationIndicators(c, c.getInactivationIndicatorEntries());
				if (ciis.length == 0) {
					for (Description d : c.getDescriptions()) {
						InactivationIndicatorEntry[] diis = getDuplicateInactivationIndicators(d, d.getInactivationIndicatorEntries());
						if (diis.length > 0) {
							processMe.add(c);
							break;
						}
					}
				} else {
					processMe.add(c);
				}
			}
		}
		info ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	private InactivationIndicatorEntry[] getDuplicateInactivationIndicators(Component c,
			List<InactivationIndicatorEntry> inactivationIndicatorEntries) {
		
		List<InactivationIndicatorEntry> duplicates = new ArrayList<>();
		List<InactivationIndicatorEntry> keepers = new ArrayList<>();
		
		for (InactivationIndicatorEntry thisEntry : inactivationIndicatorEntries) {
			//Check against every other entry
			for (InactivationIndicatorEntry thatEntry : inactivationIndicatorEntries) {
				//If we've already decided we're keeping this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || keepers.contains(thisEntry)) {
					continue;
				}
				if (thisEntry.getRefsetId().equals(thatEntry.getRefsetId()) &&
						thisEntry.getReferencedComponentId().equals(thatEntry.getReferencedComponentId()) &&
						thisEntry.getInactivationReasonId().equals(thatEntry.getInactivationReasonId())) {
					debug ("Found duplicates for " + c + ": " + thisEntry + " + " + thatEntry);
					//Delete the unpublished one
					if (thisEntry.getEffectiveTime() == null || thisEntry.getEffectiveTime().isEmpty()) {
						duplicates.add(thisEntry);
						keepers.add(thatEntry);
					} else if (thatEntry.getEffectiveTime() == null || thatEntry.getEffectiveTime().isEmpty()) {
						duplicates.add(thatEntry);
						keepers.add(thisEntry);
					} else {
						warn ("Both entries look published! " + thisEntry.getEffectiveTime());
					}
				}
			}
		}
		return duplicates.toArray(new InactivationIndicatorEntry[] {});
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		throw new NotImplementedException("This class self determines concepts to process");
	}

}
