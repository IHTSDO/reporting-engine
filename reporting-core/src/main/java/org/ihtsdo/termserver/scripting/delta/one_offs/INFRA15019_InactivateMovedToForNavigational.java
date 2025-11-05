package org.ihtsdo.termserver.scripting.delta.one_offs;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.snapshot.ArchiveImporter;

public class INFRA15019_InactivateMovedToForNavigational extends DeltaGenerator {

	public static void main(String[] args) throws TermServerScriptException {
		INFRA15019_InactivateMovedToForNavigational delta = new INFRA15019_InactivateMovedToForNavigational();
		ArchiveImporter.setSkipSave(true);
		delta.standardExecution(args);
	}

	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			//Is this a navigational concept?  Check for MOVED_TO associations if so
			if (c.getFsn().contains("(navigational concept)")) {
				for (RefsetMember rm : c.getAssociationEntries(ActiveState.ACTIVE, RF2Constants.SCTID_ASSOC_MOVED_TO_REFSETID, true)) {
					if (rm.isActiveSafely()) {
						//Set the concept to be clean so we don't try and output anything there
						c.setClean();
						rm.setActive(false, true);  //Force dirty
						outputRF2(rm);
						report(c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, rm);
					}
				}
			}
		}
	}

}
