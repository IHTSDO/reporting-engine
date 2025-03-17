package org.ihtsdo.termserver.scripting.pipeline.nuva;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;

import java.util.*;


public class NuvaTemplatedValenceConcept extends TemplatedConcept implements ContentPipeLineConstants {

	private static final Map<String, NuvaTemplatedValenceConcept> modelledValences = new HashMap<>();
	private static Concept valenceGrouper;

	@Override
	public String getSemTag() {
		return " (valence)";
	}

	public static void initialise(ContentPipelineManager cpm) throws TermServerScriptException {
		TemplatedConcept.cpm = cpm;
		valenceGrouper = cpm.getGraphLoader().getConcept("31002000103|Valence (disposition)|");
	}

	private NuvaValence getNuvaValence() {
		return (NuvaValence) externalConcept;
	}

	protected NuvaTemplatedValenceConcept(ExternalConcept externalConcept) {
		super(externalConcept);
		setPreferredTermTemplate(bracket(NAME));
		slotTermMap.put(NAME, getNuvaValence().getLongDisplayName());
		externalConcept.setProperty("Valence");
	}

	public static NuvaTemplatedValenceConcept getOrModel(NuvaValence valence) {
		NuvaTemplatedValenceConcept valenceTemplate = modelledValences.computeIfAbsent(valence.getExternalIdentifier(), v -> new NuvaTemplatedValenceConcept(valence));
		if (valenceTemplate.getConcept() == null) {
			try {
				valenceTemplate.populateTemplate();
			} catch (TermServerScriptException e) {
				valenceTemplate.getConcept().addIssue(e.getMessage());
			}
		}
		cpm.recordSuccessfulModelling(valenceTemplate);
		return valenceTemplate;
	}

	public static Collection<NuvaTemplatedValenceConcept> getModelledValences() {
		return modelledValences.values();
	}

	@Override
	public String getSchemaId() {
		return SCTID_NUVA_SCHEMA;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		//Override here if template rules needed
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description pt) {
		//Do we need to apply any specific rules to the description?
	}

	public static NuvaTemplatedValenceConcept create(ExternalConcept externalConcept) {
		return new NuvaTemplatedValenceConcept(externalConcept);
	}

	@Override
	protected void populateParts() throws TermServerScriptException {
		concept = Concept.withDefaults(null);
		concept.setModuleId(RF2Constants.SCTID_NUVA_EXTENSION_MODULE);
		//Did the valence report any parent valences?
		NuvaValence valence = (NuvaValence) externalConcept;
		if (valence.getParentValenceIds().isEmpty()) {
			concept.addRelationship(IS_A, valenceGrouper);
		} else {
			for (NuvaValence parentValence : valence.getParentValences()) {
				TemplatedConcept parentValenceTemplate = NuvaTemplatedValenceConcept.getOrModel(parentValence);
				concept.addRelationship(IS_A, parentValenceTemplate.getConcept());
			}
		}
		//Abstract concepts are sufficiently defined, the branded vaccines are not
		DefinitionStatus ds = hasAbstractValences() ? DefinitionStatus.FULLY_DEFINED : DefinitionStatus.PRIMITIVE;
		concept.setDefinitionStatus(ds);
	}

	private boolean hasAbstractValences() {
		return getNuvaValence().getParentValences().stream().anyMatch(NuvaValence::isAbstract);
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		return true;  //All valences are primitive
	}
	
}
