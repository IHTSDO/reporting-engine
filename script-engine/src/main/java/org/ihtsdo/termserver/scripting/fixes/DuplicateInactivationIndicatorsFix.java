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
public class DuplicateInactivationIndicatorsFix extends BatchFix {
	
	protected DuplicateInactivationIndicatorsFix(final BatchFix clone) {
		super(clone);
	}

	public static void main(final String[] args) throws TermServerScriptException, IOException, InterruptedException {
		final DuplicateInactivationIndicatorsFix fix = new DuplicateInactivationIndicatorsFix(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			// Recover the current project state from TS (or local cached archive) to allow
			// quick searching of all concepts
			fix.loadProjectSnapshot(false); // Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(final Task task, final Concept concept, final String info)
			throws TermServerScriptException, ValidationFailure {
		// We will not load the concept because the Browser endpoint does not populate
		// the full array of inactivation indicators
		int changesMade = 0;
		try {
			changesMade = fixDuplicateInactivationIndicators(task, concept, false);
		} catch (final TermServerScriptException e) {
			throw new TermServerScriptException("Failed to remove duplicate inactivation indicator on " + concept, e);
		}
		return changesMade;
	}

	private int fixDuplicateInactivationIndicators(final Task task, final Concept c, final boolean trialRun)
			throws TermServerScriptException {
		int changesMade = 0;
		final InactivationIndicatorEntry[] ciis = getDuplicateInactivationIndicators(c,
				c.getInactivationIndicatorEntries());
		for (final InactivationIndicatorEntry cii : ciis) {
			debug("Removing duplicate: " + cii);
			report(task, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, cii);
			tsClient.deleteRefsetMember(cii.getId(), task.getBranchPath(), false);
			changesMade++;
		}
		for (final Description d : c.getDescriptions()) {
			final InactivationIndicatorEntry[] diis = getDuplicateInactivationIndicators(d,
					d.getInactivationIndicatorEntries());
			for (final InactivationIndicatorEntry dii : diis) {
				debug("Removing duplicate: " + dii);
				report(task, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, dii);
				tsClient.deleteRefsetMember(dii.getId(), task.getBranchPath(), false);
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() {
		// Work through all inactive concepts and check the inactivation indicator on
		// all
		// active descriptions
		info("Identifying concepts to process");
		final List<Component> processMe = new ArrayList<Component>();
		for (final Concept c : gl.getAllConcepts()) {
			/*
			 * if (c.getConceptId().equals("28979006")) { debug ("Check me"); }
			 */
			if (!c.isActive()) {
				final InactivationIndicatorEntry[] ciis = getDuplicateInactivationIndicators(c,
						c.getInactivationIndicatorEntries());
				if (ciis.length == 0) {
					for (final Description d : c.getDescriptions()) {
						final InactivationIndicatorEntry[] diis = getDuplicateInactivationIndicators(d,
								d.getInactivationIndicatorEntries());
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
		info("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	private InactivationIndicatorEntry[] getDuplicateInactivationIndicators(final Component c,
			final List<InactivationIndicatorEntry> inactivationIndicatorEntries) {

		final List<InactivationIndicatorEntry> duplicates = new ArrayList<>();
		final List<InactivationIndicatorEntry> keepers = new ArrayList<>();

		for (final InactivationIndicatorEntry thisEntry : inactivationIndicatorEntries) {
			// Check against every other entry
			for (final InactivationIndicatorEntry thatEntry : inactivationIndicatorEntries) {
				// If we've already decided we're keeping this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || keepers.contains(thisEntry)) {
					continue;
				}
				if (thisEntry.getRefsetId().equals(thatEntry.getRefsetId())
						&& thisEntry.getReferencedComponentId().equals(thatEntry.getReferencedComponentId())
						&& thisEntry.getInactivationReasonId().equals(thatEntry.getInactivationReasonId())) {
					debug("Found duplicates for " + c + ": " + thisEntry + " + " + thatEntry);
					// Delete the unpublished one
					if (thisEntry.getEffectiveTime() == null || thisEntry.getEffectiveTime().isEmpty()) {
						duplicates.add(thisEntry);
						keepers.add(thatEntry);
					} else if (thatEntry.getEffectiveTime() == null || thatEntry.getEffectiveTime().isEmpty()) {
						duplicates.add(thatEntry);
						keepers.add(thisEntry);
					} else {
						// Only a problem historically if they're both active
						if (thisEntry.isActive() && thatEntry.isActive()) {
							warn("Both entries look published! " + thisEntry.getEffectiveTime());
						}
					}
				}
			}
		}
		return duplicates.toArray(new InactivationIndicatorEntry[] {});
	}

	@Override
	protected List<Component> loadLine(final String[] lineItems)
			throws TermServerScriptException {
		throw new NotImplementedException("This class self determines concepts to process");
	}

}
