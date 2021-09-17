package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.IOException;
import java.util.HashSet;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * LOINC-382 List Primitive LOINC concepts
 */
public class PrimitiveLoinc extends TermServerScript{
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		PrimitiveLoinc report = new PrimitiveLoinc();
		try {
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.getArchiveManager().setRunIntegrityChecks(false);
			report.headers="SCTID, FSN, SemTag, PT, LoincNum, Correlation, Expression";
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
			if (c.isActive() && 
					c.getModuleId().equals(SCTID_LOINC_MODULE) &&
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
