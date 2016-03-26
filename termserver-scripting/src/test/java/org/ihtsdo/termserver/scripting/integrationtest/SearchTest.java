package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.domain.ConceptHelper;
import org.ihtsdo.termserver.scripting.util.TimerUtil;
import org.junit.Assert;
import org.junit.Test;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;

public class SearchTest extends AbstractIntegrationTest {

	private final String query = "fsnprefix*";

	@Test
	public void testSearchWithFSN() throws Exception {
		for (int i = 0; i < 10; i++) {
			JSONObject concept = ConceptHelper.newConcept("fsnprefix" + i + " (test)", "ptprefix" + i, "116680003");
			client.createConcept(concept, branchPath);
		}
		
		final TimerUtil timer = new TimerUtil("Search");
		final JSONArray results = client.search(query, branchPath).array();
		timer.finish();
		Assert.assertEquals(10, results.length());
	}
}
