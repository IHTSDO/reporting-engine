package org.ihtsdo.termserver.scripting.fixes;

import java.util.List;

import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

/*
All concepts must be fully defined
All concepts must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts must be 373873005| Pharmaceutical / biologic product (product).
All concepts must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
All concepts in the module must have one and only one Has dose form attribute when "Product Strength" or "Medicinal Form"
 - The attribute value must be a descendant of 105904009| Type of drug preparation (qualifier value).
Any plus symbol in the name must be surrounded by single space, exclude for "Product Strength"
Ingredients in name should be in alpha order (Change amounts order to match).
Remove the word "preparation" also "product" when not part of semantic tag 
Change m/r to modified-release 
2nd ingredient should be lower case 

Medicinal Entity plus all descendants in one task, group by "has active ingredient"
 */
public class MedicinalEntityFix extends BatchFix implements RF2Constants{
	
	protected MedicinalEntityFix(BatchFix clone) {
		super(clone);
	}

	@Override
	public int doFix(Batch batch, Concept concept) throws TermServerFixException {
		int changesMade = ensureDefinitionStatus(batch, concept, DEFINITION_STATUS.FULLY_DEFINED);
		changesMade += ensureAcceptableParent(batch, concept, graph.getConcept(PHARM_BIO_PRODUCT_SCTID));
		return changesMade;
	}

	@Override
	public String getFixName() {
		return "MedicinalEntity";
	}

	@Override
	List<Batch> formIntoBatches(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerFixException {
		throw new TermServerFixException("Not Implemented");
	}
}
