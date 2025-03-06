package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;

import java.util.*;

public class InactivateAxiomsOrRelationshipsFeaturingTargetConcept extends DeltaGenerator implements ScriptConstants {

	private Concept restrictToType;
	private Concept find;

	public static void main(String[] args) throws TermServerScriptException {
		InactivateAxiomsOrRelationshipsFeaturingTargetConcept delta = new InactivateAxiomsOrRelationshipsFeaturingTargetConcept();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			delta.getArchiveManager().setLoadOtherReferenceSets(true);
			delta.loadProjectSnapshot();
			delta.postInit(GFOLDER_ADHOC_UPDATES);
			delta.process();
			delta.createOutputArchive();
		} finally {
			delta.finish();
		}
	}

	public void postInit(String googleFolder) throws TermServerScriptException {
		eclSubset = "<< 763087004 |Medicinal product categorized by therapeutic role (product)| ";
		restrictToType = gl.getConcept("766939001 |Plays role (attribute)| ");
		super.postInit(googleFolder);
	}

	@Override
	protected void process() throws TermServerScriptException {
		print ("Processing concepts to remove axioms featuring " + find );
		for (Concept c : findConcepts(eclSubset)) {
			if (c.isActive()) {
				processConcept(c);
			}
		}
	}

	private void processConcept(Concept c) throws TermServerScriptException {
		//Is this a multi-axiom concept?  If not, just inactivate that relationship within the axiom
		boolean isMultiAxiom = c.getAxiomEntries(ActiveState.ACTIVE, false).size() > 1;
		Set<Relationship> relationships = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, restrictToType, ActiveState.ACTIVE);
		for (Relationship r : relationships) {
			if (find == null || r.getTarget().equals(find)) {
				if (isMultiAxiom) {
					checkOtherRelationshipsInAxiom(c, r);
					AxiomEntry a = r.getAxiomEntry();
					a.setActive(false);
					a.setDirty();
					c.setModified();
					report(c, Severity.LOW, ReportActionType.AXIOM_CHANGE_MADE, r, r.getTarget());
					break;
				} else {
					r.setActive(false);
					r.setDirty();
					c.setModified();
					report(c, Severity.LOW, ReportActionType.RELATIONSHIP_INACTIVATED, r, r.getTarget());
					break;
				}
			}
		}
	}

	private void checkOtherRelationshipsInAxiom(Concept c, Relationship r) throws ValidationFailure {
		//Are there any other relationships in this axiom?
		List<Relationship> otherRels = getNonISARelsWithCheck(c, r);
		if (otherRels.size() > 1) {
			String detail = find == null ? "" : " featuring value " + find;
			throw new ValidationFailure(c, "Multiple relationships found on " + c + detail + ".  Manual review required.");
		}
	}

	private List<Relationship> getNonISARelsWithCheck(Concept c, Relationship relToRemove) throws ValidationFailure{
		//Now every axiom will include an IS A relationship, so if we're going to inactivate this axiom
		//we need to double check that IS A rels are present in some _other_ axiom on the concept
		List<Relationship> nonISARels = new ArrayList<>();
		for (Relationship r : c.getRelationshipsFromAxiom(relToRemove.getAxiomEntry().getId(), ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				nonISARels.add(r);
			} else {
				//Check if this IS A exists in some _other_ axiom, or throw an error
				boolean isAFoundInOtherAxiom = false;
				for (Relationship r2 : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r2.getAxiomEntry().equals(r.getAxiomEntry())) {
						continue;
					} else if (r2.getType().equals(IS_A) && r2.getTarget().equals(r.getTarget())) {
						isAFoundInOtherAxiom = true;
						break;
					}
				}
				if (!isAFoundInOtherAxiom) {
					throw new ValidationFailure(c, "IS A relationship " + r + " is not found in any other axiom on " + c + ".  Manual review required.");
				}
			}
		}
		return nonISARels;
	}

}
