package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

public class DescriptionFieldsTest extends AbstractIntegrationTest {

	public static final String CASE_SIGNIFICANCE = "caseSignificance";
	public static final String ENTIRE_TERM_CASE_SENSITIVE = "ENTIRE_TERM_CASE_SENSITIVE";
	public static final String INITIAL_CHARACTER_CASE_INSENSITIVE = "INITIAL_CHARACTER_CASE_INSENSITIVE";

	@Test
	public void testChangeCaseSignificance() throws Exception {
		JSONObject concept = ConceptHelper.newConcept();
		Assert.assertEquals(INITIAL_CHARACTER_CASE_INSENSITIVE, ConceptHelper.findFSN(concept).getString(CASE_SIGNIFICANCE));

		// create with INSENSITIVE
		concept = client.createConcept(concept, branchPath).toObject();

		// assert what comes back
		Assert.assertEquals(INITIAL_CHARACTER_CASE_INSENSITIVE, ConceptHelper.findFSN(concept).getString(CASE_SIGNIFICANCE));

		// change to SENSITIVE on object
		ConceptHelper.findFSN(concept).put(CASE_SIGNIFICANCE, ENTIRE_TERM_CASE_SENSITIVE);

		// update on termserver
		concept = client.updateConcept(concept, branchPath).toObject();

		// assert what comes back
		Assert.assertEquals(ENTIRE_TERM_CASE_SENSITIVE, ConceptHelper.findFSN(concept).getString(CASE_SIGNIFICANCE));
	}

	@Test
	public void testDescriptionInactivationTest() throws Exception {
		JSONObject concept = ConceptHelper.newConcept();
		ConceptHelper.addDescription("hello", ConceptHelper.DescriptionType.SYNONYM, concept);

		concept = client.createConcept(concept, branchPath).toObject();

		ConceptHelper.findDescription(concept, "hello").put("active", "false");
		Assert.assertEquals(2, ConceptHelper.findDescription(concept, "hello").getJSONObject("acceptabilityMap").names().length());

		concept = client.updateConcept(concept, branchPath).toObject();

		Assert.assertFalse(ConceptHelper.findDescription(concept, "hello").has("acceptabilityMap"));

		ConceptHelper.addDescription("another update", ConceptHelper.DescriptionType.SYNONYM, concept);
		concept = client.updateConcept(concept, branchPath).toObject();
	}

}
