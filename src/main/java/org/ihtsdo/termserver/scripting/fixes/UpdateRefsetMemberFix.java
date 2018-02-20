package org.ihtsdo.termserver.scripting.fixes;


import org.ihtsdo.termserver.scripting.client.SnowOwlClient;

import us.monoid.json.JSONObject;

public class UpdateRefsetMemberFix {

	public static void main(String[] args) throws Exception {

		String url = "http://localhost:8080/";
		SnowOwlClient client = new SnowOwlClient(url + "snowowl/snomed-ct/v2", "snowowl", "snowowl");
		String branch = "MAIN/CONREQEXT/CONREQEXT-1740";
		
//		CONREQEXT-1715
//		String [] toUpdate = {"0845f3af-a91e-555f-83c9-261d82120c88","5533d7c5-c445-5197-a8f0-d4a002f359b3"};
		String[] toUpdate= {"b6f91340-1b64-4a31-a864-073d8926ebfc"};
		for (String id : toUpdate) {
			JSONObject refsetMember = client.getRefsetMemberById(id, branch).toObject();
			System.out.println(refsetMember);
//			refsetMember.put("commitComment", "mch fix");
//			refsetMember.put("active", false);
//			client.updateRefsetMember(refsetMember, branch, true);
		}
		

	}

}
