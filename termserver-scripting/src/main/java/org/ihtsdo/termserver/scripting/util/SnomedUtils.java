package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.DESCRIPTION_TYPE;

public class SnomedUtils implements RF2Constants{

	public static String[] deconstructFSN(String fsn) {
		String[] elements = new String[2];
		int cutPoint = fsn.lastIndexOf(SEMANTIC_TAG_START);
		elements[0] = fsn.substring(0, cutPoint).trim();
		elements[1] = fsn.substring(cutPoint);
		return elements;
	}
	
	public static String toString(Map<String, ACCEPTABILITY> acceptabilityMap) throws TermServerScriptException {
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
	
	public static String translatAcceptability (ACCEPTABILITY a) throws TermServerScriptException {
		if (a.equals(ACCEPTABILITY.PREFERRED)) {
			return "P";
		}
		
		if (a.equals(ACCEPTABILITY.ACCEPTABLE)) {
			return "A";
		}
		throw new TermServerScriptException("Unable to translate acceptability " + a);
	}
	
	public static ACCEPTABILITY getAcceptability(String sctid) throws TermServerScriptException {
		if (sctid.equals(ACCEPTABLE_TERM)) {
			return ACCEPTABILITY.ACCEPTABLE;
		}
		
		if (sctid.equals(PREFERRED_TERM)) {
			return ACCEPTABILITY.PREFERRED;
		} 
		
		throw new TermServerScriptException("Unable to translate acceptability '" + sctid + "'");
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
	
	public static String deCapitalize (String str) {
		if (str == null || str.isEmpty() || str.length() < 2) {
			return str;
		}
		return str.substring(0, 1).toLowerCase() + str.substring(1);
	}	

	public static List<String> removeBlankLines(List<String> lines) {
		List<String> unixLines = new ArrayList<String>();
		for (String thisLine : lines) {
			if (!thisLine.isEmpty()) {
				unixLines.add(thisLine);
			}
		}
		return unixLines;
	}

	/**
	 * @return an array of 3 elements containing:  The path, the filename, the file extension (if it exists) or empty strings
	 */
	public static String[] deconstructFilename(File file) {
		String[] parts = new String[] {"","",""};
		
		if (file== null) {
			return parts;
		}
		parts[0] = file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(File.separator));
		if (file.getName().lastIndexOf(".") > 0) {
			parts[1] = file.getName().substring(0, file.getName().lastIndexOf("."));
			parts[2] = file.getName().substring(file.getName().lastIndexOf(".") + 1);
		} else {
			parts[1] = file.getName();
		}
		
		return parts;
	}

	public static String translateDescType(DESCRIPTION_TYPE type) throws TermServerScriptException {
		switch (type) {
			case FSN : return FSN;
			case SYNONYM : return SYN;
			case DEFINITION : return DEF;
		}
		throw new TermServerScriptException("Unable to translate description type " + type);
	}

	public static DESCRIPTION_TYPE translateDescType(String descTypeId) throws TermServerScriptException {
		switch (descTypeId) {
			case FSN : return DESCRIPTION_TYPE.FSN;
			case SYN : return DESCRIPTION_TYPE.SYNONYM;
			case DEF : return DESCRIPTION_TYPE.DEFINITION; 
		}
		throw new TermServerScriptException("Unable to translate description type: " + descTypeId);
	}
	
	public static String getStackTrace (Exception e) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		return sw.toString(); // stack trace as a string
	}

}
