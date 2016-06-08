package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;

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

	public static void main(String[] args) throws TermServerFixException, IOException {
		ProductStrengthFix fix = new ProductStrengthFix(null);
		fix.init(args);
		fix.processFile();
	}

	@Override
	public int doFix(Batch batch, Concept concept) throws TermServerFixException {
		int changesMade = ensureDefinitionStatus(batch, concept, DEFINITION_STATUS.PRIMITIVE);
		changesMade += ensureAcceptableParent(batch, concept, graph.getConcept(PHARM_BIO_PRODUCT_SCTID));
		validateAttributeValues(batch, concept, HAS_ACTIVE_INGRED, SUBSTANCE, CARDINALITY.AT_LEAST_ONE);
		validateAttributeValues(batch, concept, HAS_DOSE_FORM, DRUG_PREPARATION, CARDINALITY.EXACTLY_ONE);
		return changesMade;
	}


	@Override
	List<Batch> formIntoBatches(String fileName, List<Concept> concepts, String projectPath) {
		//Product Strength concepts we'll just simply to in batches of N
		List<Batch> batches = new ArrayList<Batch>();
		Batch thisBatch = new Batch();
		for (int lineNum = 0; lineNum < concepts.size(); lineNum++) {
			//skip the header row
			if (lineNum > 0) {
				thisBatch.addConcept(concepts.get(lineNum));
				if (lineNum % batchSize == 0) {
					batches.add(thisBatch);
					thisBatch = new Batch();
				}
			}
		}
		if (!thisBatch.getConcepts().isEmpty()) {
			batches.add(thisBatch);
		}
		return batches;
	}

	@Override
	public String getFixName() {
		return "ProductStrength";
	}

}
