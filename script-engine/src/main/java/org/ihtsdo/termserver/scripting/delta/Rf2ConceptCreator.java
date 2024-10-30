package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.util.List;
import java.util.UUID;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class Rf2ConceptCreator extends DeltaGenerator {

	public static Rf2ConceptCreator build(TermServerScript clone, File conIdFile, File descIdFile, File relIdFile, String namespace) throws TermServerScriptException {
		Rf2ConceptCreator conceptCreator = new Rf2ConceptCreator();
		if (clone != null) {
			conceptCreator.setReportManager(clone.getReportManager());
			conceptCreator.project = clone.getProject();
			conceptCreator.tsClient = clone.getTSClient();
			conceptCreator.edition = "INT";
		}
		conceptCreator.initialiseOutputDirectory();
		conceptCreator.initialiseFileHeaders();
		conceptCreator.nameSpace = namespace;
		conceptCreator.conIdGenerator = conceptCreator.initialiseIdGenerator(conIdFile, PartitionIdentifier.CONCEPT, namespace);
		conceptCreator.descIdGenerator = conceptCreator.initialiseIdGenerator(descIdFile, PartitionIdentifier.DESCRIPTION, namespace);
		conceptCreator.relIdGenerator = conceptCreator.initialiseIdGenerator(relIdFile, PartitionIdentifier.RELATIONSHIP, namespace);
	
		return conceptCreator;
	}

	public Concept writeConceptToRF2(int tabIdx, Concept concept, String info) throws TermServerScriptException {
		concept.setId(null);
		populateIds(concept);
		outputRF2(tabIdx, concept, info);  //Will only output dirty fields.
		return concept;
	}
	
	public void outputRF2(int tabIdx, Concept concept, String info) throws TermServerScriptException {
		//Populate expression now because rels turn to axioms when we output
		String expression = concept.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		if (super.outputRF2(concept)) {
			incrementSummaryInformation("Concepts output to RF2");
			report(tabIdx, concept, Severity.LOW, ReportActionType.CONCEPT_ADDED, info, SnomedUtils.getDescriptions(concept), expression, "OK");

			//Set that concept to clean so we don't output it twice
			SnomedUtils.getAllComponents(concept).stream().forEach(c -> c.setClean());
		}
	}

	public void outputRF2Inactivation(Concept concept) throws TermServerScriptException {
		//We'll do inactivations quietly
		super.outputRF2(concept);
		SnomedUtils.getAllComponents(concept).stream().forEach(c -> c.setClean());
	}


	public void populateIds(Concept concept) throws TermServerScriptException {
		for (Component c : SnomedUtils.getAllComponents(concept, true)) {
			populateComponentId(concept, c, targetModuleId);
		}
	}
	
	public void populateComponentId(Concept concept, Component c, String enforceModule) throws TermServerScriptException {
		if (enforceModule != null) {
			c.setModuleId(enforceModule);
		}
		c.setDirty();
		
		switch (c.getComponentType()) {
			case CONCEPT : setConceptId(c);
				break;
			case DESCRIPTION : setDescriptionId(concept.getId(), c);
				break;
			case INFERRED_RELATIONSHIP :
				setRelationshipId(c);
				ensureRelationshipPartsHaveIds(concept, (Relationship)c, enforceModule);
				break;
			case STATED_RELATIONSHIP :
				ensureRelationshipPartsHaveIds(concept, (Relationship)c, enforceModule);
				break;
			case ALTERNATE_IDENTIFIER :
				break;  //Has its own ID.  RefCompId will be set once concept id is known.
			default: c.setId(UUID.randomUUID().toString());
		}
	}

	private void ensureRelationshipPartsHaveIds(Concept c, Relationship r, String enforceModule) throws TermServerScriptException {
		r.setSource(c);
		if (c.getId() == null) {
			populateComponentId(c, c, enforceModule);
		}

		Concept type = r.getType();
		if (type.getId() == null) {
			populateComponentId(c, type, enforceModule);
		}

		Concept target = r.getTarget();
		if (target.getId() == null) {
			populateComponentId(c, target, enforceModule);
		}
	}

	private void setConceptId(Component component) throws TermServerScriptException {
		Concept c = (Concept)component;
		String conceptId = c.getConceptId();
		if (conceptId == null) {
			conceptId = conIdGenerator.getSCTID();
			c.setId(conceptId);
		}

		String finalConceptId = conceptId;
		c.getDescriptions().stream()
			.forEach(d -> d.setConceptId(finalConceptId));
		c.getRelationships().stream()
			.forEach(d -> d.setSourceId(finalConceptId));
		c.getAlternateIdentifiers().stream()
			.forEach(a -> a.setReferencedComponentId(finalConceptId));
	}
	
	private void setDescriptionId(String conceptId, Component component) throws TermServerScriptException {
		Description d = (Description)component;
		d.setConceptId(conceptId);
		String descId = d.getId();
		if (descId == null) {
			descId = descIdGenerator.getSCTID();
			d.setId(descId);
		}

		if (d.getConceptId() == null) {
			throw new TermServerScriptException("Description " + d + " has no concept ID");
		}

		String finalDescId = descId;
		d.getLangRefsetEntries().stream()
			.forEach(l -> {
				l.setReferencedComponentId(finalDescId);
				if (l.getId() == null) {
					l.setId(UUID.randomUUID().toString());
				}
			});
	}
	
	private void setRelationshipId(Component component) throws TermServerScriptException {
		Relationship r = (Relationship)component;
		String relId = r.getRelationshipId();
		if (relId == null) {
			relId = relIdGenerator.getSCTID();
			r.setId(relId);
		}
	}

	@Override
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

	public String getTargetModuleId() {
		return targetModuleId;
	}
}
