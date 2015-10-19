package org.ihtsdo.snowowl.test;

import org.ihtsdo.snowowl.test.domain.ConceptHelper;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONObject;

public class DescriptionFieldsTest extends AbstractIntegrationTest {

	public static final String CASE_SIGNIFICANCE = "caseSignificance";
	public static final String ENTIRE_TERM_CASE_SENSITIVE = "ENTIRE_TERM_CASE_SENSITIVE";

	@Test
	public void testCreatePrimitiveConcept() throws Exception {
		JSONObject concept = ConceptHelper.createConcept();
		final JSONObject description = ConceptHelper.getFSN(concept);
		description.put(CASE_SIGNIFICANCE, ENTIRE_TERM_CASE_SENSITIVE);
		Assert.assertEquals(ENTIRE_TERM_CASE_SENSITIVE, ConceptHelper.getFSN(concept).getString(CASE_SIGNIFICANCE));
		concept = client.createConcept(concept, branchPath).toObject();
		Assert.assertEquals(ENTIRE_TERM_CASE_SENSITIVE, ConceptHelper.getFSN(concept).getString(CASE_SIGNIFICANCE));
	}

}
