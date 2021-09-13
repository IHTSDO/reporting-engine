package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.NotImplementedException;
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
		
		final List<LangRefsetEntry> ls = getMultipleLangRefsetEntries(c, t);
		for (final LangRefsetEntry l : ls) {
			debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + l);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, l);
			changesMade++;
			if (!dryRun) {
				tsClient.deleteRefsetMember(l.getId(), t.getBranchPath(), false);
			}
			changesMade++;
		}
		
		final InactivationIndicatorEntry[] ciis = getDuplicateInactivationIndicators(c, c.getInactivationIndicatorEntries(), t);
		for (final InactivationIndicatorEntry cii : ciis) {
			debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + cii);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, cii);
			if (!dryRun) {
				tsClient.deleteRefsetMember(cii.getId(), t.getBranchPath(), false);
			}
			changesMade++;
		}
		
		final AssociationEntry[] as = getDuplicateAssociations(c, c.getAssociationEntries(), t);
		for (final AssociationEntry a : as) {
			debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + a);
			report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, a);
			if (!dryRun) {
				tsClient.deleteRefsetMember(a.getId(), t.getBranchPath(), false);
			}
			changesMade++;
		}
		
		//Do we need to load and save the concept?
		Concept loaded = null;
		if (!c.isActive() && hasMissingConceptInactiveIndicator(c)) {
			loaded = loadConcept(c, t.getBranchPath());
		}
		
		for (final Description d : c.getDescriptions()) {
			final InactivationIndicatorEntry[] diis = getDuplicateInactivationIndicators(d, d.getInactivationIndicatorEntries(), t);
			for (final InactivationIndicatorEntry dii : diis) {
				debug((dryRun?"Dry Run, not ":"") + "Removing duplicate: " + dii);
				report(t, c, Severity.LOW, ReportActionType.REFSET_MEMBER_REMOVED, dii);
				if (!dryRun) {
					tsClient.deleteRefsetMember(dii.getId(), t.getBranchPath(), false);
				}
				changesMade++;
			}
			
			if (!c.isActive() && d.isActive() && isMissingConceptInactiveIndicator(d)) {
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

	@Override
	protected List<Component> identifyComponentsToProcess() {
		// Work through all inactive concepts and check the inactivation indicators on
		// active descriptions
		info("Identifying concepts to process");
		final List<Component> processMe = new ArrayList<Component>();
		
		nextConcept:
		for (final Concept c : gl.getAllConcepts()) {
			for (String dialectRefset : ENGLISH_DIALECTS) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (d.getLangRefsetEntries(ActiveState.ACTIVE, dialectRefset).size() > 1) {
						processMe.add(c);
						continue nextConcept;
					}
				}
			}
			 
			if (!c.isActive()) {
				final AssociationEntry[] as = getDuplicateAssociations(c, c.getAssociationEntries(), null);
				if (as.length > 0) {
					processMe.add(c);
				} else {
					final InactivationIndicatorEntry[] ciis = getDuplicateInactivationIndicators(c, c.getInactivationIndicatorEntries(), null);
					if (ciis.length == 0) {
						for (final Description d : c.getDescriptions()) {
							final InactivationIndicatorEntry[] diis = getDuplicateInactivationIndicators(d, d.getInactivationIndicatorEntries(), null);
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

	private InactivationIndicatorEntry[] getDuplicateInactivationIndicators(final Component c,
			final List<InactivationIndicatorEntry> inactivationIndicatorEntries, Task t) {

		final List<InactivationIndicatorEntry> duplicates = new ArrayList<>();
		final List<InactivationIndicatorEntry> keepers = new ArrayList<>();

		for (final InactivationIndicatorEntry thisEntry : inactivationIndicatorEntries) {
			// Check against every other entry
			for (final InactivationIndicatorEntry thatEntry : inactivationIndicatorEntries) {
				// If we've already decided we're keeping this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || keepers.contains(thisEntry) || duplicates.contains(thisEntry)) {
					continue;
				}
				if (thisEntry.getRefsetId().equals(thatEntry.getRefsetId())
						&& thisEntry.getReferencedComponentId().equals(thatEntry.getReferencedComponentId())
						&& thisEntry.getInactivationReasonId().equals(thatEntry.getInactivationReasonId())) {
					debug("Found duplicates for " + c + ": " + thisEntry + " + " + thatEntry);
					// Delete the unpublished one.   If they're both unpublished, get that refset entry 
					// directly to check the published flag.   Don't worry if we're just finding the concept though - t == null
					if (t != null && StringUtils.isEmpty(thisEntry.getEffectiveTime()) && StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
						warn("Both entries look modified, checking TS for published status of " + thisEntry);
						RefsetMember r = tsClient.getRefsetMember(thisEntry.getId(), getBranchPath(t));
						warn("Result: Is " + (r.getReleased()?"":"not ") + "published.");
						if (r.getReleased()) {
							duplicates.add(thatEntry);
							keepers.add(thisEntry);
						} else {
							duplicates.add(thisEntry);
							keepers.add(thatEntry);
						}
					} else if (StringUtils.isEmpty(thisEntry.getEffectiveTime())) {
						duplicates.add(thisEntry);
						keepers.add(thatEntry);
					} else if (StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
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
	
	
	/*
	 * This is less about duplicates as it is about having more than one active entry for a given
	 * description in a given dialect, so the values may be different
	 */
	private List<LangRefsetEntry> getMultipleLangRefsetEntries(Concept c, Task t) throws TermServerScriptException {
		List<LangRefsetEntry> forDeletion = new ArrayList<>();
		for (String dialectRefset : ENGLISH_DIALECTS) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				LangRefsetEntry keep = null;
				for (LangRefsetEntry thisEntry : d.getLangRefsetEntries(ActiveState.ACTIVE, dialectRefset)) {
					if (keep == null) {
						keep = thisEntry;
					} else {
						if (!keep.getAcceptabilityId().equals(thisEntry.getAcceptabilityId())) {
							report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "LangRefsetEntries have conflicting acceptability", d, keep, thisEntry);
						}
						debug("Found duplicates for " + c + ": " + thisEntry + " + " + keep);
						// Delete the unpublished one.   If they're both unpublished, get that refset entry 
						// directly to check the published flag.
						if (StringUtils.isEmpty(thisEntry.getEffectiveTime()) && StringUtils.isEmpty(keep.getEffectiveTime())) {
							warn("Both entries look modified, checking TS for published status of " + thisEntry);
							RefsetMember r = tsClient.getRefsetMember(thisEntry.getId(), getBranchPath(t));
							warn("Result: Is " + (r.getReleased()?"":"not ") + "published.");
							if (r.getReleased()) {
								forDeletion.add(keep);
								keep = thisEntry;
							} else {
								forDeletion.add(thisEntry);
							}
						} else if (StringUtils.isEmpty(thisEntry.getEffectiveTime())) {
							forDeletion.add(thisEntry);
						} else if (StringUtils.isEmpty(keep.getEffectiveTime())) {
							forDeletion.add(keep);
							keep = thisEntry;
						} else {
							// Only a problem historically if they're both active
							if (thisEntry.isActive() && keep.isActive()) {
								warn("Both entries look published! " + thisEntry.getEffectiveTime());
							}
						}
					}
				}
			}
		}
		return forDeletion; 
	}
	
	private String getBranchPath(Task t) {
		//If we're in dry run, we need to use the project path rather than the task
		if (dryRun) {
			return t.getBranchPath().substring(0, t.getBranchPath().lastIndexOf('/'));
		}
		return t.getBranchPath();
	}

	//TODO Create a common interface for reference set entries and remove this method.
	private AssociationEntry[] getDuplicateAssociations(final Component c, final List<AssociationEntry> AssociationEntries, Task t) {
		final List<AssociationEntry> duplicates = new ArrayList<>();
		final List<AssociationEntry> keepers = new ArrayList<>();

		for (final AssociationEntry thisEntry : AssociationEntries) {
			// Check against every other entry
			for (final AssociationEntry thatEntry : AssociationEntries) {
				// If we've already decided we're keeping this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || keepers.contains(thisEntry) || duplicates.contains(thisEntry)) {
					continue;
				}
				if (thisEntry.getRefsetId().equals(thatEntry.getRefsetId())
						&& thisEntry.getRefsetId().equals(thatEntry.getRefsetId())
						&& thisEntry.getReferencedComponentId().equals(thatEntry.getReferencedComponentId())
						&& thisEntry.getTargetComponentId().equals(thatEntry.getTargetComponentId())) {
					debug("Found duplicates for " + c + ": " + thisEntry + " + " + thatEntry);
					// Delete the unpublished one
					if (t != null && StringUtils.isEmpty(thisEntry.getEffectiveTime()) && StringUtils.isEmpty(thatEntry.getEffectiveTime())) {
						warn("Both entries look modified, checking TS for published status of " + thisEntry);
						RefsetMember r = tsClient.getRefsetMember(thisEntry.getId(), getBranchPath(t));
						warn("Result: Is " + (r.getReleased()?"":"not ") + "published.");
						if (r.getReleased()) {
							duplicates.add(thatEntry);
							keepers.add(thisEntry);
						} else {
							duplicates.add(thisEntry);
							keepers.add(thatEntry);
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
				}
			}
		}
		return duplicates.toArray(new AssociationEntry[] {});
	}

	@Override
	protected List<Component> loadLine(final String[] lineItems)
			throws TermServerScriptException {
		throw new NotImplementedException("This class self determines concepts to process");
	}

}
