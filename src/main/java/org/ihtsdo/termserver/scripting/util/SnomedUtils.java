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
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CaseSignificance;

public class SnomedUtils implements RF2Constants{
	
	private static VerhoeffCheckDigit verhoeffCheck = new VerhoeffCheckDigit();

	public static String isValid(String sctId, PartitionIdentifier partitionIdentifier) {
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

	public static void isValid(String sctId, PartitionIdentifier partitionIdentifier,
			boolean errorIfInvalid) throws TermServerScriptException {
		String errMsg = isValid(sctId,partitionIdentifier);
		if (errorIfInvalid && errMsg != null) {
			throw new TermServerScriptException(errMsg);
		}
	}

	public static String[] deconstructFSN(String fsn) {
		String[] elements = new String[2];
		int cutPoint = fsn.lastIndexOf(SEMANTIC_TAG_START);
		if (cutPoint == -1) {
			System.out.println("'" + fsn + "' does not contain a semantic tag!");
			elements[0] = fsn;
		} else {
			elements[0] = fsn.substring(0, cutPoint).trim();
			elements[1] = fsn.substring(cutPoint);
		}
		return elements;
	}
	
	public static String toString(Map<String, Acceptability> AcceptabilityMap) throws TermServerScriptException {
		String US = "N";
		String GB = "N";
		if (AcceptabilityMap.containsKey(US_ENG_LANG_REFSET)) {
			US = translateAcceptability(AcceptabilityMap.get(US_ENG_LANG_REFSET));
		}
		
		if (AcceptabilityMap.containsKey(GB_ENG_LANG_REFSET)) {
			GB = translateAcceptability(AcceptabilityMap.get(GB_ENG_LANG_REFSET));
		}
		
		return "US: " + US + ", GB: " + GB;
	}
	
	public static String translateAcceptability (Acceptability a) throws TermServerScriptException {
		if (a.equals(Acceptability.PREFERRED)) {
			return "P";
		}
		
		if (a.equals(Acceptability.ACCEPTABLE)) {
			return "A";
		}
		throw new TermServerScriptException("Unable to translate Acceptability " + a);
	}
	
	public static Acceptability translateAcceptability(String sctid) throws TermServerScriptException {
		if (sctid.equals(SCTID_ACCEPTABLE_TERM)) {
			return Acceptability.ACCEPTABLE;
		}
		
		if (sctid.equals(SCTID_PREFERRED_TERM)) {
			return Acceptability.PREFERRED;
		} 
		
		throw new TermServerScriptException("Unable to translate Acceptability '" + sctid + "'");
	}
	
	public static String[] translateLangRefset(Description d) throws TermServerScriptException {
		String[] acceptabilities = new String[] {"N","N"};
		for (LangRefsetEntry entry : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
			int idx = entry.getRefsetId().equals(US_ENG_LANG_REFSET)?0:1;
			Acceptability a = translateAcceptability(entry.getAcceptabilityId());
			switch (a) {
				case ACCEPTABLE : acceptabilities[idx] = "A";
									break;
				case PREFERRED : acceptabilities[idx] = "P";
									break;
				default :
					throw new TermServerScriptException("Unable to translate Acceptability '" + a + "'");
			}
		}
		return acceptabilities;
	}

	public static String substitute(String str, Map<String, String> wordSubstitution) {
		//Replace any instances of the map key with the corresponding value
		for (Map.Entry<String, String> substitution : wordSubstitution.entrySet()) {
			//Check for the word existing in lower case, and then replace with same setting
			if (str.toLowerCase().contains(substitution.getKey().toLowerCase())) {
				//Did we match as is, do direct replacement if so
				if (str.contains(substitution.getKey())) {
					str = str.replace(substitution.getKey(), substitution.getValue());
				} else {
					//Otherwise, we should capitalize
					String find = SnomedUtils.capitalize(substitution.getKey());
					String subst = SnomedUtils.capitalize(substitution.getValue());
					str = str.replace(find, subst);
				}
			}
		}
		return str;
	}
	
	/**
	 * Merge two Acceptability maps such that a PREFERRED overrides an ACCEPTABLE
	 * AND ACCEPTABLE overrides not acceptable.
	 */
	public static Map<String, Acceptability> mergeAcceptabilityMap (Map<String, Acceptability> left, Map<String, Acceptability> right) {
		Set<String> dialects = new HashSet<String>();
		dialects.addAll(left.keySet());
		dialects.addAll(right.keySet());
		Map<String, Acceptability> merged = new HashMap<String, Acceptability>();
		
		for (String thisDialect : dialects) {
			if (!left.containsKey(thisDialect) && right.containsKey(thisDialect)) {
				merged.put(thisDialect, right.get(thisDialect));
			} 
			if (!right.containsKey(thisDialect) && left.containsKey(thisDialect)) {
				merged.put(thisDialect, left.get(thisDialect));
			} 
			if (left.containsKey(thisDialect) && right.containsKey(thisDialect)) {
				if (left.get(thisDialect).equals(Acceptability.PREFERRED) || right.get(thisDialect).equals(Acceptability.PREFERRED)) {
					merged.put(thisDialect, Acceptability.PREFERRED);
				} else {
					merged.put(thisDialect, Acceptability.ACCEPTABLE);
				}
			}
		}
		return merged;
	}
	
	/**
	 * 2 points for preferred, 1 point for acceptable
	 */
	public static int accetabilityScore (Map<String, Acceptability> AcceptabilityMap) {
		int score = 0;
		for (Acceptability a : AcceptabilityMap.values()) {
			if (a.equals(Acceptability.PREFERRED)) {
				score += 2;
			} else if (a.equals(Acceptability.ACCEPTABLE)) {
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
	
	public static String initialCapitalOnly (String str) {
		if (str == null || str.isEmpty() || str.length() < 2) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
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

	public static String translateDescType(DescriptionType type) throws TermServerScriptException {
		switch (type) {
			case FSN : return FSN;
			case SYNONYM : return SYN;
			case TEXT_DEFINITION : return DEF;
		}
		throw new TermServerScriptException("Unable to translate description type " + type);
	}

	public static DescriptionType translateDescType(String descTypeId) throws TermServerScriptException {
		switch (descTypeId) {
			case FSN : return DescriptionType.FSN;
			case SYN : return DescriptionType.SYNONYM;
			case DEF : return DescriptionType.TEXT_DEFINITION; 
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
	
	public static boolean conceptHasActiveState(Concept c, ActiveState a) {
		boolean hasActiveState = false;
		if (a.equals(ActiveState.BOTH) ||
			(a.equals(ActiveState.ACTIVE) && c.isActive()) ||
			(a.equals(ActiveState.INACTIVE) && !c.isActive())) {
			hasActiveState = true;
		}
		return hasActiveState;
	}
	//TODO See if the JSON will allow us to create the abstract "Component" which allows us to do this with one function
	public static boolean descriptionHasActiveState(Description d, ActiveState a) {
		boolean hasActiveState = false;
		if (a.equals(ActiveState.BOTH) ||
			(a.equals(ActiveState.ACTIVE) && d.isActive()) ||
			(a.equals(ActiveState.INACTIVE) && !d.isActive())) {
			hasActiveState = true;
		}
		return hasActiveState;
	}

	//Merge the lang refset entries of a into b such that b obtains the 
	//union of the two sets and preferred trumps acceptable
	public static boolean mergeLangRefsetEntries(Description a,
			Description b) {
		boolean changesMade = false;
		for (LangRefsetEntry la : a.getLangRefsetEntries(ActiveState.ACTIVE)) {
			boolean bHasThis = false;
			//First check for existing active entries.  If they're not found, search inactive
			for (LangRefsetEntry lb : b.getLangRefsetEntries(ActiveState.ACTIVE)) {
				if (lb.getRefsetId().equals(la.getRefsetId())) {
					bHasThis = true;
					if (!lb.getAcceptabilityId().equals(la.getAcceptabilityId())) {
						if (la.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
							lb.setAcceptabilityId(SCTID_PREFERRED_TERM);
							lb.setEffectiveTime(null);
							changesMade = true;
						}
					}
				}
			}
			
			if (!bHasThis) {
				for (LangRefsetEntry lb : b.getLangRefsetEntries(ActiveState.INACTIVE)) {
					if (lb.getRefsetId().equals(la.getRefsetId())) {
						bHasThis = true;
						lb.setActive(true);
						changesMade = true;
						lb.setEffectiveTime(null);
						//As well as activating, are we also promoting to preferred?
						if (!lb.getAcceptabilityId().equals(la.getAcceptabilityId())) {
							if (la.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
								lb.setAcceptabilityId(SCTID_PREFERRED_TERM);
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
			CharacteristicType characteristicType) {
		switch (characteristicType) {
			case STATED_RELATIONSHIP : return SCTID_STATED_RELATIONSHIP;
			case INFERRED_RELATIONSHIP : return SCTID_INFERRED_RELATIONSHIP;
			case QUALIFYING_RELATIONSHIP : return SCTID_QUALIFYING_RELATIONSHIP;
			case ADDITIONAL_RELATIONSHIP : return SCTID_ADDITIONAL_RELATIONSHIP;
			default : return "";
		}
	}

	public static String translateModifier(Modifier modifier) {
		switch (modifier) {
			case UNIVERSAL : return SCTID_UNIVERSAL_MODIFIER;
			case EXISTENTIAL :
			default : return SCTID_EXISTENTIAL_MODIFIER;
		}
	}
	
	public static Modifier translateModifier(String modifierSCTID) {
		switch (modifierSCTID) {
			case SCTID_UNIVERSAL_MODIFIER : return Modifier.UNIVERSAL;
			case SCTID_EXISTENTIAL_MODIFIER  :
			default : return Modifier.EXISTENTIAL;
		}
	}
	

	public static DefinitionStatus translateDefnStatus(String defnStatusSctId) {
		switch (defnStatusSctId) {
			case SCTID_PRIMITIVE : return DefinitionStatus.PRIMITIVE;
			case SCTID_FULLY_DEFINED: return DefinitionStatus.FULLY_DEFINED;
			default:
		}
		return null;
	}
	
	public static String translateDefnStatus(DefinitionStatus defn) {
		switch (defn) {
			case PRIMITIVE: return SCTID_PRIMITIVE;
			case FULLY_DEFINED: return SCTID_FULLY_DEFINED;
			default:
		}
		return null;
	}
	

	public static boolean translateActive(ActiveState active) throws TermServerScriptException {
		switch (active) {
			case ACTIVE : return true;
			case INACTIVE : return false;
			default: throw new TermServerScriptException("Unable to translate " + active + " into boolean state");
		}
	}

	public static ChangeStatus promoteLangRefsetEntries(Description d, String[] dialects) throws TermServerScriptException {
		//For each dialect check if we have an active existing entry that could be modified, 
		//inactive entry that could be activated and/or modified
		//or add a new entry
		ChangeStatus changeStatus = ChangeStatus.NO_CHANGE_MADE;
		for (String thisDialectSctid : dialects) {
			//Do we have an active entry we could change?
			List<LangRefsetEntry> langRefsetEntries = d.getLangRefsetEntries(ActiveState.ACTIVE, thisDialectSctid);
			if (langRefsetEntries.size() >1) {
				throw new TermServerScriptException("Description has more than one active langrefset entry for a given dialect: " + d);
			}
			changeStatus = promoteLangRefsetEntry(d, langRefsetEntries);
			//Have we achieved a PT?  Activate inactive term if not.
			if (changeStatus.equals(ChangeStatus.NO_CHANGE_MADE)) {
				changeStatus = promoteLangRefsetEntry(d, d.getLangRefsetEntries(ActiveState.INACTIVE, thisDialectSctid));
			}
			//Still no?  Create a new entry
			if (changeStatus.equals(ChangeStatus.NO_CHANGE_MADE)) {
				createLangRefsetEntry(d,thisDialectSctid, SCTID_PREFERRED_TERM);
				changeStatus = ChangeStatus.CHANGE_MADE;
			}
		}
		return changeStatus;
	}

	private static void createLangRefsetEntry(Description d,
			String thisDialectSctid, String AcceptabilitySctid) {
		LangRefsetEntry l = new LangRefsetEntry();
		l.setId(UUID.randomUUID().toString());
		l.setActive(true);
		l.setModuleId(d.getModuleId());
		l.setRefsetId(thisDialectSctid);
		l.setReferencedComponentId(d.getDescriptionId());
		l.setDirty();
		d.getLangRefsetEntries().add(l);
	}

	private static ChangeStatus promoteLangRefsetEntry(Description d, List<LangRefsetEntry> langRefsetEntries) throws TermServerScriptException {
		ChangeStatus changeStatus = ChangeStatus.NO_CHANGE_MADE;
		for (LangRefsetEntry thisDialectEntry : langRefsetEntries) {
			if (!thisDialectEntry.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
				thisDialectEntry.setAcceptabilityId(SCTID_PREFERRED_TERM);
				thisDialectEntry.setEffectiveTime(null);
				thisDialectEntry.setActive(true);
				thisDialectEntry.isDirty();
				changeStatus = ChangeStatus.CHANGE_MADE;
				break;
			} else {
				if (!thisDialectEntry.isActive()) {
					thisDialectEntry.setEffectiveTime(null);
					thisDialectEntry.setActive(true);
					changeStatus = ChangeStatus.CHANGE_MADE;
					break;
				} else {
					//dialect is preferred and is alreayd active so no change needed. Say this so that we don't try anything else
					changeStatus = ChangeStatus.CHANGE_NOT_REQUIRED;
				}
			}
		}
		return changeStatus;
	}
	
	public static CaseSignificance translateCaseSignificance(String caseSignificanceIndicatorStr) throws TermServerScriptException {
		switch (caseSignificanceIndicatorStr) {
			case "ENTIRE_TERM_CASE_SENSITIVE" : return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			case "CASE_INSENSITIVE" : return CaseSignificance.CASE_INSENSITIVE;
			case "INITIAL_CHARACTER_CASE_INSENSITIVE" : return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificanceIndicatorStr);
	}
	
	public static CaseSignificance translateCaseSignificanceFromString(String caseSignificanceIndicatorStr) throws TermServerScriptException {
		switch (caseSignificanceIndicatorStr) {
			case "CS" : return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			case "ci" : return CaseSignificance.CASE_INSENSITIVE;
			case "cI" : return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificanceIndicatorStr);
	}
	
	public static String translateCaseSignificanceToSctId(String caseSignificanceIndicator) throws TermServerScriptException {
		switch (caseSignificanceIndicator) {
			case "CS" : return SCTID_ENTIRE_TERM_CASE_SENSITIVE;
			case "ci" : return SCTID_ENTIRE_TERM_CASE_INSENSITIVE;
			case "cl" :
			case "cI" : return SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE;
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificanceIndicator);
	}
	
	public static String translateCaseSignificanceToSctId(CaseSignificance caseSignificance) throws TermServerScriptException {
		switch (caseSignificance) {
			case ENTIRE_TERM_CASE_SENSITIVE : return SCTID_ENTIRE_TERM_CASE_SENSITIVE;
			case CASE_INSENSITIVE : return SCTID_ENTIRE_TERM_CASE_INSENSITIVE;
			case INITIAL_CHARACTER_CASE_INSENSITIVE : return SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE;
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificance);
	}

	public static String translateCaseSignificanceFromSctId(
			String caseSignificanceSctId) throws TermServerScriptException {
		switch (caseSignificanceSctId) {
			case SCTID_ENTIRE_TERM_CASE_SENSITIVE: return "CS";
			case SCTID_ENTIRE_TERM_CASE_INSENSITIVE: return "ci";
			case SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE : return "cI";
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificanceSctId);
	}
	
	public static String translateCaseSignificanceFromEnum(
			CaseSignificance caseSignificance) throws TermServerScriptException {
		switch (caseSignificance) {
			case ENTIRE_TERM_CASE_SENSITIVE: return "CS";
			case CASE_INSENSITIVE: return "ci";
			case INITIAL_CHARACTER_CASE_INSENSITIVE : return "cI";
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificance);
	}
	
	public static CaseSignificance translateCaseSignificanceToEnum(
			String caseSignificanceSctId) throws TermServerScriptException {
		switch (caseSignificanceSctId) {
			case SCTID_ENTIRE_TERM_CASE_SENSITIVE: return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
			case SCTID_ENTIRE_TERM_CASE_INSENSITIVE: return CaseSignificance.CASE_INSENSITIVE;
			case SCTID_ONLY_INITIAL_CHAR_CASE_INSENSITIVE : return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificanceSctId);
	}

	public static void makeMachineReadable (StringBuffer hrExp) {
		int pipeIdx =  hrExp.indexOf(PIPE);
		while (pipeIdx != -1) {
			int endIdx = findEndOfTerm(hrExp, pipeIdx);
			hrExp.delete(pipeIdx, endIdx);
			pipeIdx =  hrExp.indexOf(PIPE);
		}
		remove(hrExp, SPACE_CHAR);
	}
	
	private static int findEndOfTerm(StringBuffer hrExp, int searchStart) {
		int endIdx = indexOf(hrExp, termTerminators, searchStart+1);
		//If we didn't find a terminator, cut to the end.
		if (endIdx == -1) {
			endIdx = hrExp.length();
		} else {
			//If the character found as a terminator is a pipe, then cut that too
			if (hrExp.charAt(endIdx) == PIPE_CHAR) {
				endIdx++;
			} else if (hrExp.charAt(endIdx) == ATTRIBUTE_SEPARATOR.charAt(0)) {
				//If the character is a comma, then it might be a comma inside a term so find out if the next token is a number
				if (!StringUtils.isNumericSpace(hrExp.substring(endIdx+1, endIdx+5))) {
					//OK it's a term, so find the new actual end. 
					endIdx = findEndOfTerm(hrExp, endIdx);
				}
			}
		}
		return endIdx;
	}

	static void remove (StringBuffer haystack, char needle) {
		for (int idx = 0; idx < haystack.length(); idx++) {
			if (haystack.charAt(idx) == needle) {
				haystack.deleteCharAt(idx);
				idx --;
			}
		}
	}
	
	static int indexOf (StringBuffer haystack, char[] needles, int startFrom) {
		for (int idx = startFrom; idx < haystack.length(); idx++) {
			for (char thisNeedle : needles) {
				if (haystack.charAt(idx) == thisNeedle) {
					return idx;
				}
			}
		}
		return -1;
	}

	public static InactivationIndicator translateInactivationIndicator(String indicatorSctId) {
		switch (indicatorSctId) {
			case SCTID_INACT_AMBIGUOUS: return InactivationIndicator.AMBIGUOUS;
			case SCTID_INACT_MOVED_ELSEWHERE : return InactivationIndicator.MOVED_ELSEWHERE;
			case SCTID_INACT_CONCEPT_NON_CURRENT : return InactivationIndicator.CONCEPT_NON_CURRENT;
			case SCTID_INACT_DUPLICATE : return InactivationIndicator.DUPLICATE;
			case SCTID_INACT_ERRONEOUS : return InactivationIndicator.ERRONEOUS;
			case SCTID_INACT_INAPPROPRIATE : return InactivationIndicator.INAPPROPRIATE;
			case SCTID_INACT_LIMITED : return InactivationIndicator.LIMITED;
			case SCTID_INACT_OUTDATED : return InactivationIndicator.OUTDATED; 
			case SCTID_INACT_PENDING_MOVE : return InactivationIndicator.PENDING_MOVE;
			case SCTID_INACT_NON_CONFORMANCE: return InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY;
			case SCTID_INACT_NOT_EQUIVALENT : return InactivationIndicator.NOT_EQUIVALENT;
			default: throw new IllegalArgumentException("Unrecognised inactivation indicator value " + indicatorSctId);
		}
	}
	
	public static File ensureFileExists(String fileName) throws TermServerScriptException {
		File file = new File(fileName);
		try {
			if (!file.exists()) {
				if (file.getParentFile() != null) {
					file.getParentFile().mkdirs();
				}
				file.createNewFile();
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to create file " + fileName,e);
		}
		return file;
	}
	
	public static boolean isCaseSensitive(String term) {
		String afterFirst = term.substring(1);
		boolean allLowerCase = afterFirst.equals(afterFirst.toLowerCase());
		return !allLowerCase;
	}
	
	public static CaseSignificance calculateCaseSignificance(String term) {
		//Any term that starts with a lower case letter
		//can be considered CS.   Otherwise if it is case sensitive then cI
		String firstLetter = term.substring(0, 1);
		if (firstLetter.equals(firstLetter.toLowerCase())) {
			return CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE;
		} else if (isCaseSensitive(term)) {
			return CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE;
		}
		return CaseSignificance.CASE_INSENSITIVE;
	}

}
