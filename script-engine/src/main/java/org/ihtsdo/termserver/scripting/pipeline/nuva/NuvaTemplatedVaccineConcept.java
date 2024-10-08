package org.ihtsdo.termserver.scripting.pipeline.nuva;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.TemplatedConcept;


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

	protected NuvaTemplatedVaccineConcept(ExternalConcept externalConcept) {
		super(externalConcept);
		setPreferredTermTemplate(bracket(NAME));
		externalConcept.setProperty("Vaccine");
		slotTermMap.put(NAME, getNuvaVaccine().getLongDisplayName());
	}

	@Override
	protected String getCodeSystemSctId() {
		return SCTID_NUVA_SCHEMA;
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description pt) {
		//Do we need to apply any specific rules to the description?
	}

	public static TemplatedConcept create(ExternalConcept externalConcept) {
		return new NuvaTemplatedVaccineConcept(externalConcept);
	}

	@Override
	protected void populateParts() throws TermServerScriptException {
		concept = Concept.withDefaults(null);
		concept.setModuleId(RF2Constants.SCTID_NUVA_EXTENSION_MODULE);
		concept.addRelationship(IS_A, vaccine);
		concept.setDefinitionStatus(DefinitionStatus.FULLY_DEFINED);

		//Now link each valence via a HAS_DISPOSITION attribute
		for (NuvaValence valence : getNuvaVaccine().getValences()) {
			//Have we modelled this valence yet?
			TemplatedConcept templatedValence = NuvaTemplatedValenceConcept.getOrModel(valence);
			//Adding relationships involves a compare call which checks the SCTID of the target, so we need to
			//work out early doors if this is an existing concept, or are we assigning a new ID
			//TODO
			RelationshipTemplate rt = new RelationshipTemplate(HAS_DISPOSITION, templatedValence.getConcept());
			concept.addRelationship(rt, GROUP_1);
		}
	}

	private NuvaVaccine getNuvaVaccine() {
		return (NuvaVaccine) externalConcept;
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		return false;
	}
	
}
