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
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.List;
import java.util.UUID;


public class NuvaTemplatedVaccineConcept extends TemplatedConcept implements ContentPipeLineConstants {

	protected static Concept vaccine;

	public static void initialise(ContentPipelineManager cpm) throws TermServerScriptException {
		TemplatedConcept.cpm = cpm;
		vaccine = cpm.getGraphLoader().getConcept("787859002 |Vaccine product (medicinal product)|");
	}

	@Override
	public String getSemTag() {
		return " (vaccine)";
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		//Override here if template rules needed
	}

	protected NuvaTemplatedVaccineConcept(ExternalConcept externalConcept) {
		super(externalConcept);
		setPreferredTermTemplate(bracket(NAME));
		externalConcept.setProperty("Vaccine");
		slotTermMap.put(NAME, getNuvaVaccine().getLongDisplayName());
	}

	@Override
	public String getSchemaId() {
		return SCTID_NUVA_SCHEMA;
	}

	public static TemplatedConcept create(ExternalConcept externalConcept) {
		return new NuvaTemplatedVaccineConcept(externalConcept);
	}

	@Override
	protected void populateParts() throws TermServerScriptException {
		concept = Concept.withDefaults(null);
		concept.setModuleId(RF2Constants.SCTID_NUVA_EXTENSION_MODULE);
		concept.addRelationship(IS_A, vaccine);
		//Real vaccines (not abstract) only differ in their brand name, so we'll mark those as primitive
		DefinitionStatus ds = getNuvaVaccine().isAbstract() ? DefinitionStatus.FULLY_DEFINED : DefinitionStatus.PRIMITIVE;
		concept.setDefinitionStatus(ds);

		//Now link each valence via a HAS_DISPOSITION attribute
		for (NuvaValence valence : getNuvaVaccine().getValences()) {
			//Have we modelled this valence yet?
			TemplatedConcept templatedValence = NuvaTemplatedValenceConcept.getOrModel(valence);
			//Adding relationships involves a compare call which checks the SCTID of the target, so we need to
			//work out early doors if this is an existing concept, or are we assigning a new ID

			Concept valenceConcept = templatedValence.getConcept();
			//If the valence is new, give it a temporary identifier of a UUID
			if (valenceConcept.getConceptId() == null) {
				valenceConcept.setConceptId(UUID.randomUUID().toString());
			}
			RelationshipTemplate rt = new RelationshipTemplate(HAS_DISPOSITION, valenceConcept);
			concept.addRelationship(rt, GROUP_1);
		}
	}

	private NuvaVaccine getNuvaVaccine() {
		return (NuvaVaccine) externalConcept;
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		//Real vaccines (not abstract) only differ in their brand name, so we'll mark those as primitive
		return !getNuvaVaccine().isAbstract();
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description d) throws TermServerScriptException {
		//For the NUVA PT, we'll keep that as acceptable, and use the brand name as the PT
		if (d.getType().equals(DescriptionType.SYNONYM)) {
			List<String> synonyms = getNuvaVaccine().getSynonyms();
			if (!synonyms.isEmpty()) {
				SnomedUtils.demoteAcceptabilityMap(d);
				if (synonyms.size() > 1) {
					throw new IllegalArgumentException("Vaccine has more than one synonym: " + getNuvaVaccine().getExternalIdentifier());
				}

				Description newPT = Description.withDefaults(synonyms.get(0), DescriptionType.SYNONYM, Acceptability.PREFERRED);
				concept.addDescription(newPT);
			}
		}
	}
	
}
