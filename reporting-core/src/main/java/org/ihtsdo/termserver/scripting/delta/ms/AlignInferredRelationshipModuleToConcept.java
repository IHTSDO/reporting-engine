package org.ihtsdo.termserver.scripting.delta.ms;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;

public class AlignInferredRelationshipModuleToConcept extends DeltaGenerator {

	public static void main(String[] args) throws TermServerScriptException {
		AlignInferredRelationshipModuleToConcept delta = new AlignInferredRelationshipModuleToConcept();
		delta.standardExecution(args);
	}

	@Override
	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (!inScope(c)) {
				continue;
			}
			for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (!c.getModuleId().equals(r.getModuleId())) {
					switchInferredRelModule(c, r);
				}
			}
			
			if (c.isModified()) {
				outputRF2(c, true);  //Will only output dirty fields.
			}
		}
	}

	private void switchInferredRelModule(Concept c, Relationship r) throws TermServerScriptException {
		String origModule = r.getModuleId();
		r.setModuleId(c.getModuleId());
		c.setModified();
		report(c, ReportActionType.MODULE_CHANGE_MADE, origModule +" -> " + r.getModuleId(), r);
	}

}
