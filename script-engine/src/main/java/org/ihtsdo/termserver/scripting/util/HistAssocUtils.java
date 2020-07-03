package org.ihtsdo.termserver.scripting.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScript.ReportActionType;
import org.ihtsdo.termserver.scripting.TermServerScript.Severity;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

public class HistAssocUtils {
	
	private BatchFix ts;
	private GraphLoader gl;
	
	public HistAssocUtils (BatchFix ts) {
		this.ts = ts;
		this.gl = ts.getGraphLoader();
	}

	public void modifyPossEquivAssocs(Task t, Concept incomingConcept, Concept inactivatingConcept,
			Set<Concept> replacements) throws TermServerScriptException {
		
		if (incomingConcept.getId().equals("140506004")) {
			TermServerScript.debug("here");
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
		//We'll recover concept from local store again, incase we're working with one loaded from TS
		c = gl.getConcept(c.getId());
		for (AssociationEntry entry : c.getAssociations(ActiveState.ACTIVE, true)) {
			replacements.add(gl.getConcept(entry.getTargetComponentId()));
		}
		return replacements;
	}
}
