package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.ihtsdo.termserver.scripting.domain.ConceptIds;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

public class ConceptReactivationTest extends AbstractIntegrationTest {

	@Test
	public void test() throws Exception {
		JSONObject concept = client.getConcept("347997007", branchPath).toObject();

		Assert.assertEquals(0, countActiveRelationships(concept));

		concept.put("active", true);
		ConceptHelper.findRelationship(ConceptIds.isA, concept).put("active", true);
		concept = client.updateConcept(concept, branchPath).toObject();

		Assert.assertEquals(1, countActiveRelationships(concept));
	}

	private int countActiveRelationships(JSONObject concept) throws JSONException {
		final JSONArray relationships = concept.getJSONArray("relationships");
		int count = 0;
		for (int i = 0; i < relationships.length(); i++) {
			final JSONObject rel = relationships.getJSONObject(i);
			if (rel.getBoolean("active")) {
				count++;
			}
		}
		return count;
	}

}
