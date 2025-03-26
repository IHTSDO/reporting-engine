package org.ihtsdo.termserver.scripting.pipeline.nuva.template;

import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.template.TemplatedConcept;

public class NuvaTemplatedBrandedVaccineConcept extends NuvaTemplatedVaccineConcept{

	protected NuvaTemplatedBrandedVaccineConcept(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static TemplatedConcept create(ExternalConcept externalConcept) {
		return new NuvaTemplatedBrandedVaccineConcept(externalConcept);
	}
}
