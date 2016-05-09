package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

import java.io.IOException;
import java.util.*;

/**
 * Fix script to inactivate the language reference sets of inactive descriptions found in a validation report
 */
public class LangRefsetInactivationFix extends TermServerFix{

	public static void main(String[] args) throws TermServerFixException, IOException, JSONException {
		LangRefsetInactivationFix fixer = new LangRefsetInactivationFix();
		fixer.project = "INTQA";
		fixer.init();
		fixer.fixAll();
	}
		
	public void fixAll () throws TermServerFixException, IOException, JSONException {
		String validationReportUrl = url + "snowowl/ihtsdo-sca/projects/" + project + "/validation";
		print(validationReportUrl);

		Set<String> conceptIds = new LinkedHashSet<>();

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
			print("Concepts in the report that need fixing - " + conceptIds);

			String branchPath = "MAIN/" + project;
			for (String conceptId : conceptIds) {
				doFix(conceptId, branchPath);
			}
		} else {
			print("No lang refset failures found in report " + validationReportUrl);
		}
	}

	@Override
	public void doFix(String conceptId, String branchPath) throws TermServerFixException {
		try{
			JSONObject concept = tsClient.getConcept(conceptId, branchPath).object();
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
				print("Fixing " + conceptId);
				tsClient.updateConcept(concept, branchPath);
			} else {
				print("No issue with " + conceptId);
			}
		}catch (IOException | JSONException | SnowOwlClientException e) {
			throw new TermServerFixException("Failed to fix issue", e);
		}
		
	}

}
