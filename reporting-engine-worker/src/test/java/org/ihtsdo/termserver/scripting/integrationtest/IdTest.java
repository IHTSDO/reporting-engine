package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.ihtsdo.termserver.scripting.domain.ConceptIds;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

import java.io.IOException;

public class IdTest extends AbstractIntegrationTest {

	@Test(expected = IOException.class)
	public void testCreateConceptWithIdAlreadyInUse() throws Exception {
		final JSONObject concept = ConceptHelper.newConcept().put("conceptId", ConceptIds.clinicalFinding);
		client.createConcept(concept, branchPath);
	}

	@Test
	public void testCreateConceptWithExtensionId() throws Exception {
		final String expectedConceptId = ConceptIds.exampleExtensionConceptId;
		final JSONObject concept = ConceptHelper.newConcept().put("conceptId", expectedConceptId);
		final JSONObject newConcept = client.createConcept(concept, branchPath).toObject();
		Assert.assertEquals(expectedConceptId, newConcept.get("conceptId"));
	}

	@Test
	public void testCreateConceptWithRelationshipId() throws Exception {
		final String notExpectedConceptId = ConceptIds.exampleRelationshipId;
		final JSONObject concept = ConceptHelper.newConcept().put("conceptId", notExpectedConceptId);
		final JSONObject newConcept = client.createConcept(concept, branchPath).toObject();
		Assert.assertNotEquals(notExpectedConceptId, newConcept.get("conceptId"));
	}

}
