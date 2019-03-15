package org.ihtsdo.termserver.scripting.fixes;


import org.ihtsdo.termserver.scripting.client.TermServerClient;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONObject;
import us.monoid.web.JSONResource;

public class UpdateDescriptionFix {

	public static void main(String[] args) throws Exception {
		
		String url = "http://localhost:8080/";
		//CONREQEXT-1715
		String branch = "MAIN/CONREQEXT/CONREQEXT-1740";
		TermServerClient client = new TermServerClient(url + "snowowl/snomed-ct/v2", null);
		
		JSONObject concept = client.getConcept("95531001", branch).toObject();
		
		JSONArray descriptions = concept.getJSONArray("descriptions");
		for (int i = 0; i < descriptions.length(); i++) {
			JSONObject description = descriptions.getJSONObject(i);
			if (description.get("descriptionId").equals("512168017")) {
				System.out.println(description);
				description.put("active",true);
			}
			
		}
		client.updateConcept(concept, branch);
		
		
		JSONResource descripiton = client.getDescriptionById("512168017",branch);
		System.out.println(descripiton.toObject());
	

	}

}
