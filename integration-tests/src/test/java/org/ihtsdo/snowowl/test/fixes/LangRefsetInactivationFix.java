package org.ihtsdo.snowowl.test.fixes;

import org.ihtsdo.snowowl.test.client.SnowOwlClient;
import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;
import us.monoid.web.Resty;

import java.io.IOException;
import java.util.*;

/**
 * Fix script to inactivate the language reference sets of inactive descriptions found in a validation report
 */
public class LangRefsetInactivationFix {

	public static void main(String[] args) throws IOException, JSONException {
		String url = "http://localhost:8080/";
		String project = "INTQA";
		String validationReportUrl = url + "snowowl/ihtsdo-sca/projects/" + project + "/validation";
		System.out.println(validationReportUrl);

		Set<String> conceptIds = new LinkedHashSet<>();

		Resty resty = new Resty();
		resty.withHeader("Cookie", "");
		JSONObject validationReport = resty.json(validationReportUrl).toObject();
		JSONObject report = validationReport.getJSONObject("report");
		JSONObject rvfValidationResult = report.getJSONObject("rvfValidationResult");
		JSONObject sqlTestResult = rvfValidationResult.getJSONObject("sqlTestResult");
		JSONArray assertionsFailed = sqlTestResult.getJSONArray("assertionsFailed");
		for (int i = 0; i < assertionsFailed.length(); i++) {
			JSONObject assertionFailed = assertionsFailed.getJSONObject(i);
			if (assertionFailed.getString("assertionText").equals("Members are inactive for inactive descriptions in the language refset snapshot file.")) {
				JSONArray firstNInstances = assertionFailed.getJSONArray("firstNInstances");
				for (int j = 0; j < firstNInstances.length(); j++) {
					JSONObject jsonObject = firstNInstances.getJSONObject(j);
					String conceptId = ((Long)jsonObject.getLong("conceptId")).toString();
					conceptIds.add(conceptId);
				}
			}
		}

		if (!conceptIds.isEmpty()) {
			System.out.println("Concepts in the report that need fixing - " + conceptIds);

			SnowOwlClient client = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");

			String branchPath = "MAIN/" + project;
			for (String conceptId : conceptIds) {
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
		} else {
			System.out.println("No lang refset failures found in report " + validationReportUrl);
		}
	}

}
