package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;

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
	public int doFix(Task task, Concept concept) throws TermServerFixException {
		int changesMade = ensureDefinitionStatus(task, concept, DEFINITION_STATUS.PRIMITIVE);
		changesMade += ensureAcceptableParent(task, concept, graph.getConcept(PHARM_BIO_PRODUCT_SCTID));
		validateAttributeValues(task, concept, HAS_ACTIVE_INGRED, SUBSTANCE, CARDINALITY.AT_LEAST_ONE);
		validateAttributeValues(task, concept, HAS_DOSE_FORM, DRUG_PREPARATION, CARDINALITY.EXACTLY_ONE);
		return changesMade;
	}


	@Override
	Batch formIntoBatch(String fileName, List<Concept> concepts, String projectPath) throws TermServerFixException {
		throw new TermServerFixException("Not Implemented");
	}

	@Override
	public String getFixName() {
		return "ProductStrength";
	}

	@Override
	Concept loadLine(String[] lineItems) throws TermServerFixException {
		throw new TermServerFixException("Not Implemented");
	}

}
