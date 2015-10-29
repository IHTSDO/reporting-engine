package org.ihtsdo.snowowl.test;

import org.ihtsdo.snowowl.test.domain.ConceptHelper;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

public class RelationshipMutableFieldsTest extends AbstractIntegrationTest {

	public static final String GROUP_ID = "groupId";
	public static final String MODIFIER = "modifier";

	@Test
	public void testChangeGroupId() throws Exception {
		JSONObject concept = ConceptHelper.createConcept();
		concept = client.createConcept(concept, branchPath).object();

		ConceptHelper.addRelationship("260686004", "129264002", concept);
		Assert.assertEquals(0, ConceptHelper.findRelationship("260686004", concept).getInt(GROUP_ID));
		concept = client.updateConcept(concept, branchPath).object();

		ConceptHelper.findRelationship("260686004", concept).put(GROUP_ID, 1);
		Assert.assertEquals(1, ConceptHelper.findRelationship("260686004", concept).getInt(GROUP_ID));
		concept = client.updateConcept(concept, branchPath).object();
		Assert.assertEquals(1, ConceptHelper.findRelationship("260686004", concept).getInt(GROUP_ID));
	}

	@Test
	public void testChangeModifier() throws Exception {
		final String newModifier = "UNIVERSAL";

		// Create
		JSONObject concept = client.createConcept(ConceptHelper.createConcept(), branchPath).object();
		ConceptHelper.addRelationship("260686004", "129264002", concept);
		concept = client.updateConcept(concept, branchPath).object();

		// Assert not equals
		Assert.assertNotEquals(newModifier, ConceptHelper.findRelationship("260686004", concept).getString(MODIFIER));

		// Update
		ConceptHelper.findRelationship("260686004", concept).put(MODIFIER, newModifier);
		Assert.assertEquals(newModifier, ConceptHelper.findRelationship("260686004", concept).getString(MODIFIER));
		concept = client.updateConcept(concept, branchPath).object();

		// Assert updated
		Assert.assertEquals(newModifier, ConceptHelper.findRelationship("260686004", concept).getString(MODIFIER));
	}

}
