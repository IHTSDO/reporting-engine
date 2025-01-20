package org.ihtsdo.termserver.scripting.util;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HistAssocUtils implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(HistAssocUtils.class);

	private BatchFix ts;
	private GraphLoader gl;
	
	public HistAssocUtils (BatchFix ts) {
		this.ts = ts;
		this.gl = ts.getGraphLoader();
	}

	public void modifyPossEquivAssocs(Task t, Concept incomingConcept, Concept inactivatingConcept,
			Set<Concept> replacements, RefsetMember h) throws TermServerScriptException {
		
		/*if (incomingConcept.getId().equals("140506004")) {
			TermServerScript.debug("here");
		}*/
		//If the replacements are inactive, then try and find a best fit replacement for that
		Set<Concept> originalReplacements = new HashSet<>(replacements);
		for (Concept replacement : originalReplacements) {
			if (!replacement.isActive()) {
				replacements.remove(replacement);
				//What is the replacement for the replacement?
				Set<Concept> replacementReplacements = getActiveReplacementsOrCommonParent(replacement, incomingConcept);
				report(t, incomingConcept, Severity.MEDIUM, ReportActionType.ASSOCIATION_CHANGED, h, "Inactive replacement " + replacement, " in turn replaced with " + replacementReplacements);
				replacements.addAll(replacementReplacements);
			}
		}
		
		Set<String> possEquivs = incomingConcept.getAssociationTargets().getPossEquivTo();
		List<String> replacementSCTIDs = replacements.stream()
				.map(c -> c.getId())
				.collect(Collectors.toList());
		possEquivs.remove(inactivatingConcept.getId());
		possEquivs.addAll(replacementSCTIDs);
		
		//Check out any other historical associations we might have
		for (String assocTarget : incomingConcept.getAssociationTargets().getWasA()) {
			reworkOtherAssociations(t, incomingConcept, possEquivs, assocTarget, "Was A", h);
		}
		incomingConcept.getAssociationTargets().getWasA().clear();
		
		for (String sctId : incomingConcept.getAssociationTargets().getSameAs()) {
			reworkOtherAssociations(t, incomingConcept, possEquivs, sctId, "Same As", h);
		}
		incomingConcept.getAssociationTargets().getSameAs().clear();
		
		for (String sctId : incomingConcept.getAssociationTargets().getReplacedBy()) {
			reworkOtherAssociations(t, incomingConcept, possEquivs, sctId, "Replaced By", h);
		}
		incomingConcept.getAssociationTargets().getReplacedBy().clear();
		
		for (String sctId : incomingConcept.getAssociationTargets().getAlternatives()) {
			reworkOtherAssociations(t, incomingConcept, possEquivs, sctId, "Alternative", h);
		}
		incomingConcept.getAssociationTargets().getAlternatives().clear();
	}

	public Set<Concept> getActiveReplacementsOrCommonParent(Concept replacement, Concept comingFrom) throws TermServerScriptException {
		//If our replacement is active, then that's cool, no need to do anything else
		if (replacement.isActive()) {
			return Collections.singleton(replacement);
		}
		
		Set<Concept> activeReplacements = new HashSet<>();
		Set<Concept> initiallySuggestedReplacements = getReplacements(replacement);
		//Don't want to get into an infinite loop
		if (comingFrom != null) {
			initiallySuggestedReplacements.remove(comingFrom);
		}
		
		if (initiallySuggestedReplacements.size() == 0) {
			initiallySuggestedReplacements = getMostRecentlyActiveParents(replacement);
		}
		
		for (Concept c : initiallySuggestedReplacements) {
			if (c.isActive()) {
				activeReplacements.add(c);
			} else {
				activeReplacements.addAll(getActiveReplacementsOrCommonParent(c, replacement));
			}
		}
		return activeReplacements;
	}

	private Set<Concept> getMostRecentlyActiveParents(Concept c) {
		Set<Concept> mostRecentParents = new HashSet<>();
		String mostRecentEffectiveDate = getMostRecentEffectiveDate(c);
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_A, ActiveState.BOTH)) {
			if (r.getEffectiveTime().equals(mostRecentEffectiveDate)) {
				mostRecentParents.add(r.getTarget());
			}
		}
		return mostRecentParents;
	}

	private String getMostRecentEffectiveDate(Concept c) {
		String mostRecentEffectiveDate = null;
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_A, ActiveState.BOTH)) {
			if (mostRecentEffectiveDate == null || r.getEffectiveTime().compareTo(mostRecentEffectiveDate) > 1) {
				mostRecentEffectiveDate = r.getEffectiveTime();
			}
		}
		return mostRecentEffectiveDate;
	}

	public void reworkOtherAssociations(Task t, Concept c, Set<String> possEquivs, String assocTargetId,
			String assocType, RefsetMember h) throws TermServerScriptException {
		//Is this concept already inactive or are we planning on inactivating it?  Can just allow
		//that to happen if so
		Concept assocTarget = gl.getConcept(assocTargetId);
		if (!assocTarget.isActive()) {
			report(t, c, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, h, "Inactivating Historical association to inactive concept", assocTarget);
			return;
		}
			
		if (ts.getAllComponentsToProcess().contains(assocTarget)) {
			report(t, c, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, h, "Inactivating Historical association to concept scheduled for inactivation", assocTarget);
			return;
		}
		
		//Otherwise we're going to have to make this hist assoc a possEquiv because we can't have multiple types used
		possEquivs.add(assocTargetId);
		report(t, c, Severity.HIGH, ReportActionType.ASSOCIATION_CHANGED, h, "Changing historical association from " + assocType + " to PossEquivTo", assocTarget);
	}

	public Set<Concept> getReplacements(Concept c) throws TermServerScriptException {
		Set<Concept> replacements = new HashSet<>();
		//We'll recover concept from local store again, in case we're working with one loaded from TS
		c = gl.getConcept(c.getId());
		for (AssociationEntry entry : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
			replacements.add(gl.getConcept(entry.getTargetComponentId()));
		}
		return replacements;
	}

	public InactivationIndicator getIndicatorFromAssocs(Concept c) {
		InactivationIndicator currentIndicator = c.getInactivationIndicator();
		AssociationTargets targets = c.getAssociationTargets();
		//Get inactivation indicator that's appropriate for the associations
		if (targets.getSameAs().size() > 0) {
			return InactivationIndicator.DUPLICATE;
		} else if (targets.getMovedTo().size() > 0) {
			return InactivationIndicator.MOVED_ELSEWHERE;
		} else if (targets.getReplacedBy().size() > 0) {
			return InactivationIndicator.OUTDATED;
		} else if (targets.getPossEquivTo().size() > 0) {
			return InactivationIndicator.AMBIGUOUS;
		} else if (targets.getPartEquivTo().size() > 0) {
			return InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT;
		}
		//Haven't found what we're looking for?  Keep whatever is current
		return currentIndicator;
	}
	
	public void report(Task t, Component c, Severity s, ReportActionType a, RefsetMember r, Object... details) throws TermServerScriptException {
		ts.report(t, c, s, a, 
				r == null ? "" : r.getId().subSequence(0, 7), 
				r == null ? "" :r.getEffectiveTime(), 
						details);
	}
}
