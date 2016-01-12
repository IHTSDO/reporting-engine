package org.ihtsdo.snowowl.test.fixes;

import org.ihtsdo.snowowl.test.client.SnowOwlClient;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Fix script to inactivate the language reference sets of inactive descriptions for a list of concepts.
 */
public class LangRefsetInactivationFix {

	public static void main(String[] args) throws IOException, JSONException {
//		String url = "https://dev-term.ihtsdotools.org/snowowl/snomed-ct/v2";
		String url = "http://localhost:8080/snowowl/snomed-ct/v2";
		SnowOwlClient client = new SnowOwlClient(url, "snowowl", "snowowl");

		String branchPath = "MAIN/INTQA";
		Set<String> concepts = new LinkedHashSet<>();
		concepts.add("472805006");
		concepts.add("286331000119109");
		concepts.add("286351000119103");
		concepts.add("286341000119100");
		concepts.add("286361000119101");
		concepts.add("472756005");
		concepts.add("472820000");
		concepts.add("706156002");
		for (String conceptId : concepts) {
			JSONObject concept = client.getConcept(conceptId, branchPath).object();
			boolean fixed = false;
			JSONArray descriptions = concept.getJSONArray("descriptions");
			for (int i = 0; i < descriptions.length(); i++) {
				JSONObject description = descriptions.getJSONObject(i);
				if (!description.getBoolean("active")) {
					if (description.has("acceptabilityMap")) {
						JSONObject acceptabilityMap = description.getJSONObject("acceptabilityMap");
						if (acceptabilityMap != null && acceptabilityMap.length() > 0) {
							description.remove("acceptabilityMap");
							description.putOnce("acceptabilityMap", new JSONObject());
							fixed = true;
						}
					}
				}
			}
			if (fixed) {
				System.out.println("Fixing " + conceptId);
				client.updateConcept(concept, branchPath);
			} else {
				System.out.println("No issue with " + conceptId);
			}
		}
	}

}
