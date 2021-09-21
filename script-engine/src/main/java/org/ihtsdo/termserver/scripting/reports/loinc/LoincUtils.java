package org.ihtsdo.termserver.scripting.reports.loinc;

import org.ihtsdo.otf.RF2Constants.ActiveState;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;

public class LoincUtils {
	
	public static String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	public static String CORRELATION_PREFIX = "Correlation ID:";

	public static String getLoincNumFromDescription(Concept c) throws TermServerScriptException {
		try {
			return getLoincNumDescription(c).getTerm().substring(LOINC_NUM_PREFIX.length());
		} catch (Exception e) {
			return "No LOINCNum"; 
		}
	}
	
	public static Description getLoincNumDescription(Concept c) throws TermServerScriptException {
		return getDescription(c, LOINC_NUM_PREFIX); 
	}
	
	public static String getCorrelation(Concept c) throws TermServerScriptException {
		try {
			return getDescription(c, CORRELATION_PREFIX).getTerm().substring(CORRELATION_PREFIX.length());
		} catch (Exception e) {
			return "No Correlation"; 
		}
	}
	
	private static Description getDescription(Concept c, String prefix) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().startsWith(prefix)) {
				return d;
			}
		}
		throw new TermServerScriptException(c + " does not specify a " + prefix);
		
	}
}
