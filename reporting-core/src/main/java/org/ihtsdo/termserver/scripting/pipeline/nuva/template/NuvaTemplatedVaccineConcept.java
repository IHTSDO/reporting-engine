package org.ihtsdo.termserver.scripting.pipeline.nuva.template;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipeLineConstants;
import org.ihtsdo.termserver.scripting.pipeline.ContentPipelineManager;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.nuva.domain.NuvaVaccine;
import org.ihtsdo.termserver.scripting.pipeline.nuva.domain.NuvaValence;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;

import java.util.List;
import java.util.UUID;

public class NuvaTemplatedVaccineConcept extends TemplatedConcept implements ContentPipeLineConstants {

	protected static Concept hasValence;
	protected static List<String> passiveVaccines;

	public static void initialise(ContentPipelineManager cpm) throws TermServerScriptException {
		TemplatedConcept.initialise(cpm);
		hasValence = cpm.getGraphLoader().getConcept("41002000106 |Has valence (attribute)|");
	}

	@Override
	public String getSemTag() {
		return isPassiveVaccine(getNuvaVaccine()) ?" (product)" : " (vaccine)";
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
		concept.addRelationship(IS_A, ScriptConstants.MEDICINAL_PRODUCT);

		if (isPassiveVaccine(getNuvaVaccine())) {
			concept.addRelationship(HAS_ACTIVE_INGRED, gl.getConcept("112133008 |Immunoglobulin|"));
			concept.addRelationship(PLAYS_ROLE, gl.getConcept("871530006 |Passive immunity stimulant therapeutic role|"));
		} else {
			concept.addRelationship(PLAYS_ROLE, gl.getConcept("318331000221102 |Active immunity stimulant role|"));
		}

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

	protected NuvaVaccine getNuvaVaccine() {
		return (NuvaVaccine) externalConcept;
	}

	@Override
	protected boolean detailsIndicatePrimitiveConcept() throws TermServerScriptException {
		//Real vaccines (not abstract) only differ in their brand name, so we'll mark those as primitive
		return !getNuvaVaccine().isAbstract();
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description d) throws TermServerScriptException {
		//Abstract vaccines will use the OWL label as the PT, and the OWL comment as the FSN
		//The comment will  have been supplied as the longCommonName in the template, so we
		//only need to make a tweak to the PT
		NuvaVaccine nuvaVaccine = getNuvaVaccine();
		if (d.getType().equals(DescriptionType.SYNONYM)) {
			d.setTerm(nuvaVaccine.getLabel("en", "fr"));
		}
	}

	private static boolean isPassiveVaccine(NuvaVaccine vaccine) {
		if (passiveVaccines == null) {
			passiveVaccines = List.of(
				"VAC1223","VAC1156","VAC1104","VAC1099","VAC1097","VAC1093","VAC1092","VAC1091","VAC1089","VAC1088",
				"VAC1087","VAC1084","VAC1083","VAC1082","VAC1081","VAC1069","VAC1062","VAC1050","VAC1046","VAC0941",
				"VAC0923","VAC0867","VAC0864","VAC0863","VAC0580","VAC0579","VAC0503","VAC0502","VAC0501","VAC0500",
				"VAC0499","VAC0498","VAC0497","VAC0496","VAC0485","VAC0458","VAC0442","VAC0394","VAC0378","VAC0358",
				"VAC0357","VAC0317","VAC0308"
			);
		}
		return passiveVaccines.contains(vaccine.getExternalIdentifier());
	}
	
}
