package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * LOINC-382 List Primitive LOINC concepts
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PrimitiveLoinc extends TermServerScript {

	private static final Logger LOGGER = LoggerFactory.getLogger(PrimitiveLoinc.class);

	public static void main(String[] args) throws TermServerScriptException {
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
				LOGGER.warn ("Invalid concept loaded through reference? " + c.getId());
			} else if (c.isActive() && 
					c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE) &&
					c.isPrimitive()) {
				report(c,
						c.getPreferredSynonym(),
						LoincUtils.getLoincNumFromDescription(c),
						LoincUtils.getCorrelation(c),
						c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
			}
		}
	}
}
