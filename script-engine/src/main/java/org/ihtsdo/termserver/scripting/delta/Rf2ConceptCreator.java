package org.ihtsdo.termserver.scripting.delta;

import java.util.List;
import java.util.UUID;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class Rf2ConceptCreator extends DeltaGenerator {
	
	public static Rf2ConceptCreator build(TermServerScript clone) throws TermServerScriptException {
		Rf2ConceptCreator conceptCreator = new Rf2ConceptCreator();
		if (clone != null) {
			conceptCreator.setReportManager(clone.getReportManager());
			conceptCreator.project = clone.getProject();
			conceptCreator.tsClient = clone.getTSClient();
			conceptCreator.edition = "INT";
		}
		
		conceptCreator.initialiseOutputDirectory();
		conceptCreator.initialiseFileHeaders();
		
		conceptCreator.conIdGenerator = conceptCreator.initialiseIdGenerator("dummy", PartitionIdentifier.CONCEPT);
		conceptCreator.descIdGenerator = conceptCreator.initialiseIdGenerator("dummy", PartitionIdentifier.DESCRIPTION);
		conceptCreator.relIdGenerator = conceptCreator.initialiseIdGenerator("dummy", PartitionIdentifier.RELATIONSHIP);
	
		return conceptCreator;
	}

	protected void writeConceptsToRF2(List<Concept> concepts) throws TermServerScriptException {
		for (Concept concept : concepts) {
			populateIds(concept);
			incrementSummaryInformation("Concepts created");
			outputRF2(concept);  //Will only output dirty fields.
		}
	}

	private void populateIds(Concept concept) throws TermServerScriptException {
		for (Component c : SnomedUtils.getAllComponents(concept)) {
			if (c.getId() == null) {
				switch (c.getComponentType()) {
					case CONCEPT : c.setId(conIdGenerator.getSCTID());
						break;
					case DESCRIPTION : c.setId(descIdGenerator.getSCTID());
						break;
					case INFERRED_RELATIONSHIP : //No need to do anything here because 
					case STATED_RELATIONSHIP : //we'll convert to an axiom
						break;
					default: c.setId(UUID.randomUUID().toString());
				}
			}
		}
	}
	
	public void finish() {
		closeIdGenerators();
	}
}
