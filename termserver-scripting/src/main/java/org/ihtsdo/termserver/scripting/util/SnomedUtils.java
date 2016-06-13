package org.ihtsdo.termserver.scripting.util;

import java.util.Map;

import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.fixes.TermServerFixException;

public class SnomedUtils implements RF2Constants{

	public static String[] deconstructFSN(String fsn) {
		String[] elements = new String[2];
		int cutPoint = fsn.lastIndexOf(SEMANTIC_TAG_START);
		elements[0] = fsn.substring(0, cutPoint).trim();
		elements[1] = fsn.substring(cutPoint);
		return elements;
	}
	
	public static String toString(Map<String, ACCEPTABILITY> acceptabilityMap) throws TermServerFixException {
		String US = "N";
		String GB = "N";
		if (acceptabilityMap.containsKey(US_ENG_LANG_REFSET)) {
			US = translatAcceptability(acceptabilityMap.get(US_ENG_LANG_REFSET));
		}
		
		if (acceptabilityMap.containsKey(GB_ENG_LANG_REFSET)) {
			GB = translatAcceptability(acceptabilityMap.get(GB_ENG_LANG_REFSET));
		}
		
		return "US: " + US + ", GB: " + GB;
	}
	
	public static String translatAcceptability (ACCEPTABILITY a) throws TermServerFixException {
		if (a.equals(ACCEPTABILITY.PREFERRED)) {
			return "P";
		}
		
		if (a.equals(ACCEPTABILITY.ACCEPTABLE)) {
			return "A";
		}
		throw new TermServerFixException("Unable to translate acceptability " + a);
	}
		

}
