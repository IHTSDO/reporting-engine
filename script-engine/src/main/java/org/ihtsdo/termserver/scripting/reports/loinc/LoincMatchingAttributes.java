package org.ihtsdo.termserver.scripting.reports.loinc;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts in the LOINC module that feature some attribute + value
 * INFRA-4793
 */
public class LoincMatchingAttributes extends TermServerScript{
	
	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		LoincMatchingAttributes report = new LoincMatchingAttributes();
		try {
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.safetyProtocols = false;
			report.init(args);
			report.loadProjectSnapshot(true);
			report.postInit();
			report.reportMatchingConcepts();
		} finally {
			report.finish();
		}
	}

	private void reportMatchingConcepts() throws TermServerScriptException {
		Set<Concept> attributeValues = SUBSTANCE.getDescendents(NOT_SET);
		Concept targetType = gl.getConcept("704327008 |Direct site|");
		int noAttributeTypeDetected = 0;
		int loincInactive = 0;
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			boolean attributeTypeDetected = false;
			if (c.isActive() && c.getModuleId().equals(SCTID_LOINC_MODULE)) {
				//debug(c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.isActive() && r.getType().equals(targetType)) {
						report(c, r);
						attributeTypeDetected = true;
						if (attributeValues.contains(r.getTarget())) {
							report (c, c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
							continue nextConcept;
						}
					}
				}
				if (!attributeTypeDetected) {
					noAttributeTypeDetected++;
				}
			} else if (c.getModuleId().equals(SCTID_LOINC_MODULE)) {
				loincInactive++;
			}
		}
		info ("LOINC concepts not using direct site: " + noAttributeTypeDetected);
		info ("LOINC concepts inactive: " + loincInactive);
	}

}
