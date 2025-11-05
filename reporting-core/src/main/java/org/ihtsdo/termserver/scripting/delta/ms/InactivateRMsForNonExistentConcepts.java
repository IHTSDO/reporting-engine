package org.ihtsdo.termserver.scripting.delta.ms;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.snapshot.ArchiveImporter;

public class InactivateRMsForNonExistentConcepts extends DeltaGenerator {

	public static void main(String[] args) throws TermServerScriptException {
		InactivateRMsForNonExistentConcepts delta = new InactivateRMsForNonExistentConcepts();
		delta.getArchiveManager().setLoadOtherReferenceSets(true);
		delta.getArchiveManager().setRunIntegrityChecks(false);
		ArchiveImporter.setSkipSave(true);
		delta.standardExecution(args);
	}

	public void process() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			//Is this a phantom concept?  Grab its 'other' refset members if so, and burn them.   Burn them all.
			if (c.getActive() == null) {
				for (RefsetMember rm : c.getOtherRefsetMembers()) {
					if (rm.isActiveSafely()) {
						//Set the concept to be clean so we don't try and output anything there
						c.setClean();
						rm.setActive(false, true);  //Force dirty
						report(c, Severity.LOW, ReportActionType.REFSET_MEMBER_INACTIVATED, rm);
					}
				}
			}
		}

	}

}
