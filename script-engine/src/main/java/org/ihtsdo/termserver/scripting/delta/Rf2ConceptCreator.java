package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Rf2ConceptCreator extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(Rf2ConceptCreator.class);

	public static Rf2ConceptCreator build(TermServerScript clone, File conIdFile, File descIdFile, File relIdFile) throws TermServerScriptException {
		Rf2ConceptCreator conceptCreator = new Rf2ConceptCreator();
		if (clone != null) {
			conceptCreator.setReportManager(clone.getReportManager());
			conceptCreator.project = clone.getProject();
			conceptCreator.tsClient = clone.getTSClient();
			conceptCreator.edition = "INT";
		}
		
		conceptCreator.initialiseOutputDirectory();
		conceptCreator.initialiseFileHeaders();
		
		conceptCreator.conIdGenerator = conceptCreator.initialiseIdGenerator(conIdFile, PartitionIdentifier.CONCEPT);
		conceptCreator.descIdGenerator = conceptCreator.initialiseIdGenerator(descIdFile, PartitionIdentifier.DESCRIPTION);
		conceptCreator.relIdGenerator = conceptCreator.initialiseIdGenerator(relIdFile, PartitionIdentifier.RELATIONSHIP);
	
		return conceptCreator;
	}

	public void writeConceptsToRF2(int tabIdx, List<Concept> concepts, String enforceModule) throws TermServerScriptException {
		for (Concept concept : concepts) {
			writeConceptToRF2(tabIdx, concept, "", enforceModule);
		}
	}

	public Concept writeConceptToRF2(int tabIdx, Concept concept, String info, String enforceModule) throws TermServerScriptException {
		concept.setId(null);
		populateIds(concept, enforceModule);
		//Populate expression now because rels turn to axioms when we output
		String expression = concept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		incrementSummaryInformation("Concepts created");
		outputRF2(concept);  //Will only output dirty fields.
		report(tabIdx, concept, Severity.LOW, ReportActionType.CONCEPT_ADDED, info, SnomedUtils.getDescriptions(concept), expression, "OK");
		return concept;
	}
	
	public void outputRF2(Concept concept) throws TermServerScriptException {
		super.outputRF2(concept);
	}

	public void populateIds(Concept concept, String enforceModule) throws TermServerScriptException {
		convertAcceptabilitiesToRf2(concept);
		for (Component c : SnomedUtils.getAllComponents(concept, true)) {
			populateComponentId(c, enforceModule);
		}
	}
	
	public void populateComponentId(Component c, String enforceModule) throws TermServerScriptException {
		if (enforceModule != null) {
			c.setModuleId(enforceModule);
		}
		c.setDirty();
		
		if (c.getId() == null) {
			switch (c.getComponentType()) {
				case CONCEPT : setConceptId(c);
					break;
				case DESCRIPTION : setDescriptionId(c);
					break;
				case INFERRED_RELATIONSHIP : 
					setRelationshipId(c);
				case STATED_RELATIONSHIP : 
					//No need to do anything here because we'll convert 
					//stated to an axiom and we're not expecting any inferred
					break;
				case ALTERNATE_IDENTIFIER :
					break;  //Has its own ID.  RefCompId will be set via Concept
				default: c.setId(UUID.randomUUID().toString());
			}
		}
	}

	private void setConceptId(Component component) throws TermServerScriptException {
		Concept c = (Concept)component;
		String conceptId = conIdGenerator.getSCTID();
		c.setId(conceptId);
		c.getDescriptions().stream()
			.forEach(d -> d.setConceptId(conceptId));
		c.getRelationships().stream()
			.forEach(d -> d.setSourceId(conceptId));
		c.getAlternateIdentifiers().stream()
			.forEach(a -> a.setReferencedComponentId(conceptId));
	}
	
	private void setDescriptionId(Component component) throws TermServerScriptException {
		Description d = (Description)component;
		String descId = descIdGenerator.getSCTID();
		d.setId(descId);
		d.getLangRefsetEntries().stream()
			.forEach(l -> l.setReferencedComponentId(descId));
	}
	
	private void setRelationshipId(Component component) throws TermServerScriptException {
		Relationship r = (Relationship)component;
		String relId = relIdGenerator.getSCTID();
		r.setId(relId);
	}

	private void convertAcceptabilitiesToRf2(Concept concept) throws TermServerScriptException {
		for (Description d : concept.getDescriptions()) {
			for (Map.Entry<String, Acceptability> entry : d.getAcceptabilityMap().entrySet()) {
				LangRefsetEntry l = new LangRefsetEntry();
				l.setRefsetId(entry.getKey());
				l.setAcceptabilityId(SnomedUtils.translateAcceptabilityToSCTID(entry.getValue()));
				l.setActive(true);
				l.setModuleId(d.getModuleId());
				l.setDirty();
				d.addLangRefsetEntry(l);
			}
		}
	}

	public void finish() {
		closeIdGenerators();
	}

	public void createOutputArchive(int tabIdx) throws TermServerScriptException {
		getRF2Manager().flushFiles(true); //Just flush the RF2, we might want to keep the report going
		File archive = SnomedUtils.createArchive(new File(outputDirName));
		report(tabIdx, "");
		report(tabIdx, ReportActionType.INFO, "Created " + archive.getName());
	}

	public void copyStatedRelsToInferred(Concept c) {
		for (Relationship statedRel : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			Relationship infRel = statedRel.clone();
			infRel.setCharacteristicType(CharacteristicType.INFERRED_RELATIONSHIP);
			infRel.setAxiom(null);
			infRel.setAxiomEntry(null);
			infRel.setDirty();
			infRel.setModuleId(c.getModuleId());
			c.addRelationship(infRel);
		}
	}
}
