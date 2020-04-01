package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.termserver.scripting.client.TermServerClient;


public class DeleteBornInactiveByUUID {

	public static void main(String[] args) throws Exception {
		String url = "http://localhost:8080/";
		TermServerClient client = new TermServerClient(url + "snowowl/snomed-ct/v2", null);
		String branch = "MAIN/CONREQEXT/CONREQEXT-1740";
//		String [] refMembersToDelete = {"2e6c483b-67d9-4c38-812e-74d477e14f13","1ea99a36-8ae1-4327-89bd-2397376d98e5"};
		String [] refMembersToDelete ={"b6f91340-1b64-4a31-a864-073d8926ebfc"};
		for (String refMemberId : refMembersToDelete) {
			client.deleteRefsetMember(refMemberId, branch, false);
		}
		
	}
}
