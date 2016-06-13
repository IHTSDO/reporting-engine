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
Flag when US/GB Preferred Term does not equal FSN (minus semantic tag)
Any plus symbol in the name must be surrounded by single space, exclude for "Product Strength"
Ingredients in name should be in alpha order (Change amounts order to match).
Remove the word "preparation" also "product" when not part of semantic tag 
2nd ingredient should be lower case 
FSN changes should be mirrored in Preferred Terms
Medicinal Entity plus all descendants in one task, group by "has active ingredient"
 */
public class MedicinalEntityFix extends DrugProductFix implements RF2Constants{
	
	protected MedicinalEntityFix(BatchFix clone) {
		super(clone);
	}

	@Override
	public int doFix(Batch batch, Concept concept) throws TermServerFixException {
		int changesMade = ensureDefinitionStatus(batch, concept, DEFINITION_STATUS.FULLY_DEFINED);
		changesMade += ensureAcceptableParent(batch, concept, graph.getConcept(PHARM_BIO_PRODUCT_SCTID));
		validateAttributeValues(batch, concept, HAS_ACTIVE_INGRED, SUBSTANCE, CARDINALITY.AT_LEAST_ONE);
		validatePrefInFSN(batch, concept);
		changesMade += ensureAcceptableFSN(batch, concept, null);
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
