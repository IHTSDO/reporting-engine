package org.ihtsdo.termserver.scripting.reports.loinc;

import org.ihtsdo.otf.exception.TermServerScriptException;

public class LoincTemplatedConceptWithComponent extends LoincTemplatedConcept {

	private LoincTemplatedConceptWithComponent(String loincNum) {
		super(loincNum);
	}
	
	public static LoincTemplatedConcept create(String loincNum) throws TermServerScriptException {
		LoincTemplatedConceptWithComponent templatedConcept = new LoincTemplatedConceptWithComponent(loincNum);
		templatedConcept.typeMap.put("PROPERTY", gl.getConcept("370130000 |Property (attribute)|"));
		templatedConcept.typeMap.put("SCALE", gl.getConcept("370132008 |Scale type (attribute)|"));
		templatedConcept.typeMap.put("TIME", gl.getConcept("370134009 |Time aspect (attribute)|"));
		templatedConcept.typeMap.put("SYSTEM", gl.getConcept("704327008 |Direct site (attribute)|"));
		templatedConcept.typeMap.put("METHOD", gl.getConcept("246501002 |Technique (attribute)|"));
		templatedConcept.typeMap.put("COMPONENT", gl.getConcept(" 246093002 |Component (attribute)|"));
		
		templatedConcept.preferredTermTemplate = "[PROPERTY] of [COMPONENT] in [SYSTEM] at [TIME] by [METHOD]";
		return templatedConcept;
	}

}
