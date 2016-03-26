package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.ihtsdo.termserver.scripting.domain.ConceptIds;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

public class ConceptCreationTest extends AbstractIntegrationTest {

	@Test
	public void testCreateConceptWithAdditionalRelationship() throws Exception {
		JSONObject concept = ConceptHelper.newConcept();
		ConceptHelper.addRelationship(ConceptIds.method, "129264002", concept);
		concept = client.createConcept(concept, branchPath).toObject();
		Assert.assertNotNull(ConceptHelper.findRelationship(ConceptIds.isA, concept).getString("relationshipId"));
		Assert.assertNotNull(ConceptHelper.findRelationship(ConceptIds.method, concept).getString("relationshipId"));
		Assert.assertEquals(0, ConceptHelper.findRelationship(ConceptIds.method, concept).getInt("groupId"));
	}

	@Test
	public void testReplaceOnlyRelationship() throws Exception {
		JSONObject concept = ConceptHelper.newConcept();
		concept = client.createConcept(concept, branchPath).toObject();
		String firstRelationshipId = ConceptHelper.findRelationship(ConceptIds.isA, concept).getString("relationshipId");
		Assert.assertNotNull(firstRelationshipId);
		ConceptHelper.findRelationship(ConceptIds.isA, concept).remove("relationshipId");
		concept = client.updateConcept(concept, branchPath).toObject();
		String secondRelationshipId = ConceptHelper.findRelationship(ConceptIds.isA, concept).getString("relationshipId");
		Assert.assertNotNull(secondRelationshipId);
		Assert.assertNotEquals(firstRelationshipId, secondRelationshipId);
	}

	@Test
	public void testReplaceAdditionalRelationship() throws Exception {
		JSONObject concept = ConceptHelper.newConcept();
		ConceptHelper.addRelationship(ConceptIds.method, "129264002", concept);
		concept = client.createConcept(concept, branchPath).toObject();
		String firstRelationshipId = ConceptHelper.findRelationship(ConceptIds.method, concept).getString("relationshipId");
		Assert.assertNotNull(firstRelationshipId);
		ConceptHelper.findRelationship(ConceptIds.method, concept).remove("relationshipId");
		concept = client.updateConcept(concept, branchPath).toObject();
		String secondRelationshipId = ConceptHelper.findRelationship(ConceptIds.method, concept).getString("relationshipId");
		Assert.assertNotNull(secondRelationshipId);
		Assert.assertNotEquals(firstRelationshipId, secondRelationshipId);
	}

	@Test
	public void testReplaceFSN() throws Exception {
		JSONObject concept = ConceptHelper.newConcept();
		ConceptHelper.addRelationship(ConceptIds.method, "129264002", concept);
		concept = client.createConcept(concept, branchPath).toObject();

		JSONObject fsn = ConceptHelper.findFSN(concept);
		fsn.remove("descriptionId");
		fsn.remove("term");
		fsn.put("term", "new description");

		client.updateConcept(concept, branchPath);
	}

}
