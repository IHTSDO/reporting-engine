package org.ihtsdo.termserver.scripting.pipeline.loinc.template;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;
import org.ihtsdo.termserver.scripting.pipeline.domain.ExternalConcept;
import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.loinc.domain.LoincDetail;

import java.util.List;
import java.util.Set;

public class LoincTemplatedConceptWithRelative extends LoincTemplatedConcept {

	protected static final String SEPARATOR = "#SEPARATOR#";

	protected static Set<Concept> calculationConcepts;

	protected LoincTemplatedConceptWithRelative(ExternalConcept externalConcept) {
		super(externalConcept);
	}

	public static LoincTemplatedConcept create(ExternalConcept externalConcept) throws TermServerScriptException {
		LoincTemplatedConceptWithRelative templatedConcept = new LoincTemplatedConceptWithRelative(externalConcept);
		templatedConcept.populateTypeMapCommonItems();
		templatedConcept.typeMap.put("DIVISORS", gl.getConcept("704325000 |Relative to (attribute)|"));
		templatedConcept.typeMap.put("UNITS", gl.getConcept("415067009 |Percentage unit (qualifier value)|"));
		templatedConcept.setPreferredTermTemplate("[PROPERTY] of [COMPONENT] to [DIVISORS] in [SYSTEM] at [TIME] by [METHOD] using [DEVICE] [CHALLENGE]");
		return templatedConcept;
	}

	@Override
	protected void applyTemplateSpecificModellingRules(List<RelationshipTemplate> attributes, Part part, RelationshipTemplate rt) throws TermServerScriptException {
		LoincDetail loincDetail = (LoincDetail) part;
		//Rule 2.d.ii An exception is made for LOINC DIVISORs when the property is one that would require a template
		//with Relative to, e.g., AFr, CFr, MFr, NFr, SFr, VFr, MRto, Ratio, SRto, but there is no DIVISOR LP
		//provided in the detail file, or the DIVISOR LP is LP443411-6 Specimen Volume. In this case, the
		//DIVISOR (Relative to) is not required.

		//We will run this rule when we see the COMPONENT detail, because if there's no Divisor, then
		//this method will not trigger for DIVISOR
		if (loincDetail.getPartTypeName().equals(LOINC_PART_TYPE_COMPONENT)) {
			//Rule i: If the component value is a type of calculation, then we don't
			//need a DIVISOR
			if (getCalculationConcepts().contains(rt.getTarget())) {
				addProcessingFlag(ProcessingFlag.ALLOW_BLANK_DIVISOR);
			}

			//rule ii: If there IS no divisor, or the divisor is LP443411-6 Specimen Volume
			//then allow a blank divisor (actually we'll remove it)
			if (!hasDetailForPartType(LOINC_PART_TYPE_DIVISORS) ||
					getLoincDetailForPartType(LOINC_PART_TYPE_DIVISORS).getPartNumber().equals("LP443411-6")) {
				addProcessingFlag(ProcessingFlag.SUPPRESS_DIVISOR_TERM);
			}
		}
		super.applyTemplateSpecificModellingRules(attributes, part, rt);
	}

	@Override
	protected void applyTemplateSpecificTermingRules(Description d) throws TermServerScriptException {
		//If the DIVISOR slot is still here, but we're allowing it to be empty,
		//remove it, and then remove the SEPARATOR
		if (hasProcessingFlag(ProcessingFlag.ALLOW_BLANK_DIVISOR)
				&& d.getTerm().contains("[DIVISORS]")) {
			d.setTerm(d.getTerm().replace("[DIVISORS] ", ""));
			addProcessingFlag(ProcessingFlag.SUPPRESS_DIVISOR_TERM);
		}

		//If this is the FSN we separate with 'to'.
		//For the PT, separate with '/'
		if (hasProcessingFlag(ProcessingFlag.SUPPRESS_DIVISOR_TERM)) {
			d.setTerm(d.getTerm().replace(SEPARATOR, ""));
		} else if (d.getType().equals(DescriptionType.FSN)) {
			d.setTerm(d.getTerm().replace(SEPARATOR, " to "));
			if (this instanceof LoincTemplatedConceptWithRatio) {
				//And we also want ANOTHER copy of this term (minus the semtag) because
				//the existing PT is being transformed (next line) into the slash separated form
				String fsnMinusSemtag = SnomedUtilsBase.deconstructFSN(d.getTerm())[0];
				Description additionalAcceptableDesc = Description.withDefaults(fsnMinusSemtag, DescriptionType.SYNONYM, defaultAccAcceptabilityMap);
				getConcept().addDescription(additionalAcceptableDesc);
			}
		} else {
			d.setTerm(d.getTerm().replace(SEPARATOR, "/"));
		}
		super.applyTemplateSpecificTermingRules(d);
	}

	private static Set<Concept> getCalculationConcepts() throws TermServerScriptException {
		if (calculationConcepts == null) {
			calculationConcepts = cpm.getGraphLoader()
					.getConcept("540091010000105 |Calculation (calculation)|")
					.getDescendants(NOT_SET);
		}
		return calculationConcepts;
	}

}
