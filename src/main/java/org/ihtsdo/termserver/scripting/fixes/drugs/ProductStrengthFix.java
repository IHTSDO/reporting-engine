package org.ihtsdo.termserver.scripting.fixes.drugs;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/*
All concepts in the module must be primitive when "Product Strength"
All concepts must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts in the module must be 373873005| Pharmaceutical / biologic product (product).
All concepts must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
All concepts in the module must have one and only one Has dose form attribute when "Product Strength" or "Medicinal Form"
 - The attribute value must be a descendant of 105904009| Type of drug preparation (qualifier value).
 */
public class ProductStrengthFix extends BatchFix implements RF2Constants{

	protected ProductStrengthFix(BatchFix clone) {
		super(clone);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = ensureDefinitionStatus(task, concept, DefinitionStatus.PRIMITIVE);
		changesMade += ensureAcceptableParent(task, concept, PHARM_BIO_PRODUCT);
		validateAttributeValues(task, concept, HAS_ACTIVE_INGRED, SUBSTANCE, Cardinality.AT_LEAST_ONE);
		validateAttributeValues(task, concept, HAS_MANUFACTURED_DOSE_FORM, DRUG_PREPARATION, Cardinality.EXACTLY_ONE);
		return changesMade;
	}

	@Override
	public String getScriptName() {
		return "ProductStrength";
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		throw new TermServerScriptException("Not Implemented");
	}

}
