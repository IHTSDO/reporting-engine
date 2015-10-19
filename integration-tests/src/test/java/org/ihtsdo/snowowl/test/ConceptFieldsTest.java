package org.ihtsdo.snowowl.test;

import org.ihtsdo.snowowl.test.domain.ConceptHelper;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

public class ConceptFieldsTest extends AbstractIntegrationTest {

	public static final String DEFINITION_STATUS = "definitionStatus";
	public static final String PRIMITIVE = "PRIMITIVE";
	public static final String FULLY_DEFINED = "FULLY_DEFINED";

	@Test
	public void testCreatePrimitiveConcept() throws Exception {
		final String expectedDefStatus = PRIMITIVE;
		final JSONObject concept = ConceptHelper.createConcept().put(DEFINITION_STATUS, expectedDefStatus);
		final JSONObject newConcept = client.createConcept(concept, branchPath).toObject();
		Assert.assertEquals(expectedDefStatus, newConcept.get(DEFINITION_STATUS));
	}

	@Test
	public void testCreateFullyDefinedConcept() throws Exception {
		final String expectedDefStatus = FULLY_DEFINED;
		JSONObject concept = ConceptHelper.createConcept().put(DEFINITION_STATUS, expectedDefStatus);

		Assert.assertEquals(expectedDefStatus, concept.get(DEFINITION_STATUS));
		concept = client.createConcept(concept, branchPath).toObject();
		Assert.assertEquals(expectedDefStatus, concept.get(DEFINITION_STATUS));
	}

	@Test
	public void testUpdateToPrimitiveConcept() throws Exception {
		final String initialDefStatus = FULLY_DEFINED;
		final String expectedDefStatus = PRIMITIVE;

		definitionStatusChangeTest(initialDefStatus, expectedDefStatus);
	}

	@Test
	public void testUpdateToFullyDefinedConcept() throws Exception {
		final String initialDefStatus = PRIMITIVE;
		final String expectedDefStatus = FULLY_DEFINED;

		definitionStatusChangeTest(initialDefStatus, expectedDefStatus);
	}

	private void definitionStatusChangeTest(String initialDefStatus, String expectedDefStatus) throws Exception {
		JSONObject concept = ConceptHelper.createConcept();
		concept.put(DEFINITION_STATUS, initialDefStatus);
		concept = client.createConcept(concept, branchPath).toObject();
		Assert.assertEquals(initialDefStatus, concept.get(DEFINITION_STATUS));

		concept.put(DEFINITION_STATUS, expectedDefStatus);
		concept = client.updateConcept(concept, branchPath).object();
		Assert.assertEquals(expectedDefStatus, concept.get(DEFINITION_STATUS));
	}

}
