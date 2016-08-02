package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.ihtsdo.termserver.scripting.client.SnowOwlClient;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;

import us.monoid.json.JSONArray;
import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

public class DuplicateLangRefsetsFix {

	public static void main(String[] args) throws SnowOwlClientException, JSONException, IOException {
		String url = "http://localhost:8080/";
		//CONREQEXT-1715
		String branch = "MAIN/CONREQEXT/CONREQEXT-1740";
//		String branch = "MAIN/CONREQEXT/FIXTEST";
		SnowOwlClient client = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");
		String [] toDelete = {"2549528011","40528016","40525018","2773447011","2838341018","3289886013"};
		
		List<String> descriptionIds = new ArrayList<>();
		descriptionIds.addAll(Arrays.asList(toDelete));
		
		List<String> refsetIds = Arrays.asList("900000000000508004", "900000000000509007");

		for (String descriptionId : descriptionIds) {
			System.out.println("---------Description id:" + descriptionId + "------------");
			for (String refsetId : refsetIds) {
				System.out.println("----------Language refsetId:" + refsetId + "-------------");
				final JSONArray langRefsetMembers = client.getLangRefsetMembers(descriptionId, refsetId, branch);
				// Find newly created active duplicate member to delete
				if (langRefsetMembers.length() == 2) {
					String acceptabilityId = null;
					String duplicateRefMemberId = null;
					JSONObject langRefsetToUpdate = null;
					for (int i=0;i<langRefsetMembers.length();i++) {
						JSONObject jsonObject = langRefsetMembers.getJSONObject(i);
						System.out.println("item" + i);
						System.out.println(jsonObject);
						boolean isActive = jsonObject.getBoolean("active");
						boolean isReleased = jsonObject.getBoolean("released");
						if (isActive) {
							duplicateRefMemberId = (String)jsonObject.get("id");
							acceptabilityId = jsonObject.getString("acceptabilityId");
							
						} else {
							
							langRefsetToUpdate = jsonObject;
						}
					}
					if (duplicateRefMemberId != null) {
						System.out.println("To be deleted:");
						System.out.println(duplicateRefMemberId);
						//To delete the duplicate not released language refset member
					    client.deleteRefsetMember(duplicateRefMemberId,branch,false);
					}
					
					if (langRefsetToUpdate != null && acceptabilityId != null) {
						langRefsetToUpdate.put("active", true);
						langRefsetToUpdate.put("acceptabilityId", acceptabilityId);
						langRefsetToUpdate.put("commitComment", "mch fix");
						System.out.println("Updated:");
						System.out.println(langRefsetToUpdate);
						// update existing updated language refest component
					    client.updateRefsetMember(langRefsetToUpdate, branch,false);
					}

				} else {
					System.out.println("No duplcate found!");
				}
			}
		}
	}
}
