package org.ihtsdo.termserver.scripting.pipeline.nuva.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.nuva.domain.NuvaVaccine;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public class NuvaTemplatedBrandedVaccineConcept extends NuvaTemplatedVaccineConcept{

	protected NuvaTemplatedBrandedVaccineConcept(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static TemplatedConcept create(ExternalConcept externalConcept) {
		return new NuvaTemplatedBrandedVaccineConcept(externalConcept);
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description d) throws TermServerScriptException {
		//Where a branded product exists, we'll use the branded drug name as both the PT, and the FSN (with semtag of vaccine added).
		NuvaVaccine nuvaVaccine = getNuvaVaccine();
		if (nuvaVaccine.isAbstract()) {
			throw new IllegalStateException("Branded vaccine is abstract: " + nuvaVaccine.getExternalIdentifier());
		} else if (nuvaVaccine.getUntranslatedLabels().isEmpty()) {
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
