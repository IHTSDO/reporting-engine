package org.ihtsdo.termserver.scripting.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript.ReportActionType;
import org.ihtsdo.termserver.scripting.TermServerScript.Severity;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.InactivationIndicator;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public class HistAssocUtils implements RF2Constants {
	
	private BatchFix ts;
	private GraphLoader gl;
	
	public HistAssocUtils (BatchFix ts) {
		this.ts = ts;
		this.gl = ts.getGraphLoader();
	}

	public void modifyPossEquivAssocs(Task t, Concept incomingConcept, Concept inactivatingConcept,
			Set<Concept> replacements) throws TermServerScriptException {
		
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
				ts.report(t, incomingConcept, Severity.MEDIUM, ReportActionType.INFO, "Inactive replacement " + replacement, " in turn replaced with " + replacementReplacements);
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
		for (String sctId : incomingConcept.getAssociationTargets().getWasA()) {
			reworkOtherAssociations(t, incomingConcept, possEquivs, sctId, "Was A");
		}
		incomingConcept.getAssociationTargets().getWasA().clear();
		
		for (String sctId : incomingConcept.getAssociationTargets().getSameAs()) {
			reworkOtherAssociations(t, incomingConcept, possEquivs, sctId, "Same As");
		}
		incomingConcept.getAssociationTargets().getSameAs().clear();
		
		for (String sctId : incomingConcept.getAssociationTargets().getReplacedBy()) {
			reworkOtherAssociations(t, incomingConcept, possEquivs, sctId, "Replaced By");
		}
		incomingConcept.getAssociationTargets().getReplacedBy().clear();
	}

	private Set<Concept> getActiveReplacementsOrCommonParent(Concept replacement, Concept comingFrom) throws TermServerScriptException {
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

	public void reworkOtherAssociations(Task t, Concept c, Set<String> possEquivs, String sctId,
			String assocType) throws TermServerScriptException {
		//Is this concept already inactive or are we planning on inactivating it?  Can just allow
		//that to happen if so
		Concept assocSource = gl.getConcept(sctId);
		if (!assocSource.isActive()) {
			ts.report(t, c, Severity.HIGH, ReportActionType.NO_CHANGE, "Inactivating Historical association incoming from inactive concept.", assocSource);
			return;
		}
			
		if (ts.getAllComponentsToProcess().contains(assocSource)) {
			ts.report(t, c, Severity.HIGH, ReportActionType.NO_CHANGE, "Inactivating Historical association incoming from concept scheduled for inactivation", assocSource);
			return;
		}
		
		//Otherwise we're going to have to make this hist assoc a possEquiv because we can't have multiple types used
		possEquivs.add(sctId);
		ts.report(t, c, Severity.HIGH, ReportActionType.ASSOCIATION_CHANGED, "Changing incoming historical assocation from " + assocType + " to PossEquivTo", assocSource);
	}

	public Set<Concept> getReplacements(Concept c) throws TermServerScriptException {
		Set<Concept> replacements = new HashSet<>();
		//We'll recover concept from local store again, in case we're working with one loaded from TS
		c = gl.getConcept(c.getId());
		for (AssociationEntry entry : c.getAssociations(ActiveState.ACTIVE, true)) {
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
		}
		//Haven't found what we're looking for?  Keep whatever is current
		return currentIndicator;
	}
}
