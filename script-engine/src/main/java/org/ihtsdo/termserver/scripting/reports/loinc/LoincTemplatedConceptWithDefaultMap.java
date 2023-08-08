package org.ihtsdo.termserver.scripting.reports.loinc;

import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoincTemplatedConceptWithDefaultMap extends LoincTemplatedConcept {

	private static final Logger LOGGER = LoggerFactory.getLogger(LoincTemplatedConceptWithDefaultMap.class);

	private LoincTemplatedConceptWithDefaultMap(String loincNum) {
		super(loincNum);
	}
	
	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConcept templatedConcept = new LoincTemplatedConceptWithDefaultMap(loincNum);
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] to [DIVISOR] in [SYSTEM] at [TIME] by [METHOD] using [using device] [CHALLENGE]";
		return templatedConcept;
	}

	@Override
	protected List<RelationshipTemplate> determineComponentAttributes(String loincNum, List<String> issues)
			throws TermServerScriptException {
		throw new TermServerScriptException("Not expecting to use default map.  LoincNum: " + loincNum);
	}

}
