package org.ihtsdo.termserver.scripting.integrationtest;

import org.ihtsdo.termserver.scripting.util.TimerUtil;
import org.junit.Assert;
import org.junit.Test;
import us.monoid.json.JSONArray;

public class SearchTest extends AbstractIntegrationTest {

	private final String query = "heart*";

	@Test
	public void testSearchWithFSN() throws Exception {
		final TimerUtil timer = new TimerUtil("Search");
		final JSONArray results = client.search(query, branchPath).array();
		timer.finish();
		Assert.assertEquals(49, results.length());
	}

	@Test
	public void testSearchWithPT() throws Exception {
		final TimerUtil timer = new TimerUtil("Search");
		final JSONArray results = client.searchWithPT(query, branchPath).array();
		timer.finish();
		Assert.assertEquals(49, results.length());
	}

}
