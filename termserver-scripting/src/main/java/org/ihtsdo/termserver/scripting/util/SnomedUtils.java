package org.ihtsdo.termserver.scripting.util;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

	public static String substitute(String str,
			Map<String, String> wordSubstitution) {
		//Replace any instances of the map key with the corresponding value
		for (Map.Entry<String, String> substitution : wordSubstitution.entrySet()) {
			str = str.replace(substitution.getKey(), substitution.getValue());
		}
		return str;
	}
	
	/**
	 * Merge two acceptability maps such that a PREFERRED overrides an ACCEPTABLE
	 * AND ACCEPTABLE overrides not acceptable.
	 */
	public static Map<String, ACCEPTABILITY> mergeAcceptabilityMap (Map<String, ACCEPTABILITY> left, Map<String, ACCEPTABILITY> right) {
		Set<String> dialects = new HashSet<String>();
		dialects.addAll(left.keySet());
		dialects.addAll(right.keySet());
		Map<String, ACCEPTABILITY> merged = new HashMap<String, ACCEPTABILITY>();
		
		for (String thisDialect : dialects) {
			if (!left.containsKey(thisDialect) && right.containsKey(thisDialect)) {
				merged.put(thisDialect, right.get(thisDialect));
			} 
			if (!right.containsKey(thisDialect) && left.containsKey(thisDialect)) {
				merged.put(thisDialect, left.get(thisDialect));
			} 
			if (left.containsKey(thisDialect) && right.containsKey(thisDialect)) {
				if (left.get(thisDialect).equals(ACCEPTABILITY.PREFERRED) || right.get(thisDialect).equals(ACCEPTABILITY.PREFERRED)) {
					merged.put(thisDialect, ACCEPTABILITY.PREFERRED);
				} else {
					merged.put(thisDialect, ACCEPTABILITY.ACCEPTABLE);
				}
			}
		}
		return merged;
	}
	
	/**
	 * 2 points for preferred, 1 point for acceptable
	 */
	public static int accetabilityScore (Map<String, ACCEPTABILITY> acceptabilityMap) {
		int score = 0;
		for (ACCEPTABILITY a : acceptabilityMap.values()) {
			if (a.equals(ACCEPTABILITY.PREFERRED)) {
				score += 2;
			} else if (a.equals(ACCEPTABILITY.ACCEPTABLE)) {
				score += 1;
			}
		}
		return score;
	}
	
	public static String capitalize (String str) {
		if (str == null || str.isEmpty() || str.length() < 2) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
		

}
