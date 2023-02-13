package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * LOINC-382 List Primitive LOINC concepts
 */
public class PrimitiveLoinc extends TermServerScript {
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		PrimitiveLoinc report = new PrimitiveLoinc();
		try {
			report.runStandAlone = false;
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.headers="SCTID, FSN, SemTag, PT, LoincNum, Correlation, Update Details, Expression";
			report.init(args);
			report.loadProjectSnapshot(false);
			report.postInit();
			report.reportMatchingConcepts();
		} finally {
			report.finish();
		}
	}

	private void reportMatchingConcepts() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (c.getModuleId() == null) {
				warn ("Invalid concept loaded through reference? " + c.getId());
			} else if (c.isActive() && 
					c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE) &&
					c.isPrimitive()) {
				report (c, 
						c.getPreferredSynonym(),
						LoincUtils.getLoincNumFromDescription(c),
						LoincUtils.getCorrelation(c),
						c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
		}
	}
}
