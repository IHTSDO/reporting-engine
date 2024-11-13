package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class ReplaceRelationshipTargets extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceRelationshipTargets.class);

	private Concept restrictToType;
	private Concept find;
	private Concept replace;

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ReplaceRelationshipTargets delta = new ReplaceRelationshipTargets();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			delta.loadProjectSnapshot();
			delta.postInit();
			delta.process();
			delta.createOutputArchive();
		} finally {
			delta.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		restrictToType = gl.getConcept("246501002 |Technique|");
		find = gl.getConcept("10061010000109 |Screening technique (qualifier value)|");
		replace = gl.getConcept("1287497009 |Screening technique (qualifier value)|");
		super.postInit();
	}

	@Override
	protected void process() throws TermServerScriptException {
		print ("Processing concepts replace " + find + " with " + replace);
		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (c.isActive()) {
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, restrictToType, ActiveState.ACTIVE)) {
					if (r.getTarget().equals(find)) {
						Relationship replacementRel = r.cloneWithoutAxiom(null);
						//TODO We're ending up with a new axiom.   Modify the existing one instead.
						c.removeRelationship(r);
						replacementRel.setTarget(replace);
						c.addRelationship(replacementRel);
						c.setModified();
						incrementSummaryInformation("Relationships changed");
						report(c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, r, r.getTarget());
					}
				}
			}
		}
	}

}
