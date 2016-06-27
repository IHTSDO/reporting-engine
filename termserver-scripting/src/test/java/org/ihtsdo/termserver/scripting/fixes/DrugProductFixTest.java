package org.ihtsdo.termserver.scripting.fixes;

import static org.junit.Assert.*;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ConceptType;
import org.junit.Test;

public class DrugProductFixTest {
	
	DrugProductFix dpf = new DrugProductFix(null);

	@Test
	public void DetermineConceptTypeTest() {
		Concept testConcept = new Concept ("1", "Acetic acid + hydrocortisone 2%vv/1%wv solution (product)");
		dpf.determineConceptType(testConcept);
		assertEquals(ConceptType.PRODUCT_STRENGTH, testConcept.getConceptType());
		
		testConcept = new Concept ("2","Telmisartan 80mg + amlodipine 10mg tablet (product)");
		dpf.determineConceptType(testConcept);
		assertEquals(ConceptType.PRODUCT_STRENGTH, testConcept.getConceptType());
	}

}
