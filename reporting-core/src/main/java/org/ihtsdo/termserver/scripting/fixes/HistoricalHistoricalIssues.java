package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.HistAssocUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
INFRA-2454 Find issues with historical inactivation reasons and associations and
fix them
INFRA-4924 Repair of same
*/
public class HistoricalHistoricalIssues extends BatchFix implements ScriptConstants {
	
	HistAssocUtils histAssocUtils = new HistAssocUtils(this);
	
	protected HistoricalHistoricalIssues(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		HistoricalHistoricalIssues fix = new HistoricalHistoricalIssues(null);
		try {
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.summaryTabIdx = SECONDARY_REPORT;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "Task, TaskDesc, SCTID, FSN, SemTag, ConceptType, Severity, Action, HistAssocId, HistAssoc ET, Details, , , , ,",
				"Item, Count"};
		String[] tabNames = new String[] {	"Changes",
				"Summary"};
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public int doFix(Task task, Concept c, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(c, task.getBranchPath());
		InactivationIndicator origIndicator = loadedConcept.getInactivationIndicator();
		String assocsBeforeStr = loadedConcept.getAssociationTargets().toString(gl);
		int changesMade = fixHistoricalIssues(task, c, loadedConcept);
		if (changesMade > 0) {
			String assocsAfterStr = loadedConcept.getAssociationTargets().toString(gl);
			loadedConcept.setInactivationIndicator(histAssocUtils.getIndicatorFromAssocs(loadedConcept));
			try {
				updateConcept(task, loadedConcept, "");
				String beforeStr = "Before: " + origIndicator + "\n" + assocsBeforeStr;
				String afterStr = "After: " + loadedConcept.getInactivationIndicator()+ "\n" + assocsAfterStr;
				incrementSummaryInformation("Date inactivated = " + c.getEffectiveTime());
				report(task, c, Severity.NONE, ReportActionType.INFO, null, beforeStr, afterStr);
			} catch (Exception e) {
				report(task, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int fixHistoricalIssues(Task t, Concept c, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		//Firstly, do we have multiple inactivation indicators?
		List<InactivationIndicatorEntry> inactivations = c.getInactivationIndicatorEntries(ActiveState.ACTIVE);
		if (inactivations.size() > 1) {
			String inactivationsStr = inactivations.stream()
					.map(i -> SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()).name())
					.collect(Collectors.joining(", \n"));
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Multiple active inactivation indicators", inactivationsStr);
			changesMade++;
		} else {
			String refsetId = "";
			boolean wasaComboDetected = loadedConcept.getAssociationTargets().isWasACombo();
			List<AssociationEntry> assocs = c.getAssociationEntries(ActiveState.ACTIVE, true);  //Historical associations only!
			for (AssociationEntry h : assocs) {
				//If this is a WAS A as part of a combination with other indicators, remove it
				if (h.getRefsetId().equals(SCTID_ASSOC_WAS_A_REFSETID) && wasaComboDetected) {
					report(t, c, Severity.MEDIUM, ReportActionType.ASSOCIATION_REMOVED, h, "WAS A in combination with other association.  Removing.");
					loadedConcept.getAssociationTargets().clearWasA();
					changesMade++;
					continue;
				}
				if (refsetId.isEmpty()) {
					refsetId = h.getRefsetId();
				} else if (!h.getRefsetId().equals(refsetId)) {
					//Is this association type different from the first one we saw?
					report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, h, "Multiple different association types", toString(assocs));
					changesMade++;
				}
				
				//Is the target inactive?  If so, attempt A -> B -> C hop
				Concept replacement = gl.getConcept(h.getTargetComponentId());
				if (!replacement.isActive()) {
					changesMade += attemptAbcHop(t,c, loadedConcept, replacement, h);
				}
			}
		}
		return changesMade;
	}
	
	//TODO This will need to be reworked if NCEPs start to take associations
	private int attemptAbcHop(Task t, Concept a, Concept aLoaded, Concept b, AssociationEntry h) throws TermServerScriptException {
		//a points to b as a replacement, but b is inactive.  What does b point to?
		//See Historical Association Replacement Logic in https://confluence.ihtsdotools.org/display/IT/Managed+Service+-+Extension+Upgrade+Automation+Design
		InactivationIndicator inactivationIndicatorA = a.getInactivationIndicator();
		InactivationIndicator inactivationIndicatorB = b.getInactivationIndicator();
		//Check we've got what we need
		if (inactivationIndicatorA == null || inactivationIndicatorB == null) {
			throw new TermServerScriptException("Inactive concepts encountered without inactivation indicators " + a + " " + b);
		}
		
		//If we've a "limited" indicator, work out what it should be instead
		if (inactivationIndicatorA.equals(InactivationIndicator.LIMITED)) {
			inactivationIndicatorA = histAssocUtils.getIndicatorFromAssocs(a);
		}
		
		if (inactivationIndicatorB.equals(InactivationIndicator.LIMITED)) {
			inactivationIndicatorB = histAssocUtils.getIndicatorFromAssocs(b);
		}
		
		/*if (a.getId().equals("141350007")) {
			debug ("here");
		}*/
		
		if (inactivationIndicatorA.equals(InactivationIndicator.AMBIGUOUS)) {
			if (inactivationIndicatorB.equals(InactivationIndicator.AMBIGUOUS)) {
				histAssocUtils.modifyPossEquivAssocs(t, aLoaded, b, histAssocUtils.getReplacements(b), h);
			} else if (inactivationIndicatorB.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
				aLoaded.getAssociationTargets().remove(b.getId());
				//Is this the only one?  We could maybe leave others intact.
				if (histAssocUtils.getReplacements(a).size() == 1) {
					report(t, a, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, "Removed association as target concept inactivated as NCEP, no other Assocs remain.", b);
					aLoaded.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					report(t, a, Severity.HIGH, ReportActionType.INACT_IND_MODIFIED, h, "Inactivation indicator changed to NCEP because association target has NCEP indicator,", b);
				} else {
					//OK to leave as is, if there are alternative associations
					report(t, a, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, h, "Removed association as target concept inactivated as NCEP, other Assocs remain.", b);
				}
			} else if (inactivationIndicatorB.equals(InactivationIndicator.MOVED_ELSEWHERE)) {
				report(t, a, Severity.HIGH, ReportActionType.VALIDATION_CHECK, h, "Association target 'Moved Elsewhere'.  Manual intervention required", h);
			} else if (inactivationIndicatorB.equals(InactivationIndicator.DUPLICATE)) {
				histAssocUtils.modifyPossEquivAssocs(t, aLoaded, b, histAssocUtils.getReplacements(b), h);
			} else {
				report(t, a, Severity.HIGH, ReportActionType.VALIDATION_CHECK, h, "Developer intervention required - unexpected combination", h);
			}
		} else if (inactivationIndicatorA.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			report(t, a, Severity.MEDIUM, ReportActionType.NO_CHANGE, h, "NCEP does not allow for ABC hop.  Consider lexical/modeling search methods.", h);
		} else if (inactivationIndicatorA.equals(InactivationIndicator.MOVED_ELSEWHERE)) {
			report(t, a, Severity.MEDIUM, ReportActionType.NO_CHANGE, h, "MOVED_ELSEWHERE does not allow for ABC hop.  Consider lexical/modeling search methods.", h);
			if (histAssocUtils.getReplacements(a).size() > 0) {
				report(t, a, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, h, "That said, there do seem to be other associations here.  Please check.", h);
			}
		} else if (inactivationIndicatorA.equals(InactivationIndicator.DUPLICATE)) {
			if (inactivationIndicatorB.equals(InactivationIndicator.AMBIGUOUS)) {
				aLoaded.setInactivationIndicator(InactivationIndicator.AMBIGUOUS);
				aLoaded.getAssociationTargets().clear();
				aLoaded.getAssociationTargets().setPossEquivTo(b.getAssociationTargets().getPossEquivTo());
				report(t, a, Severity.MEDIUM, ReportActionType.INACT_IND_MODIFIED, h, "Inactivation indicator changed to Ambiguous because SAME_AS association target is now inactive with Ambiguous indicator,", b);
			} else if (inactivationIndicatorB.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
				aLoaded.getAssociationTargets().remove(b.getId());
				//Is this the only one?  We could maybe leave others intact.
				if (histAssocUtils.getReplacements(a).size() == 1) {
					report(t, a, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, h, "Removed association as target concept inactivated as NCEP, no other Assocs remain.", b);
					aLoaded.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					report(t, a, Severity.HIGH, ReportActionType.INACT_IND_MODIFIED, "Inactivation indicator changed to NCEP because association target has NCEP indicator,", b);
				} else {
					//OK to leave as is, if there are alternative associations
					report(t, a, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, h, "Removed association as target concept inactivated as NCEP, other Assocs remain.", b);
				}
			} else if (inactivationIndicatorB.equals(InactivationIndicator.MOVED_ELSEWHERE)) {
				report(t, a, Severity.HIGH, ReportActionType.VALIDATION_CHECK, h, "Association target 'Moved Elsewhere'.  Manual intervention required", h);
			} else if (inactivationIndicatorB.equals(InactivationIndicator.DUPLICATE)) {
				//This is our best case scenario.  We just take B's targets as our own.  Direct A->C hop
				a.getAssociationTargets().clear();
				//Now there's a lot of cases where we have two inactive concepts pointing to each other
				if (b.getAssociationTargets().getSameAs().contains(a.getId())) {
					addressCircularReferencing(t, aLoaded, b, h);
				} else {
					//If we've got an active SAME_AS then we can stay duplicate, but if we need to 
					//look further afield, then we need to drop back down to Ambiguous
					boolean activeSameAsFound = false;
					aLoaded.getAssociationTargets().remove(b.getId());
					Set<String> origSameAs = b.getAssociationTargets().getSameAs();
					for (String same : origSameAs) {
						if (gl.getConcept(same, false, false).isActive()) {
							activeSameAsFound = true;
							report(t, a, Severity.LOW, ReportActionType.ASSOCIATION_CHANGED, h, "Same As target inactive, taking target's associations over for self", h);
						} 
					}
					
					if (!activeSameAsFound) {
						aLoaded.setInactivationIndicator(InactivationIndicator.AMBIGUOUS);
						for (String same : origSameAs) {
							Set<Concept> bestActiveAlternatives = histAssocUtils.getActiveReplacementsOrCommonParent(gl.getConcept(same, false, false), b);
							aLoaded.getAssociationTargets().addPossEquivTo(toIdSet(bestActiveAlternatives));
							report(t, a, Severity.LOW, ReportActionType.ASSOCIATION_CHANGED, h, "Same As target inactive, finding best replacements as PossEquivTo", h, bestActiveAlternatives);
						}
					}
				}
			} else {
				report(t, a, Severity.HIGH, ReportActionType.VALIDATION_CHECK, h, "Developer intervention required - unexpected combination", h);
			}
		} else {
			report(t, a, Severity.HIGH, ReportActionType.VALIDATION_CHECK, h, "Developer intervention required - unexpected inactivation indicator", a.getInactivationIndicator());
		}
		
		return CHANGE_MADE;
	}

	private Set<String> toIdSet(Set<Concept> concepts) {
		if (concepts == null || concepts.size() == 0) {
			return new HashSet<>();
		}
		return concepts.stream()
				.map(c -> c.getId())
				.collect(Collectors.toSet());
	}

	private void addressCircularReferencing(Task t, Concept a, Concept b, RefsetMember h) throws TermServerScriptException {
		//Think the best thing we can do is say we're ambiguous and point to the WAS A of b
		if (b.getAssociationTargets().getWasA().size() > 0) {
			a.setInactivationIndicator(InactivationIndicator.AMBIGUOUS);
			histAssocUtils.modifyPossEquivAssocs(t, a, b, b.getAssociationTargets().getWasAConcepts(gl), h);
		}
		report(t, a, Severity.HIGH, ReportActionType.ASSOCIATION_CHANGED, "Circular reference resolved as Ambiguous to common parents", b);
	}

	private String toString(List<AssociationEntry> assocs) {
		return assocs.stream()
				.map(a -> toString(a))
				.collect(Collectors.joining(", \n"));
	}

	private String toString(AssociationEntry a) {
		try {
			return SnomedUtils.translateAssociation(a.getRefsetId()) + " -> " + gl.getConcept(a.getTargetComponentId()).toStringWithIndicator();
		} catch (Exception e) {
			return e.toString() + ": " + a;
		}
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Collection<Concept> allPotential = gl.getAllConcepts();
		//allPotential = Collections.singletonList(gl.getConcept("123055008"));
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		setQuiet(true);
		for (Concept c : allPotential) {
			//Clone the concept so we don't bleed any changes made through into the main run
			Concept cCopy = c.cloneWithIds();
			//Attempt to fix any issues as a first pass
			if (!c.isActive() && fixHistoricalIssues(null, cCopy, cCopy) > 0) {
				allAffected.add(c);
			} else if (c.getInactivationIndicator() != null && c.getInactivationIndicator().equals(InactivationIndicator.LIMITED)) {
				//TODO Agree what we're doing with LIMITED inactivations
				//allAffected.add(c);
			}
		}
		setQuiet(false);
		return new ArrayList<Component>(allAffected);
	}
	
	public void report(Task t, Component c, Severity s, ReportActionType a, RefsetMember r, Object... details) throws TermServerScriptException {
		super.report(t, c, s, a,
				r == null ? "" : r.getId().subSequence(0, 7), 
				r == null ? "" : r.getEffectiveTime(), 
						details);
	}
}
