package org.ihtsdo.snowowl.test;

import org.ihtsdo.snowowl.test.domain.ConceptHelper;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

public class ConceptCreationTest extends AbstractIntegrationTest {

	@Test
	public void testCreateConceptWithAdditionalRelationship() throws Exception {
		JSONObject concept = ConceptHelper.createConcept();
		ConceptHelper.addRelationship("260686004", "129264002", concept);
		concept = client.createConcept(concept, branchPath).toObject();
		Assert.assertEquals(0, ConceptHelper.findRelationship("260686004", concept).getInt("groupId"));
	}

}
