package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;
import java.lang.IllegalArgumentException;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ArchiveManager;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.snapshot.SnapshotGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.springframework.web.client.RestClientResponseException;

/*
 * INFRA-2480 Finding concept and description inactivation indicators that are duplicated
 * and remove the unpublished version
 * INFRA-5156 Add ability to delete duplicated historic associations at the same time
 * Also we'll add/re-active any missing concept inactivation indicators on active descriptions
 * INFRA-5274 Also fix up multiple language reference set entries for the same description/dialect
 * ISRS-1257 Detect CNC indicators on descriptions that have been made inactive and remove
 * MSSP-1571 Detect refset members that apply to extension components but have been created in the core module
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DuplicateLangInactAssocPlusCncFixPlusModFix extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(DuplicateLangInactAssocPlusCncFixPlusModFix.class);

	String defaultModuleId = null;
	
	protected DuplicateLangInactAssocPlusCncFixPlusModFix(final BatchFix clone) {
		super(clone);
	}

	public static void main(final String[] args) throws TermServerScriptException, IOException, InterruptedException {
		final DuplicateLangInactAssocPlusCncFixPlusModFix fix = new DuplicateLangInactAssocPlusCncFixPlusModFix(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
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
		mgr.setEnsureSnapshotPlusDeltaLoad(true);
		SnapshotGenerator.setSkipSave(true); //No need to save to disk if we need a fresh copy every time. 
		//mgr.setRunIntegrityChecks(false);  //MSSP-1087
		super.init(args);
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		defaultModuleId = project.getMetadata().getDefaultModuleId();
		String[] columnHeadings = new String[]{
				"TaskId, JobName, SCTID, FSN, SemTag, Severity, Action, Before, After, , ",
				"SCTID, FSN, SemTag, Severity, Action, Details, Details, , "
		};

		String[] tabNames = new String[]{
				"Changes Made",
				"Decisions Taken"
		};
		postInit(tabNames, columnHeadings, false);
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

		List<DuplicatePair> duplicatePairs = getDuplicateRefsetMembers(c, c.getInactivationIndicatorEntries());
		for (DuplicatePair duplicatePair : duplicatePairs) {
			if (duplicatePair.isDeleting()) {
				LOGGER.debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + duplicatePair.remove);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, duplicatePair.remove);
				if (!dryRun) {
					try {
						tsClient.deleteRefsetMember(duplicatePair.remove.getId(), t.getBranchPath(), false);
					} catch (RestClientResponseException e) {
						report(t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Failed to delete refset member: " + e.getMessage());
						return NO_CHANGES_MADE;
					}
				}
				changesMade++;
				reactivateRemainingMemberIfRequired(c, duplicatePair.remove, c.getInactivationIndicatorEntries(), t);
			} else {
				for (RefsetMember modify : duplicatePair.modify) {
					RefsetMember original = c.getInactivationIndicatorEntry(modify.getId());
					report(t, c, Severity.MEDIUM, ReportActionType.INACT_IND_MODIFIED, "Was " + original, "Now " + modify);
					updateRefsetMember(t, modify, "");
					changesMade++;
				}
			}
		}
		
		duplicatePairs = getDuplicateRefsetMembers(c, c.getAssociationEntries());
		for (DuplicatePair duplicatePair : duplicatePairs) {
			if (duplicatePair.isDeleting()) {
				LOGGER.debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + duplicatePair.remove);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, duplicatePair.remove, "Kept: " + duplicatePair.keep);
				if (!dryRun) {
					tsClient.deleteRefsetMember(duplicatePair.remove.getId(), t.getBranchPath(), false);
				}
				changesMade++;
				reactivateRemainingMemberIfRequired(c, duplicatePair.remove, c.getAssociationEntries(), t);
			} else {
				for (RefsetMember modify : duplicatePair.modify) {
					RefsetMember original = c.getAssociationEntry(modify.getId());
					report(t, c, Severity.MEDIUM, ReportActionType.ASSOCIATION_CHANGED, original, modify);
					updateRefsetMember(t, modify, "");
					changesMade++;
				}
			}
		}
		
		//Do we need to load and save the concept?
		Concept loaded = null;
		if (!c.isActiveSafely() && hasMissingConceptInactiveIndicator(c)) {
			loaded = loadConcept(c, t.getBranchPath());
		}
		
		for (final Description d : c.getDescriptions()) {
			//Do we have active LangRefset members on inactive descriptions? Check scope on the LRSM, not the description
			//As we (eg Swiss) may have a description that is inactive in the International edition
			if (!d.isActiveSafely() && !d.getLangRefsetEntries(ActiveState.ACTIVE).isEmpty()) {
				ReportActionType action = ReportActionType.REFSET_MEMBER_INACTIVATED;
				for (LangRefsetEntry l : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
					if (inScope(l)) {
						if (!dryRun) {
							if (l.isReleasedSafely()) {
								l.setActive(false);
								tsClient.updateRefsetMember(l, t.getBranchPath());
							} else {
								tsClient.deleteRefsetMember(l.getId(), t.getBranchPath(), false);
							}
						}
						if (!l.isReleasedSafely()) {
							action = ReportActionType.REFSET_MEMBER_DELETED;
						}
						report(t, c, Severity.LOW, action, "Active LRSM removed on inactive description", l);
						changesMade++;
					}
				}
			}
			
			//Langrefset entries should be checked, regardless if the description is inScope or not
			duplicatePairs = getDuplicateRefsetMembers(d, d.getLangRefsetEntries());
			for (final DuplicatePair duplicatePair : duplicatePairs) {
				if (duplicatePair.isDeleting()) {
					LOGGER.debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + duplicatePair.remove);
					report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, duplicatePair.remove, "Kept: " + duplicatePair.keep);
					if (!dryRun) {
						tsClient.deleteRefsetMember(duplicatePair.remove.getId(), t.getBranchPath(), false);
					}
					//Remove this from the concept also so that we don't eg also modify its module id further down
					d.getLangRefsetEntries().remove(duplicatePair.remove);
					changesMade++;
					reactivateRemainingMemberIfRequired(c, duplicatePair.remove, d.getLangRefsetEntries(), t);
				} else {
					for (RefsetMember modify : duplicatePair.modify) {
						RefsetMember original = d.getLangRefsetEntry(modify.getId());
						report(t, c, Severity.MEDIUM, ReportActionType.LANG_REFSET_MODIFIED, original.toString(true), modify.toString(true));
						updateRefsetMember(t, modify, "");
						changesMade++;
					}
				}
			}
			
			duplicatePairs = getDuplicateRefsetMembers(d, d.getInactivationIndicatorEntries());
			for (final DuplicatePair duplicatePair : duplicatePairs) {
				if (duplicatePair.isDeleting()) {
					LOGGER.debug((dryRun?"Dry Run (so not) r":"R") + "emoving duplicate: " + duplicatePair.remove);
					ReportActionType action = ReportActionType.REFSET_MEMBER_DELETED;
					if (duplicatePair.remove.isReleasedSafely()) {
						action = ReportActionType.REFSET_MEMBER_INACTIVATED;
					}
					if (!dryRun) {
						if (duplicatePair.remove.isReleasedSafely()) {
							duplicatePair.remove.setActive(false);
							tsClient.updateRefsetMember(duplicatePair.remove, t.getBranchPath());
						} else {
							tsClient.deleteRefsetMember(duplicatePair.remove.getId(), t.getBranchPath(), false);
						}
					}
					report(t, c, Severity.LOW, action, duplicatePair.remove, "Kept: " + duplicatePair.keep);

					//Remove this from the concept also so that we don't eg also modify its module id further down
					d.getInactivationIndicatorEntries().remove(duplicatePair.remove);
					changesMade++;
					reactivateRemainingMemberIfRequired(c, duplicatePair.remove, d.getLangRefsetEntries(), t);
				}

				//We may instead or also want to modify one of the pairs
				if (duplicatePair.modify != null) {
					for (RefsetMember modify : duplicatePair.modify) {
						RefsetMember original = d.getInactivationIndicatorEntry(modify.getId());
						report(t, c, Severity.MEDIUM, ReportActionType.LANG_REFSET_MODIFIED, original.toString(true), modify.toString(true));
						updateRefsetMember(t, modify, "");
						//And we want to replace that ii on the description to avoid changing it further
						d.addInactivationIndicator((InactivationIndicatorEntry) modify);
						changesMade++;
					}
				}
			}
			
			if (!inScope(d)) {
				continue;
			}
			
			if (!c.isActiveSafely() && d.isActiveSafely() && isMissingConceptInactiveIndicator(d)) {
				if (loaded == null) {
					loaded = loadConcept(c, t.getBranchPath());
				}
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

			//If the concept is _active_ then we don't expect to see _any_ CNC indicators on the descriptions
			//ISRS-1257 OR if the description is inactive, then we don't want any CNC indicators!
			if ((c.isActiveSafely() || !d.isActiveSafely()) && hasConceptInactiveIndicator(d)) {
				for (InactivationIndicatorEntry entry : d.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
					if (entry.getInactivationReasonId().equals(SCTID_INACT_CONCEPT_NON_CURRENT)) {
						//Are we inactivating or deleting?
						if (entry.isReleasedSafely()) {
							entry.setActive(false);
							report(t, c, Severity.LOW, ReportActionType.INACT_IND_INACTIVATED, "ConceptNonCurrent", entry);
							if (!dryRun) {
								tsClient.updateRefsetMember(t.getBranchPath(), entry, false);
							}
						} else {
							report(t, c, Severity.LOW, ReportActionType.INACT_IND_DELETED, "ConceptNonCurrent", entry);
							if (!dryRun) {
								tsClient.deleteRefsetMember(entry.getId(), t.getBranchPath(), false);
							}
						}
						changesMade++;
					}
				}
			}
		}
		
		int directRefsetChanges = 0;
		for (RefsetMember rm : SnomedUtils.getAllRefsetMembers(c)) {
			if (!rm.isReleasedSafely() && defaultModuleId != null && SnomedUtils.isCore(rm)) {
				RefsetMember rmLoaded = loadRefsetMember(rm.getId(), project.getBranchPath());
				rmLoaded.setModuleId(defaultModuleId);
				String msg = "Module " + rm.getModuleId() + " -> " + rmLoaded.getModuleId();
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_MODIFIED, msg, rm);
				updateRefsetMember(t, rmLoaded, msg);
				directRefsetChanges++;
			}
		}
		
		if (loaded != null && changesMade > 0) {
			updateConcept(t, loaded, "");
		}
		return changesMade + directRefsetChanges;
	}

	private int reactivateRemainingMemberIfRequired(Concept c, RefsetMember r,
			List<? extends RefsetMember> siblings, Task t) throws TermServerScriptException {
		//Was the member we deleted active?
		if (!r.isActiveSafely()) {
			return NO_CHANGES_MADE;
		}
		
		int changesMade = 0;
		//Find the duplicate again and reactivate 
		for (RefsetMember sibling : siblings) {
			if (!sibling.getId().equals(r.getId()) &&
				r.duplicates(sibling) &&
				!sibling.isActiveSafely()) {
				sibling.setActive(true);
				LOGGER.debug((dryRun?"Dry Run, not ":"") + "Reactivating published: " + sibling);
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
		LOGGER.info("Identifying concepts to process");
		setQuiet(true);
		final List<Component> processMe = new ArrayList<Component>();
		
		nextConcept:
		//for (final Concept c : Collections.singleton(gl.getConcept("226760005"))) {
		for (final Concept c : gl.getAllConcepts()) {
 			boolean hasChanges = SnomedUtils.hasChanges(c);
			for (Description d : c.getDescriptions()) {

				//Too many of these in the international edition - discuss elsewhere
				//OK we'll do them if they've been touched in this authoring cycle
				if (project.getBranchPath().contains("SNOMEDCT-") || hasChanges) {
					//Switch to just process those 
					if (!c.isActiveSafely() && inScope(d) && d.isActiveSafely() && isMissingConceptInactiveIndicator(d)) {
						processMe.add(c);
						continue nextConcept;
					}
				
					if ((!d.isActiveSafely() && hasConceptInactiveIndicator(d))
					|| (c.isActiveSafely() && hasConceptInactiveIndicator(d, InactivationIndicator.CONCEPT_NON_CURRENT))) {
						if (inScope(d)) {
							processMe.add(c);
							continue nextConcept;
						}
					}
				}

				//Do we have active LangRefset members on inactive descriptions?
				if (!d.isActiveSafely() && !d.getLangRefsetEntries(ActiveState.ACTIVE).isEmpty()) {
					processMe.add(c);
					continue nextConcept;
				}
				
				if (!getDuplicateRefsetMembers(d, d.getLangRefsetEntries()).isEmpty()) {
					processMe.add(c);
					continue nextConcept;
				}
				
				if (!getDuplicateRefsetMembers(d, d.getInactivationIndicatorEntries()).isEmpty()) {
					//Check for all published and only one active - that's as good as it gets
					if (!asGoodAsItGets(d.getInactivationIndicatorEntries())) {
						processMe.add(c);
						continue nextConcept;
					}
				}

				//If we have multiple active inactivation indicators, address that
				if (d.getInactivationIndicatorEntries(ActiveState.ACTIVE).size() > 1) {
					processMe.add(c);
					continue nextConcept;
				}
			}
			 
			if (!c.isActiveSafely()) {
				if (!getDuplicateRefsetMembers(c, c.getAssociationEntries()).isEmpty()) {
					processMe.add(c);
					continue nextConcept;
				}
				
				if (!getDuplicateRefsetMembers(c, c.getInactivationIndicatorEntries()).isEmpty()) {
					processMe.add(c);
					continue nextConcept;	
				}
				
				//Do we have refset members newly created, but not in the default module?
				for (RefsetMember rm : SnomedUtils.getAllRefsetMembers(c)) {
					if (!rm.isReleasedSafely() && defaultModuleId != null && SnomedUtils.isCore(rm)) {
						processMe.add(c);
						continue nextConcept;	
					}
				}
			}
		}
		setQuiet(false);
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	private boolean asGoodAsItGets(List<? extends RefsetMember> refsetMembers) {
		//If we have one active and the rest are inactive, and all have been published,
		//then we can't improve on that situation
		boolean hasOneActive = false;
		for (RefsetMember rm : refsetMembers) {
			if (rm.isActiveSafely()) {
				if (hasOneActive) {
					return false;
				}
				hasOneActive = true;
			}
			if (!rm.isReleasedSafely()) {
				return false;
			}
		}
		return true;
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
	private boolean hasConceptInactiveIndicator(Description d, InactivationIndicator ii) {
		String inactivationReasonId = SnomedUtils.translateInactivationIndicator(ii);
		return d.getInactivationIndicatorEntries().stream()
				.anyMatch(i -> i.getInactivationReasonId().equals(inactivationReasonId));
	}


	private List<DuplicatePair> getDuplicateRefsetMembers(final Component c, final List<? extends RefsetMember> refsetMembers) throws TermServerScriptException {
		List<DuplicatePair> duplicatePairs = new ArrayList<>();
		for (final RefsetMember thisEntry : refsetMembers) {
			/*if (thisEntry.getId().equals("7816cc67-b074-4bb3-993d-ce8487e23e0a")) {
				LOGGER.debug("here");
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
					if (thisEntry.isReleasedSafely() && thatEntry.isReleasedSafely()) {
						RefsetMember intRM = hasModule(INTERNATIONAL_MODULES, true, thisEntry, thatEntry);
						RefsetMember extRM = hasModule(INTERNATIONAL_MODULES, false, thisEntry, thatEntry);
						
						if ((intRM != null && extRM != null)
							&& (intRM.isActiveSafely() && !extRM.isActiveSafely() && StringUtils.isEmpty(extRM.getEffectiveTime()))) {
							LOGGER.warn("Inactivated refset member in extension.  As good as it gets: {}", extRM);
						}
						
						// Only a problem historically if they're both active
						if (thisEntry.isActiveSafely() && thatEntry.isActiveSafely()) {
							LOGGER.warn("Both entries are released and active! {} + {}", thisEntry, thatEntry);
						}
						
						//That said, if one or both of them have a null effective time, then it LOOKS like we 
						//created something redundant in the last authoring cycle.
						if (StringUtils.isEmpty(thisEntry.getEffectiveTime()) || StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
							LOGGER.warn("Previously released entry(s) have lost effective time: {} + {}", thisEntry, thatEntry);
							//Have we modified a previously active refset member such that it's now a duplicate with a previously inactive one?
							//In this case we need to revert the active one back to its previous value, inactive it
							//and resurrect the previously inactive value instead
							RefsetMember previousThis = loadPreviousRefsetMember(thisEntry.getId());
							RefsetMember previousThat = loadPreviousRefsetMember(thatEntry.getId());

							if (previousThis == null || previousThat == null) {
								//It might be that This was published in International and That was published in some Extension, so
								//This might not exist on the previous extension branch if it was prior to the Extension upgrade, that's OK
								if (previousThis == null && thisEntry.getModuleId().equals(SCTID_CORE_MODULE)) {
									LOGGER.warn("No previous 'This' found, was it published in core prior to extension upgrade? " + thisEntry);
								} else if (previousThat == null && thatEntry.getModuleId().equals(SCTID_CORE_MODULE)) {
									LOGGER.warn("No previous 'That' found, was it published in core prior to extension upgrade? " + thatEntry);
								} else {
									throw new TermServerScriptException("Unable to load previous refset member for " + thisEntry + " or " + thatEntry);
								}
							}
							
							//If both have been released, and we've recently made one inactive, then that's as good as it gets.  Skip
							if (duplicationRecentlyResolved(thisEntry, thatEntry)) {
								LOGGER.warn("Previous duplication appears to have been resolved (recent inactivation) between " + thisEntry + " and " + thatEntry);
								continue;
							}
							
							//With reuse, it's possible for them both to be inactive also!
							if (!thisEntry.isActiveSafely() && !thatEntry.isActiveSafely()) {
								LOGGER.warn("Both entries are released, both inactive, but have been modified! " + thisEntry + " + " + thatEntry);
								//In this case it doesn't matter which one we revert.  Take 'this'
								duplicatePair = new DuplicatePair().modify(previousThis);
							} else {
								//First, check if BOTH members are active.   If one of them is International and the other is Extension
								//then we'll keep the international one and inactivate the extension copy.
								if (thisEntry.isActiveSafely() && thatEntry.isActiveSafely()) {
									extRM  = chooseExtension(thisEntry, thatEntry);
									if (extRM == null) {
										throw new IllegalArgumentException("Consider here, two active members in same module.  Pick one at random?");
									} else {
										extRM.setActive(false);
										duplicatePair = new DuplicatePair().modify(extRM);
										report(SECONDARY_REPORT, c, Severity.MEDIUM, ReportActionType.INFO, "International RM retained", intRM);
										report(SECONDARY_REPORT, c, Severity.MEDIUM, ReportActionType.INFO, "Extension RM inactivated", extRM);
									}
								} else {
									//We want to look for the previous entry that has the value which is on our current active member and keep that
									RefsetMember active = chooseActive(thisEntry, thatEntry, true);
									if (active == null) {
										//We already checked for both inactive, so we have two active members here that are duplicates
										//so we can revert either one of them to fix
										//But make sure the one we're inactivating is being inactivated
										previousThis.setActive(false);
										duplicatePair = new DuplicatePair().modify(previousThis);
										report(SECONDARY_REPORT, c, Severity.MEDIUM, ReportActionType.INFO, "Inactivating previous 'this'", previousThis);
									} else {
										String additionalFieldName = active.getOnlyAdditionalFieldName();
										String targetOrValue = active.getField(additionalFieldName);
										//Which of the previous entries used this target value? We'll keep that one
										RefsetMember previouslyMatching = choose(previousThis, previousThat, targetOrValue, additionalFieldName, true);
										if (previouslyMatching == null) {
											//If _neither_ refset member used this target value and they're now duplicate, then
											//the one that is currently inactive should be reset to its previous state
											RefsetMember inactive = chooseActive(thisEntry, thatEntry, false);
											RefsetMember revert = pickByID(inactive.getId(), previousThis, previousThat);
											revert.setActive(false);
											duplicatePair = new DuplicatePair().modify(revert);
										} else {
											RefsetMember inactivate = choose(previousThis, previousThat, targetOrValue, additionalFieldName, false);
											previouslyMatching.setActive(true);
											inactivate.setActive(false);
											duplicatePair = new DuplicatePair().modify(previouslyMatching, inactivate);
										}
									}
								}
							}
						}
					} else if (StringUtils.isEmpty(thisEntry.getEffectiveTime()) && StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
						// Delete the unpublished one
						if (thisEntry.isReleasedSafely() && !thatEntry.isReleasedSafely()) {
							duplicatePair = new DuplicatePair(thisEntry, thatEntry);
						} else if (!thisEntry.isReleasedSafely() && thatEntry.isReleasedSafely()) {
							duplicatePair = new DuplicatePair(thatEntry,thisEntry);
						} else if (!thisEntry.isReleasedSafely() && !thatEntry.isReleasedSafely()) {
							//Neither released.   Delete the inactive one, or randomly otherwise
							if (!thisEntry.isActiveSafely()) {
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
						if (thisEntry.isActiveSafely() && thatEntry.isActiveSafely()) {
							LOGGER.warn("Both entries look published and active! " + thisEntry.getEffectiveTime());
						}
					}
				}

				if (duplicatePair == null
						&& c instanceof Description
						&& thisEntry instanceof InactivationIndicatorEntry) {
					duplicatePair = resolveCNCInactivationIndicatorDuplicates(
							(InactivationIndicatorEntry)thisEntry,
							(InactivationIndicatorEntry)thatEntry);
				}

				if (duplicatePair != null) {
					//Don't log if it's likely we're not goign to improve the sitation
					if (!asGoodAsItGets(List.of(thisEntry, thatEntry))) {
						LOGGER.debug(duplicatePair.toString());
					}
					duplicatePairs.add(duplicatePair);
				}
			}
		}
		return duplicatePairs;
	}

	private DuplicatePair resolveCNCInactivationIndicatorDuplicates(InactivationIndicatorEntry thisEntry, InactivationIndicatorEntry thatEntry) {
		//Are both published and only one active?  That's not a duplicate, it's "as good as it gets"
		if (asGoodAsItGets(List.of(thisEntry, thatEntry))) {
			return null;
		}
		//Have we inactivated a description with an inactivation indicator?   Remove the new II if so,
		//and swap its value onto the CNC indicator which is no longer required.
		List<InactivationIndicatorEntry> ii = List.of(thisEntry, thatEntry);
		InactivationIndicatorEntry cnc = pickByValue(ii, SCTID_INACT_CONCEPT_NON_CURRENT, true);
		InactivationIndicatorEntry newII = pickByValue(ii, SCTID_INACT_CONCEPT_NON_CURRENT, false);
		if (cnc == null || newII == null) {
			return null;
		}
		//We'll keep the CNC indicator and inactivate the new one
		DuplicatePair duplicatePair = new DuplicatePair(cnc, newII);
		InactivationIndicatorEntry modified = cnc.clone(true);
		modified.setInactivationReasonId(newII.getInactivationReasonId());
		duplicatePair.modify(modified);
		return duplicatePair;
	}

	private InactivationIndicatorEntry pickByValue(List<InactivationIndicatorEntry> ii, String value, boolean match) {
		List<InactivationIndicatorEntry> potentialPicks = new ArrayList<>();
		for (InactivationIndicatorEntry entry : ii) {
			if (match == entry.getInactivationReasonId().equals(value)) {
				potentialPicks.add(entry);
			}
		}
		if (potentialPicks.size() == 0) {
			return null;
		} else if (potentialPicks.size() == 1) {
			return potentialPicks.get(0);
		} else {
			//Don't worry if they're both released and only one is active
			if(potentialPicks.size() == 2
					&& potentialPicks.get(0).isReleasedSafely() && potentialPicks.get(1).isReleasedSafely()
					&& 	(potentialPicks.get(0).isActiveSafely() || potentialPicks.get(1).isActiveSafely())
					&& 	(!potentialPicks.get(0).isActiveSafely() || !potentialPicks.get(1).isActiveSafely())) {
				return null;
			}
			LOGGER.warn("Multiple active inactivation indicators " +( match?"matching":"not matching ") + " value " + value + " found on " + ii);
		}
		return null;
	}

	private boolean duplicationRecentlyResolved(RefsetMember thisEntry, RefsetMember thatEntry) throws TermServerScriptException {
		if (!thisEntry.isReleasedSafely() || !thatEntry.isReleasedSafely()) {
			throw new TermServerScriptException("Check code, expected released members");
		}
		//Is 'this' unchanged and 'that' recently inactivated?
		if (thisEntry.isActiveSafely()  && 
				!StringUtils.isEmpty(thisEntry.getEffectiveTime()) &&
				!thatEntry.isActiveSafely() && 
				StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
			return true;
		}
		//Or the other way around
		return (!thisEntry.isActiveSafely()  &&
				StringUtils.isEmpty(thisEntry.getEffectiveTime()) &&
				thatEntry.isActiveSafely() && 
				!StringUtils.isEmpty(thatEntry.getEffectiveTime()));
	}

	private RefsetMember pickByID(String id, RefsetMember... refsetMembers) {
		for (RefsetMember rm : refsetMembers) {
			if (rm.getId().equals(id)) {
				return rm;
			}
		}
		throw new IllegalStateException(id + " was not found in provided set " + refsetMembers);
	}

	private RefsetMember choose(RefsetMember lhs, RefsetMember rhs, String targetOrValue,
			String additionalFieldName, boolean matching) {
		boolean lhsMatches = lhs.getField(additionalFieldName).equals(targetOrValue);
		boolean rhsMatches = rhs.getField(additionalFieldName).equals(targetOrValue);
		if (lhsMatches && rhsMatches) {
			//throw new IllegalStateException("Both refset members featured target or value " + lhs + " vs " + rhs);
			//In this case, we should pick the one that was active in the the last release
			if (lhs.isActiveSafely() && !rhs.isActiveSafely()) {
				return matching?lhs:rhs;
			} else if (rhs.isActiveSafely() && !lhs.isActiveSafely()) {
				return matching?rhs:lhs;
			} else {
				throw new IllegalStateException("Both refset members featured target or value and have same state: " + lhs + " vs " + rhs);
			}
		}
		if (!lhsMatches && !rhsMatches) {
			LOGGER.warn("Neither refset members featured target or value " + lhs + " vs " + rhs);
			return null;
		}
		if (matching) {
			return lhsMatches ? lhs : rhs;
		}
		return lhsMatches ? rhs : lhs;
	}

	private RefsetMember chooseActive(RefsetMember thisEntry, RefsetMember thatEntry, boolean active) {
		if ((thisEntry.isActiveSafely() && thatEntry.isActiveSafely()) ||
			(!thisEntry.isActiveSafely() && !thatEntry.isActiveSafely())) {
			LOGGER.warn("Unable to find one active member of pair {} vs {}", thisEntry, thatEntry);
			return null;
		}
		
		if (active) {
			return thisEntry.isActiveSafely() ? thisEntry : thatEntry;
		} else {
			return thisEntry.isActiveSafely() ? thatEntry : thisEntry;
		}
	}

	private RefsetMember chooseInternational(RefsetMember thisEntry, RefsetMember thatEntry) {
		if ((isInternational(thisEntry) && isInternational(thatEntry)) ||
				(!isInternational(thisEntry) && !isInternational(thatEntry))) {
			LOGGER.warn("Unable to find single International member of pair {} vs {}", thisEntry, thatEntry);
			return null;
		}
		return isInternational(thisEntry) ? thisEntry : thatEntry;
	}
	
	private RefsetMember chooseExtension(RefsetMember thisEntry, RefsetMember thatEntry) {
		if ((isInternational(thisEntry) && isInternational(thatEntry)) ||
				(!isInternational(thisEntry) && !isInternational(thatEntry))) {
			LOGGER.warn("Unable to find single Extension member of pair {} vs {}", thisEntry, thatEntry);
			return null;
		}
		return isInternational(thisEntry) ? thatEntry : thisEntry;
	}

	private boolean isInternational(RefsetMember rm) {
		return SnomedUtils.hasModule(rm, INTERNATIONAL_MODULES);
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
			if (pair.isDeleting()) {
				if (pair.keep.equals(rm) || pair.remove.equals(rm)) {
					return true;
				}
			} else {
				//Not sure why this fails to find our rm, it does implement hashCode
				//Perhaps because its array based?
				//return pair.modify.contains(rm);
				for (RefsetMember modifyItem : pair.modify) {
					if (modifyItem.getId().equals(rm.getId())) {
						return true;
					}
				}
			}
		}
		return false;
	}

	class DuplicatePair {
		RefsetMember keep;
		RefsetMember remove;
		Set<RefsetMember> modify;
		
		DuplicatePair (RefsetMember keep, RefsetMember delete) {
			this.keep = keep;
			this.remove = delete;
		}
		
		DuplicatePair () {
		}
		
		public DuplicatePair modify(RefsetMember... modify) {
			this.modify = new HashSet<RefsetMember>(Arrays.asList(modify));
			return this;
		}
		
		public boolean isDeleting() {
			return remove != null;
		}
		
		public String toString() {
			if (isDeleting()) {
				return "Delete: " + remove + " vs Keep: " + keep;
			}
			return "Modify: " + modify.toString();
		}
	}
}
