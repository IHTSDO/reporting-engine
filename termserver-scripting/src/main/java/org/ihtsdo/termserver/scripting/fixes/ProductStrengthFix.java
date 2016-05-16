package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import us.monoid.json.JSONException;
import us.monoid.web.JSONResource;

/*
All concepts in the module must be primitive when "Product Strength", otherwise fully defined
All concepts must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts in the module must be 373873005| Pharmaceutical / biologic product (product).
All concepts must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
All concepts in the module must have one and only one Has dose form attribute when "Product Strength" or "Medicinal Form"
 - The attribute value must be a descendant of 105904009| Type of drug preparation (qualifier value).
Any plus symbol in the name must be surrounded by single space, exclude for "Product Strength"
Ingredients in name should be in alpha order exclude for "Product Strength".  
[Change amounts order to match - not relevant because these should only appear outside Product Strength]
Remove the word "preparation" also "product" when not part of semantic tag - exclude Product Strength
Change m/r to modified-release - exclude Product Strength
2nd ingredient should be lower case - exclude Product Strength

Medicinal Entity plus all descendants in one task, could group by "has active ingredient"
 */
public class ProductStrengthFix extends BatchFix implements RF2Constants{
	
	public static void main(String[] args) throws TermServerFixException, IOException {
		ProductStrengthFix fix = new ProductStrengthFix();
		fix.init(args);
		fix.processFile();
	}

	@Override
	public void doFix(Concept concept, String branchPath) {
		debug ("Examining: " + concept.getConceptId());
		concept = loadConcept(concept,branchPath);
		ensureDefinitionStatus(concept, DEFINITION_STATUS.PRIMITIVE);
		ensureAcceptableParent(concept, PHARM_BIO_PRODUCT);
		
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

}
