package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.LangRefsetEntry;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ACTIVE_STATE;

public class SnomedUtils implements RF2Constants{
	
	private static VerhoeffCheckDigit verhoeffCheck = new VerhoeffCheckDigit();

	public static String isValid(String sctId, PartionIdentifier partitionIdentifier) {
		String errorMsg=null;
		int partitionNumber = Integer.valueOf("" + sctId.charAt(sctId.length() -2));
		if ( partitionNumber != partitionIdentifier.ordinal()) {
			errorMsg = sctId + " does not exist in partition " + partitionIdentifier.toString();
		}
		if (!verhoeffCheck.isValid(sctId)) {
			errorMsg = sctId + " does not exhibit a valid check digit";
		}
		return errorMsg;
	}

	public static void isValid(String sctId, PartionIdentifier partitionIdentifier,
			boolean errorIfInvalid) throws TermServerScriptException {
		String errMsg = isValid(sctId,partitionIdentifier);
		if (errorIfInvalid && errMsg != null) {
			throw new TermServerScriptException(errMsg);
		}
	}

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
	
	public static void createArchive(File dirToZip) throws TermServerScriptException {
		try {
			// The zip filename will be the name of the first thing in the zip location
			// ie in this case the directory SnomedCT_RF1Release_INT_20150731
			String zipFileName = dirToZip.listFiles()[0].getName() + ".zip";
			int fileNameModifier = 1;
			while (new File(zipFileName).exists()) {
				zipFileName = dirToZip.listFiles()[0].getName() + "_" + fileNameModifier++ + ".zip";
			}
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFileName));
			String rootLocation = dirToZip.getAbsolutePath() + File.separator;
			TermServerScript.println("Creating archive : " + zipFileName + " from files found in " + rootLocation);
			addDir(rootLocation, dirToZip, out);
			out.close();
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to create archive from " + dirToZip, e);
		} finally {
			try {
				FileUtils.deleteDirectory(dirToZip);
			} catch (IOException e) {}
		}
	}
	
	public static void addDir(String rootLocation, File dirObj, ZipOutputStream out) throws IOException {
		File[] files = dirObj.listFiles();
		byte[] tmpBuf = new byte[1024];

		for (int i = 0; i < files.length; i++) {
			if (files[i].isDirectory()) {
				addDir(rootLocation, files[i], out);
				continue;
			}
			FileInputStream in = new FileInputStream(files[i].getAbsolutePath());
			String relativePath = files[i].getAbsolutePath().substring(rootLocation.length());
			TermServerScript.debug(" Adding: " + relativePath);
			out.putNextEntry(new ZipEntry(relativePath));
			int len;
			while ((len = in.read(tmpBuf)) > 0) {
				out.write(tmpBuf, 0, len);
			}
			out.closeEntry();
			in.close();
		}
	}
	
	public static boolean conceptHasActiveState(Concept c, ACTIVE_STATE a) {
		boolean hasActiveState = false;
		if (a.equals(ACTIVE_STATE.BOTH) ||
			(a.equals(ACTIVE_STATE.ACTIVE) && c.isActive()) ||
			(a.equals(ACTIVE_STATE.INACTIVE) && !c.isActive())) {
			hasActiveState = true;
		}
		return hasActiveState;
	}
	//TODO See if the JSON will allow us to create the abstract "Component" which allows us to do this with one function
	public static boolean descriptionHasActiveState(Description d, ACTIVE_STATE a) {
		boolean hasActiveState = false;
		if (a.equals(ACTIVE_STATE.BOTH) ||
			(a.equals(ACTIVE_STATE.ACTIVE) && d.isActive()) ||
			(a.equals(ACTIVE_STATE.INACTIVE) && !d.isActive())) {
			hasActiveState = true;
		}
		return hasActiveState;
	}

	//Merge the lang refset entries of a into b such that b obtains the 
	//union of the two sets and preferred trumps acceptable
	public static boolean mergeLangRefsetEntries(Description a,
			Description b) {
		boolean changesMade = false;
		for (LangRefsetEntry la : a.getLangRefsetEntries(ACTIVE_STATE.ACTIVE)) {
			boolean bHasThis = false;
			//First check for existing active entries.  If they're not found, search inactive
			for (LangRefsetEntry lb : b.getLangRefsetEntries(ACTIVE_STATE.ACTIVE)) {
				if (lb.getRefsetId().equals(la.getRefsetId())) {
					bHasThis = true;
					if (!lb.getAcceptabilityId().equals(la.getAcceptabilityId())) {
						if (la.getAcceptabilityId().equals(PREFERRED_TERM)) {
							lb.setAcceptabilityId(PREFERRED_TERM);
							lb.setEffectiveTime(null);
							changesMade = true;
						}
					}
				}
			}
			
			if (!bHasThis) {
				for (LangRefsetEntry lb : b.getLangRefsetEntries(ACTIVE_STATE.INACTIVE)) {
					if (lb.getRefsetId().equals(la.getRefsetId())) {
						bHasThis = true;
						lb.setActive(true);
						changesMade = true;
						lb.setEffectiveTime(null);
						//As well as activating, are we also promoting to preferred?
						if (!lb.getAcceptabilityId().equals(la.getAcceptabilityId())) {
							if (la.getAcceptabilityId().equals(PREFERRED_TERM)) {
								lb.setAcceptabilityId(PREFERRED_TERM);
							}
						}
					}
				}
			}
			
			//If we didn't find any existing lang refset for this dialect then copy it from "a"
			if (!bHasThis) {
				b.getLangRefsetEntries().add(la.clone(b.getDescriptionId()));
				changesMade = true;
			}
		}
		return changesMade;
	}

	public static String translateCharacteristicType(
			CHARACTERISTIC_TYPE characteristicType) {
		switch (characteristicType) {
			case STATED_RELATIONSHIP : return SCTID_STATED_RELATIONSHIP;
			case INFERRED_RELATIONSHIP : return SCTID_INFERRED_RELATIONSHIP;
			case QUALIFYING_RELATIONSHIP : return SCTID_QUALIFYING_RELATIONSHIP;
			case ADDITIONAL_RELATIONSHIP : return SCTID_ADDITIONAL_RELATIONSHIP;
			default : return "";
		}
	}

	public static String translateModifier(MODIFER modifier) {
		switch (modifier) {
			case EXISTENTIAL : return SCTID_EXISTENTIAL_MODIFIER;
			case UNIVERSAL : return SCTID_UNIVERSAL_MODIFIER;
			default : return "";
		}
	}
	

	public static boolean translateActive(ACTIVE_STATE active) throws TermServerScriptException {
		switch (active) {
			case ACTIVE : return true;
			case INACTIVE : return false;
			default: throw new TermServerScriptException("Unable to translate " + active + " into boolean state");
		}
	}

}
