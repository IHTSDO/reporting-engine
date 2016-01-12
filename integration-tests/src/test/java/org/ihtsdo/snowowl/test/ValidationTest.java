package org.ihtsdo.snowowl.test;

import org.ihtsdo.snowowl.test.domain.ConceptHelper;
import us.monoid.json.JSONObject;

public class ValidationTest extends AbstractIntegrationTest {

//	@Test This spike isn't released yet
	public void testDoubleSpace() throws Exception {
		JSONObject concept = ConceptHelper.newConcept();
		ConceptHelper.addDescription("double  space", ConceptHelper.DescriptionType.SYNONYM, concept);
		client.createConcept(concept, branchPath);
	}

}
