package org.ihtsdo.termserver.scripting.aag;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.AuthoringAcceptanceGatewayClient;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * MSSP-2084
 * Exceptionally, we're going to allow components that look like they're born inactive
 * because they'd previously existed in another module which the RVF is not aware of.
*/
public class WhitelistBornInactiveComponents extends TermServerScript {

	public static String assertionId = "2b193a88-8dab-4d19-b995-b556ed59398d";
	//public static String assertionText = "New inactive states follow active states in the DESCRIPTION snapshot.";

	public static void main(String[] args) throws TermServerScriptException {
		WhitelistBornInactiveComponents fix = new WhitelistBornInactiveComponents();
		try {
			fix.runStandAlone = false;
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.init(args);
			fix.additionalReportColumns = "Active, Details, Details, Details";
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); 
			fix.postInit();
			fix.doFix();
		} finally {
			fix.finish();
		}
	}

	public void doFix() throws TermServerScriptException {
		AuthoringAcceptanceGatewayClient client = new AuthoringAcceptanceGatewayClient(url, authenticatedCookie);
		int failures = 0;
		for (Concept concept : gl.getAllConcepts()) {
			for (Component c : SnomedUtils.getAllComponents(concept)) {
				try {
					if (inScope(c) && !c.isActive() && !c.isReleased()) {
						WhitelistItem whitelistItem = WhitelistItem.createFromComponent(concept, project.getBranchPath(), assertionId, c);
						WhitelistItem savedWhitelistItem = client.createWhitelistItem(whitelistItem);
						report(PRIMARY_REPORT, concept , "Component WhiteListed", c, savedWhitelistItem);
					}
				} catch (Exception e) {
					report(PRIMARY_REPORT, concept , "API Failure", c, e);
					failures++;
					if (failures >= 3) {
						throw new TermServerScriptException("Failed to whitelist " + c, e);
					}
				}
			}
		}
	}
}
