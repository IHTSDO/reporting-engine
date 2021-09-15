package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.util.StringUtils;

/*
 * INFRA-2480 Finding concept and description inactivation indicators that are duplicated
 * and remove the unpublished version
 * INFRA-5156 Add ability to delete duplicated historic associations at the same time
 * Also we'll add/re-active any missing concept inactivation indicators on active descriptions
 * INFRA-5274 Also fix up multiple language reference set entries for the same description/dialect
*/
public class DuplicateLangInactAssocPlusCncFix extends BatchFix {
	
	protected DuplicateLangInactAssocPlusCncFix(final BatchFix clone) {
		super(clone);
	}

	public static void main(final String[] args) throws TermServerScriptException, IOException, InterruptedException {
		final DuplicateLangInactAssocPlusCncFix fix = new DuplicateLangInactAssocPlusCncFix(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.runStandAlone = false;  //We need to look up the project path for MS projects
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
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
	public void init(String[] args) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		super.init(args);
	}

	@Override
	public int doFix(final Task task, final Concept concept, final String info)
			throws TermServerScriptException, ValidationFailure {
		// We will not load the concept because the Browser endpoint does not populate
		// the full array of inactivation indicators
		int changesMade = 0;
		try {
			changesMade = fixDuplicates(task, concept, false);
		} catch (final TermServerScriptException e) {
			throw new TermServerScriptException("Failed to remove duplicate inactivation indicator on " + concept, e);
		}
		return changesMade;
	}

	private int fixDuplicates(final Task t, final Concept c, final boolean trialRun)
			throws TermServerScriptException {
		int changesMade = 0;
		
		/*if (c.getId().equals("840534001")) {
			debug ("here");
		}*/
		
		final RefsetMember[] ciis = getDuplicateRefsetMembers(c, c.getInactivationIndicatorEntries());
		for (final RefsetMember cii : ciis) {
			debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + cii);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, cii);
			if (!dryRun) {
				tsClient.deleteRefsetMember(cii.getId(), t.getBranchPath(), false);
			}
			changesMade++;
			reactivateRemainingMemberIfRequired(c, cii, c.getInactivationIndicatorEntries(), t);
			
		}
		
		final RefsetMember[] as = getDuplicateRefsetMembers(c, c.getAssociationEntries());
		for (final RefsetMember a : as) {
			debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + a);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, a);
			if (!dryRun) {
				tsClient.deleteRefsetMember(a.getId(), t.getBranchPath(), false);
			}
			changesMade++;
			reactivateRemainingMemberIfRequired(c, a, c.getAssociationEntries(), t);
		}
		
		//Do we need to load and save the concept?
		Concept loaded = null;
		if (!c.isActive() && hasMissingConceptInactiveIndicator(c)) {
			loaded = loadConcept(c, t.getBranchPath());
		}
		
		for (final Description d : c.getDescriptions()) {
			final RefsetMember[] ls = getDuplicateRefsetMembers(d, d.getLangRefsetEntries());
			if (true);
			for (final RefsetMember l : ls) {
				debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + l);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, l);
				if (!dryRun) {
					tsClient.deleteRefsetMember(l.getId(), t.getBranchPath(), false);
				}
				changesMade++;
				reactivateRemainingMemberIfRequired(c, l, d.getLangRefsetEntries(), t);
			}
			
			final RefsetMember[] diis = getDuplicateRefsetMembers(d, d.getInactivationIndicatorEntries());
			for (final RefsetMember dii : diis) {
				debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + dii);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, dii);
				if (!dryRun) {
					tsClient.deleteRefsetMember(dii.getId(), t.getBranchPath(), false);
				}
				changesMade++;
				reactivateRemainingMemberIfRequired(c, dii, d.getInactivationIndicatorEntries(), t);
			}
			
			if (!c.isActive() && d.isActive() && isMissingConceptInactiveIndicator(d)) {
				/*if (d.getId().equals("3902340014")) {
					debug("here");
				}*/
				Description dLoaded = loaded.getDescription(d.getDescriptionId());
				//We'll set the indicator directly on the description in the browser object
				//and let the TS work out if it needs to add a new refset member or reactive one
				if (dLoaded == null) {
					report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Local import expected description " + d.getDescriptionId() + " but was not found on " + t.getBranchPath(), d);
				} else {
					dLoaded.setInactivationIndicator(InactivationIndicator.CONCEPT_NON_CURRENT);
					report(t, c, Severity.LOW, ReportActionType.INACT_IND_ADDED, "ConceptNonCurrent", dLoaded);
					changesMade++;
				}
			}
		}
		
		if (loaded != null && changesMade > 0) {
			updateConcept(t, loaded, "");
		}
		return changesMade;
	}

	private int reactivateRemainingMemberIfRequired(Concept c, RefsetMember r,
			List<? extends RefsetMember> siblings, Task t) throws TermServerScriptException {
		//Was the member we deleted active?
		if (!r.isActive()) {
			return NO_CHANGES_MADE;
		}
		
		int changesMade = 0;
		//Find the duplicate again and reactivate 
		for (RefsetMember sibling : siblings) {
			if (!sibling.getId().equals(r.getId()) &&
				r.duplicates(sibling) &&
				!sibling.isActive()) {
				sibling.setActive(true);
				debug((dryRun?"Dry Run, not ":"") + "Reactivating published: " + sibling);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REACTIVATED, sibling);
				if (!dryRun) {
					tsClient.updateRefsetMember(sibling, t.getBranchPath());
				}
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() {
		// Work through all inactive concepts and check the inactivation indicators on
		// active descriptions
		info("Identifying concepts to process");
		setQuiet(true);
		final List<Component> processMe = new ArrayList<Component>();
		
		nextConcept:
		for (final Concept c : gl.getAllConcepts()) {
			
			/*if (c.getId().equals("840534001")) {
				debug("here");
			}*/
			
			for (String dialectRefset : ENGLISH_DIALECTS) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getLangRefsetEntries(ActiveState.ACTIVE, dialectRefset).size() > 1) {
						processMe.add(c);
						continue nextConcept;
					}
				}
			}
			
			for (Description d : c.getDescriptions()) {
				if (getDuplicateRefsetMembers(d, d.getInactivationIndicatorEntries()).length > 0) {
					processMe.add(c);
					continue nextConcept;
				}
			}
			 
			if (!c.isActive()) {
				final RefsetMember[] as = getDuplicateRefsetMembers(c, c.getAssociationEntries());
				if (as.length > 0) {
					processMe.add(c);
				} else {
					final RefsetMember[] ciis = getDuplicateRefsetMembers(c, c.getInactivationIndicatorEntries());
					if (ciis.length == 0) {
						for (final Description d : c.getDescriptions()) {
							final RefsetMember[] diis = getDuplicateRefsetMembers(d, d.getInactivationIndicatorEntries());
							if (diis.length > 0) {
								processMe.add(c);
								continue nextConcept;
							}
						}
					} else {
						processMe.add(c);
						continue nextConcept;
					}
				}
				
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (isMissingConceptInactiveIndicator(d)) {
						processMe.add(c);
						continue nextConcept;
					}
				}
			}
		}
		setQuiet(false);
		info("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}
	
	private boolean hasMissingConceptInactiveIndicator(Concept c) {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (isMissingConceptInactiveIndicator(d)) {
				return true;
			}
		}
		return false;
	}

	private boolean isMissingConceptInactiveIndicator(Description d) {
		boolean hasConceptInactiveIndicator = false;
		for (InactivationIndicatorEntry entry : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
			if (entry.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
				hasConceptInactiveIndicator = true;
				break;
			}
		}
		return !hasConceptInactiveIndicator;
	}

	private RefsetMember[] getDuplicateRefsetMembers(final Component c, final List<? extends RefsetMember> refsetMembers) {
		final List<RefsetMember> duplicates = new ArrayList<>();
		final List<RefsetMember> keepers = new ArrayList<>();

		for (final RefsetMember thisEntry : refsetMembers) {
			/*if (thisEntry.getId().equals("7816cc67-b074-4bb3-993d-ce8487e23e0a")) {
				debug("here");
			}*/
			// Check against every other entry
			for (final RefsetMember thatEntry : refsetMembers) {
				// If we've already decided we're keeping this entry or deleting this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || 
						keepers.contains(thisEntry) || keepers.contains(thatEntry) ||
						duplicates.contains(thisEntry) || duplicates.contains(thatEntry)) {
					continue;
				}
				if (thisEntry.duplicates(thatEntry)) {
					// Delete the unpublished one
					if (StringUtils.isEmpty(thisEntry.getEffectiveTime()) && StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
						if (thisEntry.getReleased() && !thatEntry.getReleased()) {
							duplicates.add(thatEntry);
						} else if (!thisEntry.getReleased() && thatEntry.getReleased()) {
							duplicates.add(thisEntry);
						} else if (!thisEntry.getReleased() && !thatEntry.getReleased()) {
							//Neither released.   Delete the inactive one, or randomly otherwise
							if (!thisEntry.isActive()) {
								duplicates.add(thisEntry);
							} else {
								duplicates.add(thatEntry);
							}
						}
					} else if (thisEntry.getEffectiveTime() == null || thisEntry.getEffectiveTime().isEmpty()) {
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
					
					if (duplicates.contains(thisEntry)) {
						debug("Found duplicates for " + c + ": " + thisEntry + " + " + thatEntry);
					}
				}
			}
		}
		return duplicates.toArray(new RefsetMember[] {});
	}

}
