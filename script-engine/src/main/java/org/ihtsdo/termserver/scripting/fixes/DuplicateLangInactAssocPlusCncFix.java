package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.amazonaws.services.ssooidc.model.InvalidScopeException;

/*
 * INFRA-2480 Finding concept and description inactivation indicators that are duplicated
 * and remove the unpublished version
 * INFRA-5156 Add ability to delete duplicated historic associations at the same time
 * Also we'll add/re-active any missing concept inactivation indicators on active descriptions
 * INFRA-5274 Also fix up multiple language reference set entries for the same description/dialect
 * ISRS-1257 Detect CNC indicators on descriptions that have been made inactive and remove
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
			fix.getArchiveManager().setRunIntegrityChecks(false);
			fix.init(args);
			fix.loadProjectSnapshot(false); // Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	@Override
	public void init(String[] args) throws TermServerScriptException {
		ArchiveManager mgr = getArchiveManager();
		mgr.setPopulateReleasedFlag(true);
		//mgr.setRunIntegrityChecks(false);  //MSSP-1087
		super.init(args);
	}

	@Override
	public int doFix(final Task task, final Concept concept, final String info)
			throws TermServerScriptException, ValidationFailure {
		// We will not load the concept because the Browser endpoint does not populate
		// the full array of inactivation indicators
		int changesMade = 0;
		try {
			changesMade = fixIssues(task, concept, false);
		} catch (final TermServerScriptException e) {
			throw new TermServerScriptException("Failed to remove duplicate inactivation indicator on " + concept, e);
		}
		return changesMade;
	}

	private int fixIssues(final Task t, final Concept c, final boolean trialRun)
			throws TermServerScriptException {
		int changesMade = 0;
		
		/*if (c.getId().equals("840534001")) {
			debug ("here");
		}*/
		
		List<DuplicatePair> duplicatePairs = getDuplicateRefsetMembers(c, c.getInactivationIndicatorEntries());
		for (DuplicatePair duplicatePair : duplicatePairs) {
			debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + duplicatePair.delete);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, duplicatePair.delete);
			if (!dryRun) {
				tsClient.deleteRefsetMember(duplicatePair.delete.getId(), t.getBranchPath(), false);
			}
			changesMade++;
			reactivateRemainingMemberIfRequired(c, duplicatePair.delete, c.getInactivationIndicatorEntries(), t);
			
		}
		
		duplicatePairs = getDuplicateRefsetMembers(c, c.getAssociationEntries());
		for (DuplicatePair duplicatePair : duplicatePairs) {
			debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + duplicatePair.delete);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, duplicatePair.delete, "Kept: " + duplicatePair.keep);
			if (!dryRun) {
				tsClient.deleteRefsetMember(duplicatePair.delete.getId(), t.getBranchPath(), false);
			}
			changesMade++;
			reactivateRemainingMemberIfRequired(c, duplicatePair.delete, c.getAssociationEntries(), t);
		}
		
		//Do we need to load and save the concept?
		Concept loaded = null;
		if (!c.isActive() && hasMissingConceptInactiveIndicator(c)) {
			loaded = loadConcept(c, t.getBranchPath());
		}
		
		for (final Description d : c.getDescriptions()) {
			if (!inScope(d)) {
				continue;
			}
			duplicatePairs = getDuplicateRefsetMembers(d, d.getLangRefsetEntries());
			for (final DuplicatePair duplicatePair : duplicatePairs) {
				debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + duplicatePair.delete);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, duplicatePair.delete, "Kept: " + duplicatePair.keep);
				if (!dryRun) {
					tsClient.deleteRefsetMember(duplicatePair.delete.getId(), t.getBranchPath(), false);
				}
				changesMade++;
				reactivateRemainingMemberIfRequired(c, duplicatePair.delete, d.getLangRefsetEntries(), t);
			}
			
			duplicatePairs = getDuplicateRefsetMembers(d, d.getInactivationIndicatorEntries());
			for (final DuplicatePair duplicatePair : duplicatePairs) {
				debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + duplicatePair.delete);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, duplicatePair.delete, "Kept: " + duplicatePair.keep);
				if (!dryRun) {
					tsClient.deleteRefsetMember(duplicatePair.delete.getId(), t.getBranchPath(), false);
				}
				changesMade++;
				reactivateRemainingMemberIfRequired(c, duplicatePair.delete, d.getInactivationIndicatorEntries(), t);
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
			
			//ISRS-1257 However if the description is inactive, then we don't want any CNC indicators!
			if (!d.isActive() && hasConceptInactiveIndicator(d)) {
				for (InactivationIndicatorEntry entry : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					if (entry.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
						//Are we inactivating or deleting?
						if (entry.isReleased()) {
							entry.setActive(false);
							report(t, c, Severity.LOW, ReportActionType.INACT_IND_INACTIVATED, entry);
							if (!dryRun) {
								tsClient.updateRefsetMember(t.getBranchPath(), entry, false);
							}
						} else {
							report(t, c, Severity.LOW, ReportActionType.INACT_IND_DELETED, entry);
							if (!dryRun) {
								tsClient.deleteRefsetMember(entry.getId(), t.getBranchPath(), false);
							}
						}
						changesMade++;
					}
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
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		// Work through all inactive concepts and check the inactivation indicators on
		// active descriptions
		info("Identifying concepts to process");
		setQuiet(true);
		final List<Component> processMe = new ArrayList<Component>();
		
		nextConcept:
		//for (final Concept c : Collections.singleton(gl.getConcept("50968003"))) {	
		for (final Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				/*if (d.getId().equals("1197486005")) {
						debug("here");
				}*/
				
				//Too many of these in the international edition - discuss elsewhere
				if (project.getBranchPath().contains("SNOMEDCT-")) {
					if (!c.isActive() && d.isActive() && isMissingConceptInactiveIndicator(d)) {
						debug("Missing CNC CII: " + d);
						processMe.add(c);
						continue nextConcept;
					}
				}
				
				if (!d.isActive() && hasConceptInactiveIndicator(d)) {
					if (inScope(d)) {
						processMe.add(c);
						continue nextConcept;
					}
				}
				
				if (getDuplicateRefsetMembers(d, d.getLangRefsetEntries()).size() > 0) {
					processMe.add(c);
					continue nextConcept;
				}
				
				if (getDuplicateRefsetMembers(d, d.getInactivationIndicatorEntries()).size() > 0) {
					processMe.add(c);
					continue nextConcept;
				}
			}
			 
			if (!c.isActive()) {
				if (getDuplicateRefsetMembers(c, c.getAssociationEntries()).size() > 0) {
					processMe.add(c);
					continue nextConcept;
				}
				
				if (getDuplicateRefsetMembers(c, c.getInactivationIndicatorEntries()).size() > 0) {
					processMe.add(c);
					continue nextConcept;	
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
	
	private boolean hasConceptInactiveIndicator(Description d) {
		return !isMissingConceptInactiveIndicator(d);
	}

	private List<DuplicatePair> getDuplicateRefsetMembers(final Component c, final List<? extends RefsetMember> refsetMembers) {
		List<DuplicatePair> duplicatePairs = new ArrayList<>();
		for (final RefsetMember thisEntry : refsetMembers) {
			/*if (thisEntry.getId().equals("7816cc67-b074-4bb3-993d-ce8487e23e0a")) {
				debug("here");
			}*/
			// Check against every other entry
			for (final RefsetMember thatEntry : refsetMembers) {
				DuplicatePair duplicatePair = null;
				// If we've already decided we're keeping this entry or deleting this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || 
						mentioned(duplicatePairs, thisEntry) ||
						mentioned(duplicatePairs, thatEntry)) {
					continue;
				}
				if (thisEntry.duplicates(thatEntry)) {
					//Are they both published?  If INT is active and EXT is inactive with no effective time then that's as good as we'll get
					if (thisEntry.isReleased() && thatEntry.isReleased()) {
						RefsetMember intRM = hasModule(INTERNATIONAL_MODULES, true, thisEntry, thatEntry);
						RefsetMember extRM = hasModule(INTERNATIONAL_MODULES, false, thisEntry, thatEntry);
						
						if (intRM != null && extRM != null) {
							if (intRM.isActive() && !extRM.isActive() && StringUtils.isEmpty(extRM.getEffectiveTime())) {
								warn("Inactivated refsetmember in extension.  As good as it gets: " + extRM);
							}
						}
						
						// Only a problem historically if they're both active
						if (thisEntry.isActive() && thatEntry.isActive()) {
							warn("Both entries are released and active! " + thisEntry + " + " + thatEntry);
						}
						
						//That said, if they both have a null effective time, then it LOOKS like we 
						//created something redudnant in the last authoring cycle.
						if (StringUtils.isEmpty(thisEntry.getEffectiveTime()) && StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
							warn("Previously released entries have lost their effective time: " + thisEntry + " + " + thatEntry);
						}
					} else if (StringUtils.isEmpty(thisEntry.getEffectiveTime()) && StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
						// Delete the unpublished one
						if (thisEntry.isReleased() && !thatEntry.isReleased()) {
							duplicatePair = new DuplicatePair(thisEntry, thatEntry);
						} else if (!thisEntry.isReleased() && thatEntry.isReleased()) {
							duplicatePair = new DuplicatePair(thatEntry,thisEntry);
						} else if (!thisEntry.isReleased() && !thatEntry.isReleased()) {
							//Neither released.   Delete the inactive one, or randomly otherwise
							if (!thisEntry.isActive()) {
								duplicatePair = new DuplicatePair(thatEntry,thisEntry);
							} else {
								duplicatePair = new DuplicatePair(thisEntry, thatEntry);
							}
						}
					} else if (thisEntry.getEffectiveTime() == null || thisEntry.getEffectiveTime().isEmpty()) {
						duplicatePair = new DuplicatePair(thatEntry,thisEntry);
					} else if (thatEntry.getEffectiveTime() == null || thatEntry.getEffectiveTime().isEmpty()) {
						duplicatePair = new DuplicatePair(thisEntry, thatEntry);
					} else {
						// Only a problem historically if they're both active
						if (thisEntry.isActive() && thatEntry.isActive()) {
							warn("Both entries look published and active! " + thisEntry.getEffectiveTime());
						}
					}
				}
				if (duplicatePair != null) {
					debug(duplicatePair);
					duplicatePairs.add(duplicatePair);
				}
			}
		}
		return duplicatePairs;
	}

	private RefsetMember hasModule(String[] targetModules, boolean matchLogic, RefsetMember... refsetMembers) {
		for (RefsetMember rm : refsetMembers) {
			if (matchLogic && SnomedUtils.hasModule(rm, targetModules)) {
				return rm;
			} else if (!matchLogic && SnomedUtils.hasNotModule(rm, targetModules)) {
				return rm;
			}
		}
		return null;
	}

	private boolean mentioned(List<DuplicatePair> duplicatePairs, RefsetMember rm) {
		for (DuplicatePair pair : duplicatePairs) {
			if (pair.keep.equals(rm) || pair.delete.equals(rm)) {
				return true;
			}
		}
		return false;
	}

	class DuplicatePair {
		RefsetMember keep;
		RefsetMember delete;
		DuplicatePair (RefsetMember keep, RefsetMember delete) {
			this.keep = keep;
			this.delete = delete;
		}
		
		public String toString() {
			return "Delete: " + delete + " vs Keep: " + keep;
		}
	}
}
