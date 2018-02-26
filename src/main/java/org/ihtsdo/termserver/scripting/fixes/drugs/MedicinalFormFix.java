package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/*
All concepts must be fully defined
All concepts must have one and only one stated |Is a| relationship.
 - The parent concept for all concepts must be 373873005| Pharmaceutical / biologic product (product).
All concepts must have one or more Has active ingredient attributes.
 - The attribute values must be a descendant of 105590001|Substance (substance).
Any plus symbol in the name must be surrounded by single space
FSN changes should be mirrored in Preferred Terms
Flag when US/GB Preferred Term does not equal FSN (minus semantic tag)
Ingredients in name should be in alpha order (Change amounts order to match).
Remove the word "preparation" also "product" when not part of semantic tag 
2nd ingredient should be lower case 
Change m/r to modified-release 
All concepts in the module must have one and only one Has dose form attribute when "Product Strength" or "Medicinal Form"
 - The attribute value must be a descendant of 105904009| Type of drug preparation (qualifier value).

 */
public class MedicinalFormFix extends DrugProductFix implements RF2Constants{
	
	static Map<String, String> wordSubstitution = new HashMap<String, String>();
	static {
		wordSubstitution.put("m\\/r", "modified-release");
		wordSubstitution.put("acetaminophen", "paracetamol");
	}

	protected MedicinalFormFix(BatchFix clone) {
		super(clone);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0; //ensureDefinitionStatus(task, concept, DEFINITION_STATUS.FULLY_DEFINED);
		changesMade += ensureAcceptableParent(task, concept, PHARM_BIO_PRODUCT);
		validateAttributeValues(task, concept, HAS_ACTIVE_INGRED, SUBSTANCE, Cardinality.AT_LEAST_ONE);
		validatePrefInFSN(task, concept);
		changesMade += ensureAcceptableFSN(task, concept, wordSubstitution);
		return changesMade;
	}

	@Override
	public String getScriptName() {
		return "MedicinalFormFix";
	}

	@Override
	protected Batch formIntoBatch(String fileName, List<Concept> allConcepts,
			String branchPath) throws TermServerScriptException {
		throw new TermServerScriptException("Not Implemented");
	}

}
