package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.template.AncestorsCache;
import org.ihtsdo.termserver.scripting.template.DescendentsCache;

public class SnomedUtils implements RF2Constants {
	
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
	
	public static String toString(Map<String, Acceptability> acceptabilityMap) {
		if (acceptabilityMap == null) {
			return "";
		}
		try {
			String US = "N";
			String GB = "N";
			if (acceptabilityMap.containsKey(US_ENG_LANG_REFSET)) {
				US = translateAcceptability(acceptabilityMap.get(US_ENG_LANG_REFSET));
			}
			
			if (acceptabilityMap.containsKey(GB_ENG_LANG_REFSET)) {
				GB = translateAcceptability(acceptabilityMap.get(GB_ENG_LANG_REFSET));
			}
			
			return "US: " + US + ", GB: " + GB;
		} catch (TermServerScriptException e) {
			System.out.println("Failed to convert acceptability map to string: " + e);
		}
		return "";
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

	public static Map<String, Acceptability> createAcceptabilityMap(Acceptability acceptability, String[] dialects) {
		Map<String, Acceptability> aMap = new HashMap<String, Acceptability>();
		for (String dialect : dialects) {
			aMap.put(dialect, acceptability);
		}
		return aMap;
	}
	
	public static Map<String, Acceptability> createPreferredAcceptableMap(String preferredRefset, String acceptableRefset) {
		Map<String, Acceptability> aMap = new HashMap<String, Acceptability>();
		aMap.put(preferredRefset, Acceptability.PREFERRED);
		aMap.put(acceptableRefset, Acceptability.ACCEPTABLE);
		return aMap;
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
	
	public static Map<String, Acceptability> mergeAcceptabilityMap (Description left,Description right) {
		return mergeAcceptabilityMap(left.getAcceptabilityMap(), right.getAcceptabilityMap());
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
	
	public static String initialCapitalOnly (String str) {
		if (str == null || str.isEmpty() || str.length() < 2) {
			return str;
		}
		return str.substring(0, 1).toUpperCase() + str.substring(1).toLowerCase();
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
	
	public static File createArchive(File dirToZip) throws TermServerScriptException {
		File outputFile;
		try {
			// The zip filename will be the name of the first thing in the zip location
			// ie in this case the directory SnomedCT_RF1Release_INT_20150731
			String zipFileName = dirToZip.listFiles()[0].getName() + ".zip";
			int fileNameModifier = 1;
			while (new File(zipFileName).exists()) {
				zipFileName = dirToZip.listFiles()[0].getName() + "_" + fileNameModifier++ + ".zip";
			}
			outputFile = new File(zipFileName);
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputFile));
			String rootLocation = dirToZip.getAbsolutePath() + File.separator;
			TermServerScript.info("Creating archive : " + zipFileName + " from files found in " + rootLocation);
			addDir(rootLocation, dirToZip, out);
			out.close();
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to create archive from " + dirToZip, e);
		} finally {
			try {
				FileUtils.deleteDirectory(dirToZip);
			} catch (IOException e) {}
		}
		return outputFile;
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
	
	public static String translateDefnStatusToSctid(DefinitionStatus defn) {
		switch (defn) {
			case PRIMITIVE: return SCTID_PRIMITIVE;
			case FULLY_DEFINED: return SCTID_FULLY_DEFINED;
			default:
		}
		return null;
	}
	
	public static String translateDefnStatus(DefinitionStatus defn) {
		switch (defn) {
			case PRIMITIVE: return "P";
			case FULLY_DEFINED: return "SD";
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
	
	public static HistoricalAssociation translateHistoricalAssociation(String assocSctId) {
		switch (assocSctId) {
			case SCTID_HIST_WAS_A_REFSETID: return HistoricalAssociation.WAS_A;
			case SCTID_HIST_REPLACED_BY_REFSETID : return HistoricalAssociation.REPLACED_BY;
			case SCTID_HIST_SAME_AS_REFSETID : return HistoricalAssociation.SAME_AS;
			case SCTID_HIST_MOVED_TO_REFSETID : return HistoricalAssociation.MOVED_TO;
			case SCTID_HIST_POSS_EQUIV_REFSETID : return HistoricalAssociation.POSS_EQUIV_TO;
			case SCTID_HIST_ALTERNATIVE_ASSOC_REFSETID : return HistoricalAssociation.ALTERNATIVE;
			default: throw new IllegalArgumentException("Unrecognised historical association indicator value " + assocSctId);
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
	
	public static boolean isConceptType(Concept c, ConceptType conceptType) throws TermServerScriptException {
		//Does this concept know it's type?  Assign if not
		if (c.getConceptType() == null) {
			populateConceptType(c);
		}
		return c.getConceptType().equals(conceptType);
	}
	
	public static boolean isConceptType(Concept c, ConceptType[] conceptTypes) throws TermServerScriptException {
		//Does this concept know it's type?  Assign if not
		if (c.getConceptType() == null) {
			populateConceptType(c);
		}
		for (ConceptType conceptType : conceptTypes) {
			if (c.getConceptType().equals(conceptType)) {
				return true;
			}
		}
		return false;
	}

	public static void populateConceptType(Concept c) throws TermServerScriptException {
		if (c.getFsn() == null) {
			determineConceptTypeFromAttributes(c, CharacteristicType.STATED_RELATIONSHIP);
		} else {
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			boolean isOnly = isOnly(c);
			switch (semTag) {
				case DrugUtils.MP : c.setConceptType(isOnly ? ConceptType.MEDICINAL_PRODUCT_ONLY : ConceptType.MEDICINAL_PRODUCT);
											break;
				case DrugUtils.MPF : c.setConceptType(isOnly ? ConceptType.MEDICINAL_PRODUCT_FORM_ONLY : ConceptType.MEDICINAL_PRODUCT_FORM);
											break;
				case DrugUtils.CD : c.setConceptType(ConceptType.CLINICAL_DRUG);
											break;
				case DrugUtils.PRODUCT : c.setConceptType(ConceptType.PRODUCT);
											checkForGroupers(c);  //May further refine the concept type
											break;
				default : c.setConceptType(ConceptType.UNKNOWN);
			}
		}
	}
	
	private static void checkForGroupers(Concept c) throws TermServerScriptException {
		GraphLoader gl = GraphLoader.getGraphLoader();
		
		//We're only going to consider sufficiently defined concepts here.
		if (!c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			return;
		}
		
		//Get the local cached copy of the concept so that we have the fully hierarchy tree populated
		Concept cCached = gl.getConcept(c.getConceptId());
		
		Concept dispositions = gl.getConcept("766779001 |Medicinal product categorized by disposition (product)|");
		Concept structures = gl.getConcept("763760008 |Medicinal product categorized by structure (product)|");
		boolean isStructure = false;
		boolean isDisposition = false;
		
		if (cCached.getAncestors(NOT_SET).contains(structures)) {
			isStructure = true;
		}
		
		if (cCached.getAncestors(NOT_SET).contains(dispositions)) {
			isDisposition = true;
		}
		
		if (isStructure && isDisposition) {
			c.setConceptType(ConceptType.STRUCTURE_AND_DISPOSITION_GROUPER);
		} else if (isStructure) {
			c.setConceptType(ConceptType.STRUCTURAL_GROUPER);
		} else if (isDisposition) {
			c.setConceptType(ConceptType.DISPOSITION_GROUPER);
		}
	}

	//Concepts with a base active ingredient count are "Only"
	private static boolean isOnly(Concept c) {
		return c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() > 0;
	}

	private static void determineConceptTypeFromAttributes(Concept c, CharacteristicType charType) {
		try {
			//Do we have ingredients?  We're at least an MP
			if (getTargets(c, new Concept[] { HAS_ACTIVE_INGRED, HAS_PRECISE_INGRED }, charType).size() > 0) {
				c.setConceptType(ConceptType.MEDICINAL_PRODUCT);
				//Do we also have dose form?  If so, MPF
				if (getTargets(c, new Concept[] { HAS_MANUFACTURED_DOSE_FORM }, charType).size() > 0) {
					c.setConceptType(ConceptType.MEDICINAL_PRODUCT_FORM);
					//And if we have strength, CD
					if (getTargets(c, new Concept[] { HAS_CONC_STRENGTH_DENOM_UNIT, HAS_PRES_STRENGTH_UNIT }, charType).size() > 0) {
						c.setConceptType(ConceptType.CLINICAL_DRUG);
					}
				}
			}
		} catch (Exception e) {
			TermServerScript.warn("Unable to determine concept type of " + c + " due to " + e);
		}
	}

	//Return a set of groups where there is an active non-isa relationship
	public static Set<Integer> getActiveGroups(Concept c) {
		Set<Integer> activeGroups = new HashSet<>();
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				activeGroups.add((int)r.getGroupId());
			}
		}
		return activeGroups;
	}
	
	public static int getFirstFreeGroup(Concept c) {
		Set<Integer> activeGroups = getActiveGroups(c);
		for (int i = 1; ; i++) {
			if (!activeGroups.contains(i)) {
				return i;
			}
		}
	}
	
	public static boolean termAlreadyExists(Concept concept, String newTerm) {
		return termAlreadyExists(concept, newTerm, ActiveState.BOTH);
	}

	public static boolean termAlreadyExists(Concept concept, String newTerm, ActiveState activeState) {
		boolean termAlreadyExists = false;
		for (Description description : concept.getDescriptions(activeState)) {
			if (description.getTerm() != null && description.getTerm().equals(newTerm)) {
				termAlreadyExists = true;
			}
		}
		return termAlreadyExists;
	}
	
	public static Map<String, Acceptability> createAcceptabilityMap(AcceptabilityMode acceptabilityMode) {
		Map<String, Acceptability> aMap = new HashMap<String, Acceptability>();
		//Note that when a term is preferred in one dialect, we'll make it acceptable in the other
		switch (acceptabilityMode) {
			case PREFERRED_BOTH :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.PREFERRED);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.PREFERRED);
				break;
			case PREFERRED_US :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.PREFERRED);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				break;
			case PREFERRED_GB :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.PREFERRED);
				break;
			case ACCEPTABLE_BOTH :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				break;
			case ACCEPTABLE_US :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				break;
			case ACCEPTABLE_GB :
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				break;
		}
		return aMap;
	}

	public static String getPT(String sctId) throws TermServerScriptException {
		return GraphLoader.getGraphLoader().getConcept(sctId).getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
	}

	public static Concept createConcept(String term, String semTag, Concept parent) {
		Concept newConcept = Concept.withDefaults(null);
		addDefaultTerms(newConcept, term, semTag);
		if (parent != null) {
			Relationship parentRel = new Relationship (null, IS_A, parent, UNGROUPED);
			newConcept.addRelationship(parentRel);
		}
		return newConcept;
	}

	public static Concept createConcept(String sctId, String fsn) {
		Concept newConcept = Concept.withDefaults(sctId);
		String[] parts = deconstructFSN(fsn);
		addDefaultTerms(newConcept, parts[0], parts[1]);
		return newConcept;
	}
	
	
	private static void addDefaultTerms(Concept c, String term, String semTag) {
		Description fsn = Description.withDefaults(term == null? null : term + " " + semTag, DescriptionType.FSN, Acceptability.PREFERRED);
		c.addDescription(fsn);
		
		Description pt = Description.withDefaults(term, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		c.addDescription(pt, true);  //Allow duplication - we might have a null term if we don't know enough to create one yet.
	}
	
	//Where we have multiple potential responses eg concentration or presentation strength, return the first one found given the 
	//order specified by the array
	public static Concept getTarget(Concept c, Concept[] types, int groupId, CharacteristicType charType) throws TermServerScriptException {
		for (Concept type : types) {
			List<Relationship> rels = c.getRelationships(charType, type, groupId);
			if (rels.size() > 1) {
				TermServerScript.warn(c + " has multiple " + type + " in group " + groupId);
			} else if (rels.size() == 1) {
				//This might not be the full concept, so recover it fully from our loaded cache
				return GraphLoader.getGraphLoader().getConcept(rels.get(0).getTarget().getConceptId());
			}
		}
		return null;
	}
	
	public static Set<Concept> getTargets(Concept c, Concept[] types, CharacteristicType charType) throws TermServerScriptException {
		Set<Concept> targets = new HashSet<>();
		for (Concept type : types) {
			List<Relationship> rels = c.getRelationships(charType, type, ActiveState.ACTIVE);
			targets.addAll(rels.stream().map(r -> r.getTarget()).collect(Collectors.toSet()));
		}
		return targets;
	}
	
	public static String getModel(Concept c, CharacteristicType charType) {
		String model = "";
		boolean isFirst = true;
		for (RelationshipGroup g : c.getRelationshipGroups(charType)) {
			if (!isFirst) {
				model += ", \n";
			} else isFirst = false;
			model += g;
		}
		//Split into separate lines so we can see better
		model = model.replaceAll ("\\{", "\\{  ").replaceAll("\\,", "\\,\n   ");
		return model;
	}
	
	public static Integer countAttributes(Concept c) {
		int attributeCount = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributeCount++;
			}
		}
		return attributeCount;
	}

	/**
	 * @return true if the concept has the specified type as an attribute
	 */
	public static boolean hasType(CharacteristicType charType, Concept c, Concept type) {
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			if (r.getType().equals(type)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the highest concept reached before hitting "end"
	 */
	public static Concept getHighestAncestorBefore(Concept start, Concept end) {
		Set<Concept> topLevelAncestors = new HashSet<>();
		for (Concept parent : start.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (parent.equals(end)) {
				return start;
			} else if (parent.equals(ROOT_CONCEPT)) {
				throw new IllegalStateException(start + " reached ROOT before finding " + end);
			} else {
				topLevelAncestors.add(getHighestAncestorBefore(parent, end));
			}
		}
		
		if (topLevelAncestors.size() > 1) {
			throw new IllegalArgumentException(start + " has multiple ancestors immediately before " + end);
		} else if (topLevelAncestors.isEmpty()) {
			throw new IllegalArgumentException("Failed to find ancestors of " + start + " before " + end);
		}
		
		return topLevelAncestors.iterator().next();
	}

	/**
	 * @return true if the attribute types/values (including IS A) of concept a are more specific than b
	 */
	public static boolean hasMoreSpecificModel(Concept a, Concept b, AncestorsCache cache) {
		boolean moreSpecificAttributeDetected = false;
		//Work through all the attributes of a and see if the ancestors match an attribute in b
		nextRel:
		for (Relationship r : a.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			//Filter for matching types (same or less specific) and values (same or less specific)
			//If the relationship is not identical, then it's more specific
			//TODO Strictly we should consider the RELS in equivalent groups
			List<Relationship> matchingRels = b.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)
												.stream()
												.filter(br -> cache.getAncestorsOrSelfSafely(r.getType()).contains(br.getType()))
												.filter(br -> cache.getAncestorsOrSelfSafely(r.getTarget()).contains(br.getTarget()))
												.collect(Collectors.toList());
			//If there are no matching rels, then these concept models are disjoint
			if (matchingRels.size() == 0) {
				return false;
			}
			//If we have an exact match, move on to the next relationship.  Otherwise, we've
			//found a more specific attribute
			for (Relationship thisMatch : matchingRels) {
				if (thisMatch.getType().equals(r.getType()) &&
						thisMatch.getTarget().equals(r.getTarget())) {
					continue nextRel;
				}
			}
			moreSpecificAttributeDetected = true;
		}
		return moreSpecificAttributeDetected;
	}
	
	//Return true if a is more specific than b.   False if they're the same
	public static boolean isMoreSpecific(Relationship a, Relationship b, AncestorsCache cache) throws TermServerScriptException {
		boolean sameType = false;
		boolean moreSpecificType = false;
		//Compare Types
		if (a.getType().equals(b.getType())) {
			sameType = true;
		} else if (cache.getAncestors(a.getType()).contains(b.getType())) {
			moreSpecificType = true;
		} else {
			return false;
		}
		
		//If type is same or more specific, how does value compare?
		boolean sameValue = false;
		boolean moreSpecificValue = false;
		if (a.getTarget().equals(b.getTarget())) {
			sameValue = true;
		} else if (cache.getAncestors(a.getTarget()).contains(b.getTarget())) {
			moreSpecificValue = true;
		} else {
			return false;
		}
		
		//If they're exactly the same, then it's not MORE specific
		if (sameType && sameValue) {
			return false;
		} else if (moreSpecificType || moreSpecificValue) {
			return true;
		}
		return false;
	}

	/*
	 * Get active relationships which are the same as, or descendants of the stated types and values
	 */
	public static List<Relationship> getSubsumedRelationships(Concept c, Concept type, Concept target,
			CharacteristicType charType, AncestorsCache cache) throws TermServerScriptException {
		List<Relationship> matchedRelationships = new ArrayList<>();
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			//Does this type have the desired type as self or ancestor?  Need local copies to do subsumption testing
			Concept rType = GraphLoader.getGraphLoader().getConcept(r.getType().getConceptId());
			Concept rTarget = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			if (cache.getAncestorsOrSelf(rType).contains(type)) {
				if (cache.getAncestorsOrSelf(rTarget).contains(target)) {
					matchedRelationships.add(r);
				}
			}
		}
		return matchedRelationships;
	}

	/*
	 * @return true if r1 and r2 can be found grouped together in the specified characteristic type
	 */
	public static boolean isGroupedWith(Relationship r1, Relationship r2, Concept c, CharacteristicType charType) {
		if (r1.equalsTypeValue(r2)) {
			throw new IllegalArgumentException("Cannot answer if " + r1 + " is grouped with itself");
		}
		
		for (RelationshipGroup group : c.getRelationshipGroups(charType)) {
			boolean foundFirst = false;
			for (Relationship r : group.getRelationships()) {
				if (r.equalsTypeValue(r1) || r.equalsTypeValue(r2)) {
					if (foundFirst) { //already seen one
						return true;
					} else {
						foundFirst = true;
					}
				}
			}
		}
		return false;
	}

	public static Set<RelationshipGroup> appearsInGroups(Concept c, Relationship findMe, CharacteristicType charType) {
		//Return all groups containing r's type and value
		Set<RelationshipGroup> groups = new HashSet<>();
		
		nextGroup:
		for (RelationshipGroup group : c.getRelationshipGroups(charType)) {
			for (Relationship r : group.getRelationships()) {
				if (r.equalsTypeValue(findMe)) {
					groups.add(group);
					continue nextGroup;
				}
			}
		}
		return groups;
	}
	
	public static void removeRedundancies(Set<Concept> concepts) throws TermServerScriptException {
		Set<Concept> redundant = new HashSet<>();
		DescendentsCache cache = GraphLoader.getGraphLoader().getDescendantsCache();
		//For each concept, it is redundant if any of it's descendants are also present
		for (Concept concept : concepts) {
			Set<Concept> descendants = new HashSet<>(cache.getDescendents(concept));
			descendants.retainAll(concepts);
			if (descendants.size() > 0) {
				redundant.add(concept);
			}
		}
		concepts.removeAll(redundant);
	}

}
