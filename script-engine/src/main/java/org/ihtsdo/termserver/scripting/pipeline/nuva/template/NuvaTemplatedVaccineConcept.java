package org.ihtsdo.termserver.scripting.pipeline.nuva.template;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.nuva.domain.NuvaVaccine;
import org.ihtsdo.termserver.scripting.pipeline.nuva.domain.NuvaValence;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.List;
import java.util.UUID;


public class NuvaTemplatedVaccineConcept extends TemplatedConcept implements ContentPipeLineConstants {

	protected static Concept vaccine;
	protected static Concept hasValence;

	public static void initialise(ContentPipelineManager cpm) throws TermServerScriptException {
		TemplatedConcept.initialise(cpm);
		vaccine = cpm.getGraphLoader().getConcept("787859002 |Vaccine product (medicinal product)|");
		hasValence = cpm.getGraphLoader().getConcept("41002000106 |Has valence (attribute)|");
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
		concept.addRelationship(PLAYS_ROLE, gl.getConcept("318331000221102 |Active immunity stimulant role|"));

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
			RelationshipTemplate rt = new RelationshipTemplate(hasValence, valenceConcept);
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
		//Where a branded product exists, we'll use the branded drug name as both the PT, and the FSN (with semtag of vaccine added).
		NuvaVaccine nuvaVaccine = getNuvaVaccine();
		if (!nuvaVaccine.isAbstract()) {
			if (nuvaVaccine.getUntranslatedLabels().isEmpty()) {
				throw new IllegalArgumentException("Vaccine has no untranslated Labels: " + nuvaVaccine.getExternalIdentifier());
			} else {
				//If this is an FSN, we will replace it (adding a new semantic tag) and throw away the procedurally generated one
				//If it's the PT, then we will replace it, and demote the original
				String newTerm = nuvaVaccine.getUntranslatedLabels().get(0);
				if (d.getType().equals(DescriptionType.SYNONYM)) {
					SnomedUtils.demoteAcceptabilityMap(d);
					//We create a new description here as the original one will be added to the concept in the calling function
					Description newPT = Description.withDefaults(newTerm, DescriptionType.SYNONYM, defaultPrefAcceptabilityMap);
					newPT.setModuleId(cpm.getExternalContentModuleId());
					concept.addDescription(newPT);
				} else {
					//We only need to modify the text of the FSN here as no second version is needed
					d.setTerm(newTerm + getSemTag());
				}
			}
		}
	}
	
}
