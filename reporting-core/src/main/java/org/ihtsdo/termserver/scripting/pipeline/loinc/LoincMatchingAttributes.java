package org.ihtsdo.termserver.scripting.pipeline.loinc;

import java.util.HashSet;
import java.util.Set;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * Reports all concepts in the LOINC module that feature some attribute + value
 * INFRA-4793
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincMatchingAttributes extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincMatchingAttributes.class);

	public static void main(String[] args) throws TermServerScriptException {
		LoincMatchingAttributes report = new LoincMatchingAttributes();
		try {
			report.getGraphLoader().setExcludedModules(new HashSet<>());
			report.init(args);
			report.loadProjectSnapshot(true);
			report.postInit();
			report.reportMatchingConcepts();
		} finally {
			report.finish();
		}
	}

	private void reportMatchingConcepts() throws TermServerScriptException {
		Set<Concept> attributeValues = SUBSTANCE.getDescendants(NOT_SET);
		Concept targetType = gl.getConcept("704327008 |Direct site|");
		int noAttributeTypeDetected = 0;
		int loincInactive = 0;
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			boolean attributeTypeDetected = false;
			if (c.isActive() && c.getModuleId().equals(SCTID_LOINC_EXTENSION_MODULE)) {
				//LOGGER.debug(c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.isActive() && r.getType().equals(targetType)) {
						report(c, r);
						attributeTypeDetected = true;
						if (attributeValues.contains(r.getTarget())) {
							report(c, c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
							continue nextConcept;
						}
					}
				}
				if (!attributeTypeDetected) {
					noAttributeTypeDetected++;
					report(c, "Does not feature " + targetType, c.toExpression(CharacteristicType.STATED_RELATIONSHIP));
					
				}
			} else if (c.getModuleId().equals(SCTID_LOINC_EXTENSION_MODULE)) {
				loincInactive++;
			}
		}
		LOGGER.info("LOINC concepts not using direct site: " + noAttributeTypeDetected);
		LOGGER.info("LOINC concepts inactive: " + loincInactive);
	}

}
