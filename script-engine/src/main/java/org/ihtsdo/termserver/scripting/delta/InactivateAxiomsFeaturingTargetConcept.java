package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.AxiomEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class InactivateAxiomsFeaturingTargetConcept extends DeltaGenerator implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivateAxiomsFeaturingTargetConcept.class);

	private Concept restrictToType;
	private Concept find;

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		InactivateAxiomsFeaturingTargetConcept delta = new InactivateAxiomsFeaturingTargetConcept();
		try {
			delta.newIdsRequired = false; // We'll only be inactivating existing relationships
			delta.init(args);
			delta.getArchiveManager(true).setLoadOtherReferenceSets(true);
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
		super.postInit();
	}

	private void process() throws TermServerScriptException {
		print ("Processing concepts to remove axioms featuring " + find );
		for (Concept c : GraphLoader.getGraphLoader().getAllConcepts()) {
			if (c.isActive()) {
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, restrictToType, ActiveState.ACTIVE)) {
					if (r.getTarget().equals(find)) {
						AxiomEntry a = r.getAxiomEntry();
						a.setActive(false);
						a.setDirty();
						c.setModified();
						report(c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, r, r.getTarget());
					}
				}
			}
		}
	}

}
