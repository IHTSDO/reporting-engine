package org.ihtsdo.termserver.scripting.util;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.validator.routines.checkdigit.CheckDigitException;
import org.apache.commons.validator.routines.checkdigit.VerhoeffCheckDigit;
import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component.ComponentType;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.AncestorsCache;
import org.ihtsdo.termserver.scripting.DescendantsCache;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TransitiveClosure;
import org.ihtsdo.termserver.scripting.client.TermServerClient.ExtractType;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;


public class SnomedUtils extends SnomedUtilsBase implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(SnomedUtils.class);

	private static final SimpleDateFormat EFFECTIVE_DATE_FORMAT = new SimpleDateFormat("yyyyMMdd");
	private static VerhoeffCheckDigit verhoeffCheck = new VerhoeffCheckDigit();
	
	private static HashSet<String> missingFsnReport = new HashSet<>();
	
	private static List<String> InternationalModules = Arrays.asList(INTERNATIONAL_MODULES);
	
	//Although MS Windows use backslashes in their file paths, the standard for zip archive states
	//that file separators should always be the backslash
	private static final String BWD_SLASH = "\\\\";
	private static final String FWD_SLASH = "/";
	
	// Regular expression to match numbers with at least 8 digits
	private static final String SCTID_REGEX = "\\b\\d{8,}\\b";
	
	// Create a Pattern object
	private static final Pattern sctIdPattern = Pattern.compile(SCTID_REGEX);
	
	public static String isValid(String sctId, PartitionIdentifier partitionIdentifier) {
		String errorMsg=null;
		
		if (!StringUtils.isNumeric(sctId)) {
			return "SCTID is not entirely numeric: '" + sctId + "'";
		}
		
		//Are we checking a specific partition?
		if (partitionIdentifier != null) {
			int partitionNumber = Integer.valueOf("" + sctId.charAt(sctId.length() -2));
			if (partitionNumber != partitionIdentifier.ordinal()) {
				errorMsg = sctId + " does not exist in partition " + partitionIdentifier.toString();
			}
		}
		
		if (!verhoeffCheck.isValid(sctId)) {
			errorMsg = partitionIdentifier.name() + " " + sctId + " does not exhibit a valid check digit";
		}
		return errorMsg;
	}

	public static boolean isValid(String sctId, PartitionIdentifier partitionIdentifier,
			boolean errorIfInvalid) throws TermServerScriptException {
		String errMsg = isValid(sctId,partitionIdentifier);
		if (errorIfInvalid && errMsg != null) {
			throw new TermServerScriptException(errMsg);
		}
		return errMsg == null;
	}
	
	public static String toString(Map<String, Acceptability> acceptabilityMap) {
		return LanguageHelper.toString(acceptabilityMap);
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
	
	public static String translateAcceptabilityToSCTID(Acceptability a) throws TermServerScriptException {
		if (a.equals(Acceptability.PREFERRED)) {
			return SCTID_PREFERRED_TERM;
		}
		
		if (a.equals(Acceptability.ACCEPTABLE)) {
			return SCTID_ACCEPTABLE_TERM;
		}
		throw new TermServerScriptException("Unable to translate Acceptability " + a);
	}
	
	public static Acceptability translateAcceptabilityFromChar (char a) throws TermServerScriptException {
		if (a == 'P') {
			return Acceptability.PREFERRED;
		}
		
		if (a == 'A') {
			return Acceptability.ACCEPTABLE;
		}
		
		if (a == 'N') {
			return null;
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
	
	public static Acceptability translateAcceptabilitySafely(String sctid) {
		if (sctid.equals(SCTID_ACCEPTABLE_TERM)) {
			return Acceptability.ACCEPTABLE;
		}
		
		if (sctid.equals(SCTID_PREFERRED_TERM)) {
			return Acceptability.PREFERRED;
		} 
		
		return null;
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
		Set<String> dialects = new HashSet<>();
		dialects.addAll(left.keySet());
		dialects.addAll(right.keySet());
		Map<String, Acceptability> merged = new HashMap<>();
		
		for (String thisDialect : dialects) {
			if (!left.containsKey(thisDialect) && right.containsKey(thisDialect)) {
				merged.put(thisDialect, right.get(thisDialect));
			} 
			if (!right.containsKey(thisDialect) && left.containsKey(thisDialect)) {
				merged.put(thisDialect, left.get(thisDialect));
			} 
			if (left.containsKey(thisDialect) && right.containsKey(thisDialect)) {
				if (left.get(thisDialect).equals(Acceptability.PREFERRED)
						|| right.get(thisDialect).equals(Acceptability.PREFERRED)) {
					merged.put(thisDialect, Acceptability.PREFERRED);
				} else {
					merged.put(thisDialect, Acceptability.ACCEPTABLE);
				}
			}
		}
		return merged;
	}
	
	public static void demoteAcceptabilityMap (Description d) {
		for (String refset : d.getAcceptabilityMap().keySet()) {
			d.getAcceptabilityMap().put(refset, Acceptability.ACCEPTABLE);
		}

		for (LangRefsetEntry entry : d.getLangRefsetEntries(ActiveState.ACTIVE)) {
			if (entry.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
				entry.setAcceptabilityId(SCTID_ACCEPTABLE_TERM);
				entry.setEffectiveTime(null);
				entry.setDirty();
			}
		}
	}
	
	public static Map<String, Acceptability> mergeAcceptabilityMap (Description left,Description right) {
		return mergeAcceptabilityMap(left.getAcceptabilityMap(), right.getAcceptabilityMap());
	}
		
	
	/**
	 * 2 points for preferred, 1 point for acceptable
	 */
	public static int accetabilityScore (Map<String, Acceptability> acceptabilityMap) {
		int score = 0;
		for (Acceptability a : acceptabilityMap.values()) {
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
		
		if (file == null || StringUtils.isEmpty(file.getPath())) {
			return parts;
		}
		parts[0] = file.getAbsolutePath().substring(0, file.getAbsolutePath().lastIndexOf(File.separator));
		if (file.getName().lastIndexOf(".") > 0) {
			parts[1] = file.getAbsolutePath().substring(parts[0].length() + 1, file.getAbsolutePath().lastIndexOf("."));
			parts[2] = file.getAbsolutePath().substring(file.getAbsolutePath().lastIndexOf(".") + 1);
		} else {
			parts[1] = file.getName();
		}
		
		return parts;
	}

	public static String translateDescType(DescriptionType type) {
		return switch (type) {
			case FSN -> SCTID_FSN;
			case SYNONYM -> SCTID_SYN;
			case TEXT_DEFINITION -> SCTID_DEF;
		};
	}

	public static DescriptionType translateDescType(String descTypeId) throws TermServerScriptException {
		switch (descTypeId) {
			case SCTID_FSN : return DescriptionType.FSN;
			case SCTID_SYN : return DescriptionType.SYNONYM;
			case SCTID_DEF : return DescriptionType.TEXT_DEFINITION;
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
		if (dirToZip == null) {
			throw new TermServerScriptException ("Create archive asked to zip null folder");
		}
		File outputFile;
		try {
			// The zip filename will be the name of the first thing in the zip location
			// ie in this case the directory SnomedCT_RF1Release_INT_20150731
			if (dirToZip.listFiles() == null || dirToZip.listFiles().length == 0) {
				throw new TermServerScriptException ("No files found to zip in '" + dirToZip + "'");
			}
			
			String zipFileName = dirToZip.listFiles()[0].getName() + ".zip";
			int fileNameModifier = 1;
			while (new File(zipFileName).exists()) {
				zipFileName = dirToZip.listFiles()[0].getName() + "_" + fileNameModifier++ + ".zip";
			}
			outputFile = new File(zipFileName);
			ZipOutputStream out = new ZipOutputStream(new FileOutputStream(outputFile));
			String rootLocation = dirToZip.getAbsolutePath() + File.separator;
			LOGGER.info("Creating archive : " + zipFileName + " from files found in " + rootLocation);
			addDir(rootLocation, dirToZip, out);
			out.close();
		} catch (IOException e) {
			throw new TermServerScriptException("Failed to create archive from " + dirToZip, e);
		} finally {
			try {
				FileUtils.deleteDirectory(dirToZip);
			} catch (IOException e) {}
		}
		LOGGER.info("Created archive: " + outputFile);
		
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
			String relativePath = files[i].getAbsolutePath().substring(rootLocation.length()).replaceAll(BWD_SLASH,FWD_SLASH);
			LOGGER.debug(" Adding: " + relativePath);
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
			(a.equals(ActiveState.ACTIVE) && c.isActiveSafely()) ||
			(a.equals(ActiveState.INACTIVE) && !c.isActiveSafely())) {
			hasActiveState = true;
		}
		return hasActiveState;
	}
	//TODO See if the JSON will allow us to create the abstract "Component" which allows us to do this with one function
	public static boolean descriptionHasActiveState(Description d, ActiveState a) {
		boolean hasActiveState = false;
		if (a.equals(ActiveState.BOTH) ||
			(a.equals(ActiveState.ACTIVE) && d.isActiveSafely()) ||
			(a.equals(ActiveState.INACTIVE) && !d.isActiveSafely())) {
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
				b.getLangRefsetEntries().add(la.clone(b.getDescriptionId(), false));
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
	
	public static CharacteristicType translateCharacteristicType(String characteristicTypeId) {
		switch (characteristicTypeId) {
			case SCTID_STATED_RELATIONSHIP : return CharacteristicType.STATED_RELATIONSHIP;
			case SCTID_INFERRED_RELATIONSHIP : return CharacteristicType.INFERRED_RELATIONSHIP;
			case SCTID_QUALIFYING_RELATIONSHIP : return CharacteristicType.QUALIFYING_RELATIONSHIP;
			case SCTID_ADDITIONAL_RELATIONSHIP : return CharacteristicType.ADDITIONAL_RELATIONSHIP;
			default : throw new IllegalArgumentException("Unexpected characteristicTypeId " + characteristicTypeId);
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
	
	public static DefinitionStatus translateDefnStatusStr(String defnStr) throws TermServerScriptException {
		switch (defnStr) {
			case "P": return DefinitionStatus.PRIMITIVE;
			case "FD":
			case "SD": return DefinitionStatus.FULLY_DEFINED;
			default: throw new TermServerScriptException("Unrecognised definition status :" + defnStr);
		}
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
				if (!thisDialectEntry.isActiveSafely()) {
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
	
	public static String translateCaseSignificanceFromEnum(CaseSignificance caseSignificance) throws TermServerScriptException {
		switch (caseSignificance) {
			case ENTIRE_TERM_CASE_SENSITIVE: return "CS";
			case CASE_INSENSITIVE: return "ci";
			case INITIAL_CHARACTER_CASE_INSENSITIVE : return "cI";
			default :
		}
		throw new TermServerScriptException("Do not recognise case significance indicator : " + caseSignificance);
	}

	public static String translateCaseSignificanceFromEnumSafely(CaseSignificance caseSignificance) {
		switch (caseSignificance) {
			case ENTIRE_TERM_CASE_SENSITIVE: return "CS";
			case CASE_INSENSITIVE: return "ci";
			case INITIAL_CHARACTER_CASE_INSENSITIVE : return "cI";
			default : return "??";
		}
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
			case SCTID_INACT_NOT_SEMANTICALLY_EQUIVALENT : return InactivationIndicator.NOT_SEMANTICALLY_EQUIVALENT;
			case SCTID_INACT_MEANING_OF_COMPONENT_UNKNOWN: return InactivationIndicator.MEANING_OF_COMPONENT_UNKNOWN;
			case SCTID_INACT_CLASS_DERIVED_COMPONENT : return InactivationIndicator.CLASSIFICATION_DERIVED_COMPONENT;
			case SCTID_INACT_GRAMMATICAL_DESCRIPTION_ERROR : return InactivationIndicator.GRAMMATICAL_DESCRIPTION_ERROR;
			default: throw new IllegalArgumentException("Unrecognised inactivation indicator value " + indicatorSctId);
		}
	}

	public static String translateInactivationIndicator(InactivationIndicator ii) {
		switch (ii) {
			case AMBIGUOUS : return SCTID_INACT_AMBIGUOUS;
			case MOVED_ELSEWHERE : return SCTID_INACT_MOVED_ELSEWHERE;
			case CONCEPT_NON_CURRENT : return SCTID_INACT_CONCEPT_NON_CURRENT;
			case DUPLICATE : return SCTID_INACT_DUPLICATE;
			case ERRONEOUS : return SCTID_INACT_ERRONEOUS;
			case INAPPROPRIATE : return SCTID_INACT_INAPPROPRIATE;
			case LIMITED : return SCTID_INACT_LIMITED;
			case OUTDATED : return SCTID_INACT_OUTDATED;
			case PENDING_MOVE : return SCTID_INACT_PENDING_MOVE;
			case NONCONFORMANCE_TO_EDITORIAL_POLICY : return SCTID_INACT_NON_CONFORMANCE;
			case NOT_SEMANTICALLY_EQUIVALENT : return SCTID_INACT_NOT_SEMANTICALLY_EQUIVALENT;
			case MEANING_OF_COMPONENT_UNKNOWN: return SCTID_INACT_MEANING_OF_COMPONENT_UNKNOWN;
			case CLASSIFICATION_DERIVED_COMPONENT : return SCTID_INACT_CLASS_DERIVED_COMPONENT;
			case GRAMMATICAL_DESCRIPTION_ERROR : return SCTID_INACT_GRAMMATICAL_DESCRIPTION_ERROR;
			default: throw new IllegalArgumentException("Unrecognised inactivation indicator  " + ii.toString());
		}
	}
	
	public static Association translateAssociation(String assocSctId) {
		switch (assocSctId) {
			case SCTID_ASSOC_WAS_A_REFSETID: return Association.WAS_A;
			case SCTID_ASSOC_POSS_REPLACED_BY_REFSETID : return Association.POSS_REPLACED_BY;
			case SCTID_ASSOC_REPLACED_BY_REFSETID : return Association.REPLACED_BY;
			case SCTID_ASSOC_SAME_AS_REFSETID : return Association.SAME_AS;
			case SCTID_ASSOC_MOVED_TO_REFSETID : return Association.MOVED_TO;
			case SCTID_ASSOC_POSS_EQUIV_REFSETID : return Association.POSS_EQUIV_TO;
			case SCTID_ASSOC_PART_EQUIV_REFSETID : return Association.PARTIALLY_EQUIV_TO;
			case SCTID_ASSOC_ALTERNATIVE_REFSETID : return Association.ALTERNATIVE;
			case SCTID_ASSOC_REFERS_TO_REFSETID : return Association.REFERS_TO;
			case SCTID_ASSOC_ANATOMY_STRUC_ENTIRE_REFSETID : return Association.ANATOMY_STRUC_ENTIRE;
			case SCTID_ASSOC_ANATOMY_STRUC_PART_REFSETID : return Association.ANATOMY_STRUC_PART;
			default: throw new IllegalArgumentException("Unrecognised historical association indicator value " + assocSctId);
		}
	}

	public static String translateAssociation(Association assoc) {
		switch (assoc) {
			case WAS_A : return SCTID_ASSOC_WAS_A_REFSETID;
			case POSS_REPLACED_BY : return SCTID_ASSOC_POSS_REPLACED_BY_REFSETID ;
			case REPLACED_BY : return SCTID_ASSOC_REPLACED_BY_REFSETID ;
			case SAME_AS : return SCTID_ASSOC_SAME_AS_REFSETID ;
			case MOVED_TO : return SCTID_ASSOC_MOVED_TO_REFSETID ;
			case POSS_EQUIV_TO : return SCTID_ASSOC_POSS_EQUIV_REFSETID ;
			case PARTIALLY_EQUIV_TO : return SCTID_ASSOC_PART_EQUIV_REFSETID ;
			case ALTERNATIVE : return SCTID_ASSOC_ALTERNATIVE_REFSETID ;
			case REFERS_TO : return SCTID_ASSOC_REFERS_TO_REFSETID ;
			case ANATOMY_STRUC_ENTIRE : return SCTID_ASSOC_ANATOMY_STRUC_ENTIRE_REFSETID ;
			case ANATOMY_STRUC_PART : return SCTID_ASSOC_ANATOMY_STRUC_PART_REFSETID ;
			default: throw new IllegalArgumentException("Unrecognised historical association indicator value " + assoc);
		}
	}

	public static String prettyPrintHistoricalAssociations(Concept c, GraphLoader gl) throws TermServerScriptException {
		return prettyPrintHistoricalAssociations(c, gl, false);
	}

	public static String prettyPrintHistoricalAssociations(Concept c, GraphLoader gl, boolean includeInactivationIndicator) throws TermServerScriptException {
		String inactIndicatorStr = c.getInactivationIndicator() == null ? "NOT SET": c.getInactivationIndicator().toString();
		String associations = includeInactivationIndicator? inactIndicatorStr + "\n" : "";
		boolean isFirst = true;
		for (AssociationEntry assoc : c.getAssociationEntries(ActiveState.ACTIVE, true))  {
			if (!isFirst) {
				associations += "\n";
			} else {
				isFirst = false;
			}
			if (assoc.getRefsetId() == null || assoc.getTargetComponentId() == null) {
				throw new TermServerScriptException("Malformed historical association encountered : " + assoc);
			}
			associations += translateAssociation(assoc.getRefsetId()) + ": " + gl.getConcept(assoc.getTargetComponentId());
		}
		
		if (c.getAssociationEntries().isEmpty()) {
			String targets = c.getAssociationTargets().toString(gl);
			if (targets.length() > 0 && associations.length() > 0) {
				associations += "\n";
			}
			associations += targets;
		}
		
		return associations;
	}
	
	public static String prettyPrintHistoricalAssociations(Description d, GraphLoader gl) throws TermServerScriptException {
		String associations = "";
		boolean isFirst = true;
		for (AssociationEntry assoc : d.getAssociationEntries())  {
			if (assoc.isActiveSafely()) {
				if (!isFirst) {
					associations += "\n";
				} else {
					isFirst = false;
				}
				associations += translateAssociation(assoc.getRefsetId()) + " " + gl.getConcept(assoc.getTargetComponentId());
			}
		}
		
		String targets = d.getAssociationTargets().toString(gl);
		if (targets.length() > 0 && associations.length() > 0) {
			associations += "\n";
		}
		associations += targets;

		return associations;
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
			String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn())[1];
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
			LOGGER.warn("Unable to determine concept type of " + c + " due to " + e);
		}
	}

	//Return a set of groups where there is an active non-isa relationship
	public static Set<Integer> getActiveGroups(Concept c) {
		return getActiveGroups(c, CharacteristicType.STATED_RELATIONSHIP);
	}
	
	public static Set<Integer> getActiveGroups(Concept c, CharacteristicType charType) {
		Set<Integer> activeGroups = new HashSet<>();
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				activeGroups.add((int)r.getGroupId());
			}
		}
		return activeGroups;
	}
	
	public static int getFirstFreeGroup(Concept c) {
		return getFirstFreeGroup(c, CharacteristicType.STATED_RELATIONSHIP);
	}
	
	public static int getFirstFreeGroup(Concept c,CharacteristicType charType) {
		Set<Integer> activeGroups = getActiveGroups(c, charType);
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
		Map<String, Acceptability> aMap = new HashMap<>();
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

	public static Concept createConcept(String term, String semTag, Concept parent) throws TermServerScriptException {
		Concept newConcept = Concept.withDefaults(null);
		addDefaultTerms(newConcept, term, semTag);
		if (parent != null) {
			Relationship parentRel = new Relationship (null, IS_A, parent, UNGROUPED);
			newConcept.addRelationship(parentRel);
		}
		return newConcept;
	}

	public static Concept createConcept(String sctId, String fsn) throws TermServerScriptException {
		Concept newConcept = Concept.withDefaults(sctId);
		String[] parts = deconstructFSN(fsn);
		addDefaultTerms(newConcept, parts[0], parts[1]);
		return newConcept;
	}
	
	
	private static void addDefaultTerms(Concept c, String term, String semTag) throws TermServerScriptException {
		Description fsn = Description.withDefaults(term == null? null : term + " " + semTag, DescriptionType.FSN, Acceptability.PREFERRED);
		c.addDescription(fsn);
		
		Description pt = Description.withDefaults(term, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		c.addDescription(pt, true);  //Allow duplication - we might have a null term if we don't know enough to create one yet.
	}
	
	//Where we have multiple potential responses eg concentration or presentation strength, return the first one found given the 
	//order specified by the array
	public static Concept getTarget(Concept c, Concept[] types, int groupId, CharacteristicType charType) throws TermServerScriptException {
		return getTarget(c, types, groupId, charType, false);
	}
	
	//Where we have multiple potential responses eg concentration or presentation strength, return the first one found given the 
	//order specified by the array
	public static Concept getTarget(Concept c, Concept[] types, int groupId, CharacteristicType charType, boolean allowNewConcepts) throws TermServerScriptException {
		for (Concept type : types) {
			Set<Relationship> rels = c.getRelationships(charType, type, groupId);
			if (rels.size() > 1) {
				LOGGER.warn(c + " has multiple " + type + " in group " + groupId);
			} else if (rels.size() == 1) {
				if (allowNewConcepts) {
					return rels.iterator().next().getTarget();
				} else {
					//This might not be the full concept, so recover it fully from our loaded cache
					return GraphLoader.getGraphLoader().getConcept(rels.iterator().next().getTarget().getConceptId());
				}
			}
		}
		return null;
	}
	
	public static Concept getTarget(RelationshipGroup g, Concept[] types, boolean allowNewConcepts) throws TermServerScriptException {
		for (Concept type : types) {
			Set<Relationship> rels = g.getRelationshipsWithType(type);
			if (rels.size() > 1) {
				LOGGER.warn("{} has multiple {}", g, type);
			} else if (rels.size() == 1) {
				if (allowNewConcepts) {
					return rels.iterator().next().getTarget();
				} else {
					//This might not be the full concept, so recover it fully from our loaded cache
					return GraphLoader.getGraphLoader().getConcept(rels.iterator().next().getTarget().getConceptId());
				}
			}
		}
		return null;
	}
	
	public static String getConcreteValue(Concept c, Concept[] types, int groupId, CharacteristicType charType) throws TermServerScriptException {
		for (Concept type : types) {
			Set<Relationship> rels = c.getRelationships(charType, type, groupId);
			if (rels.size() > 1) {
				LOGGER.warn("{} has multiple {} in group {}", c, type, groupId);
			} else if (rels.size() == 1) {
				Relationship r = rels.iterator().next();
				if (!r.isConcrete()) {
					throw new IllegalArgumentException("Attempt to recover concrete value from non-concrete relationship: " + r);
				}
				return r.getConcreteValue().getValue();
			}
		}
		return null;
	}
	
	public static Set<Concept> getTargets(Concept c, Concept[] types, CharacteristicType charType) {
		Set<Concept> targets = new HashSet<>();
		for (Concept type : types) {
			Set<Relationship> rels = c.getRelationships(charType, type, ActiveState.ACTIVE);
			targets.addAll(rels.stream().filter(Relationship::isNotConcrete).map(Relationship::getTarget).collect(Collectors.toSet()));
		}
		return targets;
	}
	
	public static Set<Concept> getTargets(Concept c) {
		return c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE).stream()
				.filter(Relationship::isNotConcrete)
				.map(Relationship::getTarget)
				.collect(Collectors.toSet());
	}

	public static Integer getConcreteIntValue(Concept c, Concept type, CharacteristicType charType, int groupId) throws TermServerScriptException {
		Integer value = null;
		Set<Relationship> rels = c.getRelationships(charType, type, groupId);
		for (Relationship rel : rels) {
			if (rel.isConcrete() && value == null) {
				try {
					value = Integer.parseInt(rel.getConcreteValue().getValue());
				} catch (Exception e) {
					throw new TermServerScriptException("Attempt to recover non-integer value " + rel.getConcreteValue() + " on type " + type + " for concept " + c);
				}
			} else if (!rel.isConcrete()) {
				throw new TermServerScriptException("Attempt to recover concrete value from non-concrete type " + type + " for concept " + c);
			} else if (value != null) {
				throw new TermServerScriptException("Unexpected multiple " + type + " for concept " + c);
			}
		}
		return value;
	}

	public static Integer countAttributes(Expressable c, CharacteristicType charType) {
		int attributeCount = 0;
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
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
	 * @return true if the concept has one of the specified types as an attribute
	 */
	public static boolean hasType(CharacteristicType charType, Concept c, Set<Concept> types) {
		for (Concept type : types) {
			if (hasType(charType, c, type)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * @return true if the concept has the specified attributeValue
	 */
	public static boolean hasValue(CharacteristicType charType, Concept c, Concept value) {
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			if (r.getTarget().equals(value)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * @return the highest concept reached before hitting "end"
	 */
	public static Concept getHighestAncestorBefore(Concept start, Concept end) {
		Set<Concept> topLevelAncestors = getHighestAncestorsBefore(start, end);
		if (topLevelAncestors.size() > 1) {
			throw new IllegalArgumentException(start + " has multiple ancestors immediately before " + end);
		} else if (topLevelAncestors.isEmpty()) {
			throw new IllegalArgumentException("Failed to find ancestors of " + start + " before " + end);
		}
		return topLevelAncestors.iterator().next();
	}
	
	public static Set<Concept> getHighestAncestorsBefore(Concept start, Concept end) {
		if (start == null) {
			throw new IllegalStateException("Asked to find highest ancestor - start concept not specified");
		}
		if (end == null) {
			throw new IllegalStateException("Asked to find highest ancestor - end concept not specified");
		}
		Set<Concept> topLevelAncestors = new HashSet<>();
		for (Concept parent : start.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (parent.equals(end)) {
				return Collections.singleton(start);
			} else if (parent.equals(ROOT_CONCEPT)) {
				throw new IllegalStateException(start + " reached ROOT before finding " + end);
			} else {
				topLevelAncestors.addAll(getHighestAncestorsBefore(parent, end));
			}
		}
		return topLevelAncestors;
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
			Set<Relationship> matchingRels = b.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)
												.stream()
												.filter(br -> cache.getAncestorsOrSelfSafely(r.getType()).contains(br.getType()))
												.filter(br -> cache.getAncestorsOrSelfSafely(r.getTarget()).contains(br.getTarget()))
												.collect(Collectors.toSet());
			//If there are no matching rels, then these concept models are disjoint
			if (matchingRels.isEmpty()) {
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
		if (a.isConcrete() && b.isConcrete()) {
			if (a.getConcreteValue().equals(b.getConcreteValue())) {
				sameValue = true;
			}
		} else {
			if (a.getTarget().equals(b.getTarget())) {
				sameValue = true;
			} else if (cache.getAncestors(a.getTarget()).contains(b.getTarget())) {
				moreSpecificValue = true;
			} else {
				return false;
			}
		}
		
		//If they're exactly the same, then it's not MORE specific
		if (sameType && sameValue) {
			return false;
		} else if (moreSpecificType || moreSpecificValue) {
			return true;
		}
		return false;
	}
	

	/*Very similar to "moreSpecific", but here we're saying the type might be more 
	 * specific and the value less so, or visa versa.
	 * This will also pick up the "more specific" case, so check those first
	 */
	public static boolean inconsistentSubsumption(Relationship a, Relationship b, AncestorsCache cache) throws TermServerScriptException {
		boolean sameType = false;
		boolean subsumptionRelationshipType = false;
		//Compare Types
		if (a.getType().equals(b.getType())) {
			sameType = true;
		} else if (cache.getAncestors(a.getType()).contains(b.getType()) ||
				cache.getAncestors(b.getType()).contains(a.getType())) {
			subsumptionRelationshipType = true;
		} else {
			return false;
		}
		
		//If type has some subsumption relationship how does value compare?
		boolean sameValue = false;
		boolean subsumptionRelationshipValue = false;
		if (a.isConcrete() && b.isConcrete()) {
			if (a.getConcreteValue().equals(b.getConcreteValue())) {
				sameValue = true;
			}
		} else {
			if (a.getTarget().equals(b.getTarget())) {
				sameValue = true;
			} else if (cache.getAncestors(a.getTarget()).contains(b.getTarget()) ||
					cache.getAncestors(b.getTarget()).contains(a.getTarget())) {
				subsumptionRelationshipValue = true;
			} else {
				return false;
			}
		}
		
		//If they're exactly the same, then it's not MORE specific
		if (sameType && sameValue) {
			return false;
		} else if (subsumptionRelationshipType || subsumptionRelationshipValue) {
			return true;
		}
		return false;
	}
	
	/**
	 * @return true if a is same or more specific than b
	 * Note a may have more attributes than b, as long as all of b's attributes are 
	 * present (or more specific) in a, then the presence of a covers b.  
	 * b is redundant
	 */
	public static boolean covers(RelationshipGroup a, RelationshipGroup b, AncestorsCache cache) throws TermServerScriptException {
		for (Relationship rb : b.getRelationships()) {
			boolean matchFound = false;
			for (Relationship ra : a.getRelationships()) {
				if (ra.equalsTypeAndTargetValue(rb) || isMoreSpecific(ra, rb, cache)) {
					matchFound = true;
					break;
				}
			}
			if (!matchFound) {
				return false;
			}
		}
		//If we get here, all relationships in group "b" match, or are more general
		//than those in group a
		return true;
	}

	/*
	 * Get active relationships which are the same as, or descendants of the stated types and values
	 */
	public static Set<Relationship> getSubsumedRelationships(Concept c, Concept type, Concept target,
			CharacteristicType charType, AncestorsCache cache) throws TermServerScriptException {
		Set<Relationship> matchedRelationships = new HashSet<>();
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
		if (r1.equalsTypeAndTargetValue(r2)) {
			throw new IllegalArgumentException("Cannot answer if " + r1 + " is grouped with itself");
		}
		
		for (RelationshipGroup group : c.getRelationshipGroups(charType)) {
			boolean foundFirst = false;
			for (Relationship r : group.getRelationships()) {
				if (r.equalsTypeAndTargetValue(r1) || r.equalsTypeAndTargetValue(r2)) {
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
				if (r.equalsTypeAndTargetValue(findMe)) {
					groups.add(group);
					continue nextGroup;
				}
			}
		}
		return groups;
	}
	
	public static void removeRedundancies(Set<Concept> concepts) throws TermServerScriptException {
		Set<Concept> redundant = new HashSet<>();
		DescendantsCache cache = GraphLoader.getGraphLoader().getDescendantsCache();
		//For each concept, it is redundant if any of its descendants are also present
		for (Concept concept : concepts) {
			Set<Concept> descendants = new HashSet<>(cache.getDescendants(concept));
			descendants.retainAll(concepts);
			if (!descendants.isEmpty()) {
				redundant.add(concept);
			}
		}
		concepts.removeAll(redundant);
	}

	public static ComponentType getComponentType(String sctId) throws TermServerScriptException {
		//Check out the 2nd to last character - indicates type of component
		String penultimate = sctId.substring(sctId.length() - 2, sctId.length() - 1);
		switch (penultimate) {
			case "0" : return ComponentType.CONCEPT;
			case "1" : return ComponentType.DESCRIPTION;
			case "2" : return ComponentType.INFERRED_RELATIONSHIP;
			default : throw new TermServerScriptException("Unable to determine component type of: " + sctId);
		}
	}

	public static boolean hasAcceptabilityInDialect(Description d, String langRefsetId,
			Acceptability targetAcceptability) throws TermServerScriptException {
		
		//Can we have this acceptablity in ANY langRefSet?
		if (langRefsetId == null) {
			Collection<Acceptability> acceptabilities = d.getAcceptabilities();
			if (acceptabilities.isEmpty()) {
				return targetAcceptability.equals(Acceptability.NONE);
			} else if (targetAcceptability.equals(Acceptability.BOTH) && !acceptabilities.isEmpty()) {
				return true;
			} else {
				for (Acceptability acceptability : acceptabilities) {
					if (acceptability.equals(targetAcceptability)) {
						return true;
					}
				}
				return false;
			}
		} else {
			Acceptability acceptability = d.getAcceptability(langRefsetId);
			if ((targetAcceptability == null || targetAcceptability.equals(Acceptability.NONE)) 
					&& acceptability == null) {
				return true;
			} else if (acceptability == null || targetAcceptability == null) {
				return false;
			}
			
			//Once we're here, the descriptions acceptability cannot be null.
			if (targetAcceptability.equals(Acceptability.BOTH)) {
				return true;
			}
			return acceptability.equals(targetAcceptability);
		}
	}

	//Check every stated relationship is exactly matched by an inferred one
	//Actually we have to check for matching groups to ensure we're checking like-for-like
	public static boolean inferredMatchesStated(Concept c) {
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		
		//First of all, do we have the same number?
		if (statedGroups.size() != inferredGroups.size()) {
			return false;
		}
		nextStatedGroup:
		for (RelationshipGroup statedGroup : statedGroups) {
			for (RelationshipGroup inferredGroup : inferredGroups) {
				if (statedGroup.equals(inferredGroup)) {
					continue nextStatedGroup;
				}
			}
			//If we get to here, we've failed to find an inferred group to match the stated one
			return false;
		}
		return true;
	}

	public static Concept getHistoricalParent(Concept c) throws TermServerScriptException {
		if (c.isActiveSafely()) {
			throw new TermServerScriptException("Attempted to find historical parent of an active concept: " + c);
		}
		//TODO Sort the parent relationships by time so we get the one most recently inactivated
		List<Concept> parents = c.getRelationships().stream()
				.filter(r -> r.getType().equals(IS_A))
				.map(r -> r.getTarget())
				.collect(Collectors.toList());
		
		//Return the first one that is still active
		for (Concept parent : parents) {
			if (parent.isActiveSafely()) {
				return parent;
			}
		}
		
		//Otherwise, find the parent's parent and see if that's 
		//This could be done as a breadth first recursive search
		for (Concept parent : parents) {
			Concept grandParent = getHistoricalParent(parent);
			if (grandParent != null) {
				return grandParent;
			}
		}
		return null;
	}

	public static String populateFSNs(String stl) throws TermServerScriptException {
		//Loop through string and replace any numbers that aren't followed by a pipe
		//with the full string
		GraphLoader gl = GraphLoader.getGraphLoader();
		
		int origLength = stl.length();
		
		//Easiest to remove all pipes and then replace all
		stl = stl.replaceAll("\\|.*?\\|", "").replaceAll("  ", "");
		Pattern p = Pattern.compile("\\d{8,}");
		Matcher m = p.matcher(stl);
		StringBuffer sb = new StringBuffer();
		while (m.find()) {
			String sctId = m.group();
			Concept c = gl.getConceptSafely(sctId);
			if (c == null) {
				throw new TermServerScriptException("Unknown concept encountered in STL: " + sctId);
			}
			m.appendReplacement(sb, c.toString());
		}
		m.appendTail(sb);
		
		if (sb.length() < origLength) {
			LOGGER.warn("Populating FSNs has reduced overall length - check: '{}'", sb);
		}
		return sb.toString();
	}
	
	public static final Comparator<Description> decriptionPrioritiser = (d1, d2) -> priority(d2).compareTo(priority(d1));

	/**
	 * @return the list ordered so that FSN is returned first, then PT, then acceptable synonyms
	 */
	public static List<Description> prioritise(List<Description> descriptions) {
		Collections.sort(descriptions, decriptionPrioritiser);
		return descriptions;
	}
	
	private static Integer priority(Description d) {
		if (d.getType().equals(DescriptionType.FSN)) {
			return 2;
		} else if (d.isPreferred()) {
			return 1;
		}
		return 0;
	}
	
	
	/**
	 * @return a prioritised joined list of descriptions
	 */
	public static String getDescriptions(Concept c) {
		return prioritise(c.getDescriptions(ActiveState.ACTIVE)).stream()
				.map(Description::getTerm).collect(Collectors.joining(",\n"));
	}

	public static String getDescriptionsOfType(Concept c, List<String> targetTypes) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
				.filter(d -> SnomedUtils.isTargetDescriptionType(targetTypes, d))
				.sorted(SnomedUtils.decriptionPrioritiser)
				.map(Description::getTerm)
				.collect(Collectors.joining(",\n"));
	}


	public static String getDescriptionsFull(Concept c) {
		return prioritise(c.getDescriptions(ActiveState.ACTIVE)).stream()
				.map(Description::toString).collect(Collectors.joining(",\n"));
	}

	public static String getDescriptionsToString(Concept c) {
		return getDescriptionsToString(c, false);
	}

	public static String getDescriptionsToString(Concept c, boolean includeInactiveDescriptions) {
		List<Description> descriptions = includeInactiveDescriptions ? c.getDescriptions() : c.getDescriptions(ActiveState.ACTIVE);
		return prioritise(descriptions).stream()
				.map(Object::toString).collect(Collectors.joining(",\n"));
	}
	
	public static String getDescriptions(Concept c, boolean includeDefinitions) {
		return prioritise(c.getDescriptions(ActiveState.ACTIVE)).stream()
				.filter(d -> includeDefinitions || !d.getType().equals(DescriptionType.TEXT_DEFINITION))
				.map(Description::getTerm)
				.collect(Collectors.joining(",\n"));
	}
	
	public static Set<Description> getDescriptionsList(Concept c, boolean includeDefinitions) {
		return getDescriptionsList(c, ActiveState.ACTIVE, includeDefinitions);
	}

	public static Set<Description> getDescriptionsList(Concept c, ActiveState activeState, boolean includeDefinitions) {
		return prioritise(c.getDescriptions(activeState)).stream()
				.filter(d -> includeDefinitions || !d.getType().equals(DescriptionType.TEXT_DEFINITION))
				.collect(Collectors.toSet());
	}

	public static Set<Concept> hasParents(Set<Concept> matchParents, Set<Concept> range, int limit) {
		Set<Concept> matching = new HashSet<>();
		for (Concept testMe : range) {
			if (testMe.getParents(CharacteristicType.INFERRED_RELATIONSHIP).containsAll(matchParents)) {
				matching.add(testMe);
			}
			if (matching.size() >= limit) {
				break;
			}
		}
		return matching;
	}

	public static int notNullCount(Concept[] concepts) {
		int notNullCount = 0;
		for (Concept concept : concepts) {
			if (concept != null) {
				notNullCount++;
			}
		}
		return notNullCount;
	}

	public static int appearances(Concept[] concepts, Concept conceptOfInterest) {
		int appearances = 0;
		for (Concept concept : concepts) {
			if (concept != null && concept.equals(conceptOfInterest)) {
				appearances++;
			}
		}
		return appearances;
	}

	public static Collection<RelationshipGroup> toGroups(Collection<Relationship> relationships) {
		Map<Integer, RelationshipGroup> groupMap = new HashMap<>();
		for (Relationship r : relationships) {
			RelationshipGroup g = groupMap.get(r.getGroupId());
			if (g == null) {
				g = new RelationshipGroup(r.getGroupId());
				groupMap.put(r.getGroupId(), g);
			}
			g.addRelationship(r);
		}
		return groupMap.values();
	}

	public static RelationshipGroup findMatchingGroup(Concept c, RelationshipGroup g, CharacteristicType charType) {
		nextPotentialMatch:
		for (RelationshipGroup potentialMatch : c.getRelationshipGroups(charType)) {
			if (potentialMatch.size() == g.size()) {
				nextRelationship:
				for (IRelationship r1 : potentialMatch.getIRelationships()) {
					for (IRelationship r2 : g.getIRelationships()) {
						if (r1.getType().equals(r2.getType())) {
							if (r1.isConcrete() && r2.isConcrete()) {
								if (r1.getConcreteValue().equals(r2.getConcreteValue())) {
									continue nextRelationship;
								}
							} else {
								if (r1.getTarget().equals(r2.getTarget())) {
									continue nextRelationship;
								}
							}
						}
					}
					//No match found for r1 in g.  Try next group.
					continue nextPotentialMatch; 
				}
			//All r1s found a match - we've found a group match
			return potentialMatch;
			}
		}
		return null;
	}

	public static RelationshipGroup findMatchingOrDescendantGroup(Concept c, RelationshipGroup g, CharacteristicType charType) throws TermServerScriptException {
		nextPotentialMatch:
		for (RelationshipGroup potentialMatch : c.getRelationshipGroups(charType)) {
			if (potentialMatch.size() == g.size()) {
				nextRelationship:
				for (IRelationship r1 : potentialMatch.getIRelationships()) {
					for (IRelationship r2 : g.getIRelationships()) {
						if (r1.getType().equals(r2.getType()) || r1.getType().getAncestors(RF2Constants.NOT_SET).contains(r2.getType())) {
							if (r1.isConcrete() && r2.isConcrete()) {
								if (r1.getConcreteValue().equals(r2.getConcreteValue())) {
									continue nextRelationship;
								}
							} else if (r1.getTarget().equals(r2.getTarget()) || r1.getTarget().getAncestors(RF2Constants.NOT_SET).contains(r2.getTarget())) {
								continue nextRelationship;
							}
						}
					}
					//No match found for r1 in g.  Try next group.
					continue nextPotentialMatch;
				}
				//All r1s found a match - we've found a group match
				return potentialMatch;
			}
		}
		return null;
	}

	public static boolean isFreeGroup(CharacteristicType charType, Concept c, int checkIfFree) {
		for (RelationshipGroup g : c.getRelationshipGroups(charType)) {
			if (g.getGroupId() == checkIfFree) {
				return false;
			}
		}
		return true;
	}
	
	public static String makeMachineReadable(String exp) {
		return exp.trim()
				.toUpperCase()
				.replaceAll("\\|[^\\|]+\\|", " ") // Remove |string| sections entirely.
				.replaceAll("\\s+([\\(\\{\\<\\>\\}\\)\\]!:=*\",])", "$1") // Remove necessary spaces before "sections".
				.replaceAll("([\\(\\{\\<\\>\\}\\)\\]!:=*])\\s+", "$1") // Remove necessary spaces after "sections".
				.replaceAll("([^A-Z])MINUS([^A-Z])", "$1 MINUS $2") // Ensure MINUS verb has spaces around it.
				.replaceAll("([^A-Z])AND([^A-Z])", "$1 AND $2") // Ensure AND verb has spaces around it.
				.replaceAll("([^A-Z])OR([^A-Z])", "$1 OR $2") // Ensure OR verb has spaces around it.
				.replaceAll("\\+ID", " +ID") // Ensure "+ID" has a space before the plus.
				.replaceAll("\\@", " @") // Ensure "@" has a space before.
				.replaceAll("\\s+", " ") // Replace multiple whitespace (space, tab, newline etc) with single space.
				.trim();
	}

	public static boolean containsAttributeOrMoreSpecific(Concept c, RelationshipTemplate targetAttribute, DescendantsCache cache) throws TermServerScriptException {
		Set<Concept> types = cache.getDescendantsOrSelf(targetAttribute.getType());
		//If there's no attribute value specified, we'll match on just the target type
		Set<Concept> values = targetAttribute.getTarget() == null ? null : cache.getDescendantsOrSelf(targetAttribute.getTarget());
		return !c.getRelationships().stream()
				.filter(Component::isActiveSafely)
				.filter(r -> r.getCharacteristicType().equals(targetAttribute.getCharacteristicType()))
				.filter(r -> types.contains(r.getType()))
				.filter(r -> {
					if (r.isNotConcrete()) {
						return values == null || values.contains(r.getTarget());
					} else {
						return r.getConcreteValue().equals(targetAttribute.getConcreteValue());
					}
				})
				.toList().isEmpty();
	}

	public static boolean startsWithSCTID(String str) {
		//Can we find an SCTID in this string?
		str = str.trim().replaceAll(ESCAPED_PIPE, " ");
		String[] parts = str.split(SPACE);
		String errMsg = isValid(parts[0], null);
		return errMsg == null;
	}

	public static String getAssociationType(AssociationEntry a) throws TermServerScriptException {
		Concept assocType = GraphLoader.getGraphLoader().getConcept(a.getRefsetId());
		return assocType.getPreferredSynonym().replace(" association reference set", "");
	}

	public static Integer compareEffectiveDate(String effectiveDate1, String effectiveDate2) {
		if (effectiveDate1 == null || effectiveDate2 == null) {
			return null;
		}

		try {
			Date date = EFFECTIVE_DATE_FORMAT.parse(effectiveDate1);
			Date date2 = EFFECTIVE_DATE_FORMAT.parse(effectiveDate2);
			return date.compareTo(date2);
		} catch (ParseException e) {
		}
		return null;
	}

	public static boolean isConceptSctid(String componentId) {
		//A zero in the penultimate character indicates a concept SCTID
		if (StringUtils.isEmpty(componentId)) {
			return false;
		}
		return componentId.charAt(componentId.length()-2) == '0';
	}
	
	public static boolean isDescriptionSctid(String componentId) {
		//A 1 in the penultimate character indicates a description SCTID
		if (StringUtils.isEmpty(componentId)) {
			return false;
		}
		return componentId.charAt(componentId.length()-2) == '1';
	}

	public static boolean isRelationshipSctid(String componentId) {
		//A 2 in the penultimate character indicates a relationship SCTID
		if (StringUtils.isEmpty(componentId)) {
			return false;
		}
		return componentId.charAt(componentId.length()-2) == '2';
	}
	public static Component getParentComponent(RefsetMember rm, GraphLoader gl) throws TermServerScriptException {
		if (rm != null && !StringUtils.isEmpty(rm.getReferencedComponentId())) {
			if (isConceptSctid(rm.getReferencedComponentId())) {
				//Don't validate, don't create if required
				return gl.getConcept(rm.getReferencedComponentId(), false, false);
			} else {
				return gl.getDescription(rm.getReferencedComponentId(), false, false);
			}
		}
		return null;
	}
	
	public static Component getParentComponent(Component c, GraphLoader gl) throws TermServerScriptException {
		if (c instanceof RefsetMember) {
			return getParentComponent((RefsetMember)c, gl);
		} else if (c instanceof Concept) {
			return null;
		} else if (c instanceof Relationship) {
			return ((Relationship)c).getSource();
		} else if (c instanceof Description) {
			return gl.getConcept(((Description)c).getConceptId());
		} else {
			throw new TermServerScriptException("Type could not be determined: " + c);
		}
	}

	/*
	 * Adds a historical association using the string based map format as per the Terminology Server's API
	 */
	public static void addHistoricalAssociationInTsForm(Concept c, AssociationEntry histAssoc) {
		//The TS form can only store active associations
		if (histAssoc.isActiveSafely()) {
			AssociationTargets targets = c.getAssociationTargets();
			String target = histAssoc.getTargetComponentId();
			switch (histAssoc.getRefsetId()) {
				case (SCTID_ASSOC_MOVED_TO_REFSETID) : targets.getMovedTo().add(target);
														break;
				case (SCTID_ASSOC_WAS_A_REFSETID) : targets.getWasA().add(target);
														break;
				case (SCTID_ASSOC_REPLACED_BY_REFSETID) : targets.getReplacedBy().add(target);
														break;
				case (SCTID_ASSOC_SAME_AS_REFSETID) : targets.getSameAs().add(target);
														break;	
				case (SCTID_ASSOC_POSS_EQUIV_REFSETID) : targets.getPossEquivTo().add(target);
														break;
				case (SCTID_ASSOC_PART_EQUIV_REFSETID) : targets.getPartEquivTo().add(target);
				break;	
			}
		}
	}

	public static boolean inModule(Component c, String[] modules) {
		for (String module : modules) {
			if (c.getModuleId().equals(module)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Search concept for a group which features the same relationship 
	 * as that of "type" that can be found in the specfied group
	 * @throws TermServerScriptException 
	 */
	public static RelationshipGroup findMatchingGroup(Concept c, RelationshipGroup g,
			Concept type, CharacteristicType charType, boolean allowNewConcepts) throws TermServerScriptException {
		Concept targetValue = getTarget(g, new Concept[] { type }, allowNewConcepts);
		//Do we have that same type/value in concept?
		for (RelationshipGroup checkGroup : c.getRelationshipGroups(charType)) {
			Concept matchedValue =  getTarget(checkGroup, new Concept[] { type }, allowNewConcepts);
			if (matchedValue != null && matchedValue.equals(targetValue)) {
				return checkGroup;
			}
		}
		return null;
	}

	public static boolean isECL(String ecl) {
		if (ecl.contains("<") || ecl.contains("{") || ecl.contains(":") || ecl.toUpperCase().contains("MINUS")) {
			return true;
		}
		return false;
	}

	public static String formatReleaseDate(String effectiveTime) throws TermServerScriptException {
		if (effectiveTime.length() != 8) {
			throw new TermServerScriptException("Malformed effectiveTime '" + effectiveTime + "'");
		}
		return effectiveTime.substring(0,4) + "-" + effectiveTime.substring(4,6) + "-" + effectiveTime.substring(6,8);
	}

	public static boolean hasUsGbPtVariance(Concept c) throws TermServerScriptException {
		Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
		Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
		if (usPT == null || gbPT == null || usPT.equals(gbPT)) {
			return false;
		}
		return true;
	}

	//Do we in fact have ANY synonym descriptions which are acceptable/preferred in US but not in GB AND vice versa
	public static boolean hasUsGbVariance(Concept c) throws TermServerScriptException {
		boolean hasUsSpecific = false;
		for (Description d : c.getDescriptions(US_ENG_LANG_REFSET, Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
			if (!d.isAcceptable(GB_ENG_LANG_REFSET) && !d.isPreferred(GB_ENG_LANG_REFSET)) {
				hasUsSpecific = true;
				break;
			}
		}
		
		boolean hasGbSpecific = false;
		for (Description d : c.getDescriptions(GB_ENG_LANG_REFSET, Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
			if (!d.isAcceptable(US_ENG_LANG_REFSET) && !d.isPreferred(US_ENG_LANG_REFSET)) {
				hasGbSpecific = true;
				break;
			}
		}
		
		return hasUsSpecific && hasGbSpecific;
	}

	public static boolean hasUsGbVariance(Description d) {
		return (d.isAcceptable(US_ENG_LANG_REFSET) && !d.isAcceptable(GB_ENG_LANG_REFSET)) ||
				(d.isPreferred(US_ENG_LANG_REFSET) && !d.isPreferred(GB_ENG_LANG_REFSET)) ||
				(d.isAcceptable(GB_ENG_LANG_REFSET) && !d.isAcceptable(US_ENG_LANG_REFSET)) ||
				(d.isPreferred(GB_ENG_LANG_REFSET) && !d.isPreferred(US_ENG_LANG_REFSET));
	}

	public static boolean hasLangRefsetDifference(String descId, Concept a, Concept b) {
		//Check that we have this description in both concepts (eg local and extension branch)
		Description descA = a.getDescription(descId);
		Description descB = b.getDescription(descId);
		if (descA == null || descB == null) {
			return true;
		}
		
		Map<String, Acceptability> mapAcceptA = descA.getAcceptabilityMap();
		Map<String, Acceptability> mapAcceptB = descB.getAcceptabilityMap();
		
		if ((mapAcceptA == null || mapAcceptA.isEmpty()) && (mapAcceptB == null || mapAcceptB.isEmpty())) {
			return false;
		}
		
		if ((mapAcceptA == null && (mapAcceptB != null && !mapAcceptB.isEmpty())) ||
			(mapAcceptA != null && (mapAcceptB == null || mapAcceptB.isEmpty())) ||
			(mapAcceptA != null && mapAcceptA.size() != mapAcceptB.size())) {
			return true;
		}
		
		for (String refsetId : mapAcceptA.keySet()) {
			Acceptability acceptA = mapAcceptA.get(refsetId);
			Acceptability acceptB = mapAcceptB.get(refsetId);
			if (acceptB == null || !acceptA.equals(acceptB)) {
				return true;
			}
		}
		
		return false;
	}

	public static boolean hasDescActiveStateDifference(String descId, Concept a, Concept b) {
		//Check that we have this description in both concepts (eg local and extension branch)
		Description descA = a.getDescription(descId);
		Description descB = b.getDescription(descId);
		if (descA == null || descB == null) {
			return true;
		}
		return descA.isActiveSafely() != descB.isActiveSafely();
	}
	
	public static String shortestTerm(Concept c) {
		String shortestTerm = null;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (shortestTerm == null || d.getTerm().length() < shortestTerm.length()) {
				shortestTerm = d.getTerm();
			}
		}
		return shortestTerm;
	}

	public static boolean isEmpty(Object obj) {
		if (obj == null) {
			return true;
		}
		return StringUtils.isEmpty(obj.toString());
	}

	public static String[] deconstructSCTIDFsn(String sctidFsn) {
		String[] parts = new String[2];
		String[] split = sctidFsn.split(ESCAPED_PIPE);
		parts[0] = split[0].trim();
		parts[1] = split[1];
		return parts;
	}
	
	public static List<Concept> sortFSN(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		return superSet.stream()
		.sorted((c1, c2) -> c1.getFsn().compareTo(c2.getFsn()))
		.toList();
	}
	
	public static List<Concept> sort(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		return superSet.stream()
		.sorted(SnomedUtils::compareSemTagFSN)
		.toList();
	}
	
	public static List<Concept> sortActive(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		return superSet.stream()
		.filter(c -> c.isActiveSafely())
		.sorted(SnomedUtils::compareSemTagFSN)
		.toList();
	}
	
	public static List<Concept> sortInactive(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		return superSet.stream()
		.filter(c -> !c.isActiveSafely())
		.sorted(SnomedUtils::compareSemTagFSN)
		.toList();
	}
	
	public static int compareSemTagFSN(Concept c1, Concept c2) {
		String[] fsnSemTag1 = SnomedUtilsBase.deconstructFSN(c1.getFsn());
		String[] fsnSemTag2 = SnomedUtilsBase.deconstructFSN(c2.getFsn());
		
		if (fsnSemTag1[1] == null) {
			if (!missingFsnReport.contains(c1.getId())) {
				LOGGER.warn("FSN Encountered without semtag: {}", c1);
				missingFsnReport.add(c1.getId());
			}
			return c1.getId().compareTo(c2.getId());
		} else if (fsnSemTag2[1] == null) {
			if (!missingFsnReport.contains(c2.getId())) {
				LOGGER.warn("FSN Encountered without semtag: {}", c2);
				missingFsnReport.add(c2.getId());
			}
			return c1.getId().compareTo(c2.getId());
		}
		
		if (fsnSemTag1[1].equals(fsnSemTag2[1])) {
			return fsnSemTag1[0].compareTo(fsnSemTag2[0]);
		}
		return fsnSemTag1[1].compareTo(fsnSemTag2[1]);
	}

	public static boolean isCore(Component c) {
		return c.getModuleId().equals(SCTID_CORE_MODULE)
				|| c.getModuleId().equals(SCTID_MODEL_MODULE);
	}
	
	public static boolean hasChanges(Concept c) {
		return hasChangesSinceIncludingSubComponents(c, null, true);
	}

	public static boolean hasChangesSinceIncludingSubComponents(Concept c, String fromET,  boolean inclusiveDate) {
		if (hasChangesSince(c, fromET, inclusiveDate)) {
			return true;
		}
		
		for (Description d : c.getDescriptions()) {
			if (hasChangesSince(d, fromET, inclusiveDate)) {
				return true;
			}
			for (LangRefsetEntry l : d.getLangRefsetEntries()) {
				if (hasChangesSince(l, fromET, inclusiveDate)) {
					return true;
				}
			}
		}
		
		for (Relationship r : c.getRelationships()) {
			if (hasChangesSince(r, fromET, inclusiveDate)) {
				return true;
			}
		}
		
		for (AxiomEntry a : c.getAxiomEntries()) {
			if (hasChangesSince(a, fromET, inclusiveDate)) {
				return true;
			}
		}
		
		for (AssociationEntry h : c.getAssociationEntries()) {
			if (hasChangesSince(h, fromET, inclusiveDate)) {
				return true;
			}
		}
		
		for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries()) {
			if (hasChangesSince(i, fromET, inclusiveDate)) {
				return true;
			}
		}
		
		return false;
	}
	
	public static boolean hasChangesSince(Component c, String fromET) {
		return hasChangesSince(c, fromET, true);
	}
	
	public static boolean hasChangesSince(Component c, String fromET, boolean inclusiveDate) {
		if (StringUtils.isEmpty(c.getEffectiveTime())) {
			return true;
		}
		
		return (!StringUtils.isEmpty(fromET) &&
				((inclusiveDate && c.getEffectiveTime().compareTo(fromET) >= 0) ||
				 (!inclusiveDate && c.getEffectiveTime().compareTo(fromET) > 0)));
	}

	//TODO Handle groups and multiple focus concepts
	public static Set<Relationship> fromExpression(GraphLoader gl, String expression) throws TermServerScriptException {
		Set<Relationship> rels = new HashSet<>();
		//First the focus concept(s)
		int cutPoint = expression.indexOf(":");
		Concept parent = gl.getConcept(expression.substring(0, cutPoint));
		rels.add(new Relationship(IS_A, parent));
		String remainder = expression.substring(cutPoint + 1);
		for (String attribute : remainder.split(COMMA)) {
			cutPoint = attribute.indexOf("=");
			Concept type = gl.getConcept(attribute.substring(0, cutPoint));
			Concept target = gl.getConcept(attribute.substring(cutPoint + 1));
			rels.add(new Relationship(type, target));
		}
		return rels;
	}

	public static boolean isInternational(Component c) {
		return InternationalModules.contains(c.getModuleId());
	}
	
	public static boolean isEqualValueInGroups(Concept type, RelationshipGroup lhsGroup, RelationshipGroup rhsGroup) {
		Object lhsValue = getValueInGroup(type, lhsGroup);
		Object rhsValue = getValueInGroup(type, rhsGroup);
		
		if (lhsValue == null && rhsValue == null) {
			return true;
		} else if (lhsValue == null) {
			return false;
		} else if (rhsValue == null) {
			return false;
		}
		return lhsValue.equals(rhsValue);
	}

	private static Object getValueInGroup(Concept type, RelationshipGroup group) {
		for (Relationship r : group.getRelationships()) {
			if (r.getType().equals(type)) {
				return r.isConcrete() ? r.getConcreteValue() : r.getTarget();
			}
		}
		return null;
	}

	public static boolean hasModule(Component c, String[] targetModules) {
		for (String targetModule : targetModules) {
			if (c.getModuleId().equals(targetModule)) {
				return true;
			}
		}
		return false;
	}
	
	public static boolean hasNotModule(Component c, String[] targetModules) {
		boolean hasModule = false;
		for (String targetModule : targetModules) {
			if (c.getModuleId().equals(targetModule)) {
				hasModule = true;
			}
		}
		return !hasModule;
	}
	
	public static Collection<Component> getAllComponents(Concept c) {
		return getAllComponents(c, false);
	}

	public static Collection<Component> getAllComponents(Component c) {
		if (c instanceof Concept) {
			return getAllComponents((Concept)c);
		} else if (c instanceof Description) {
			return getAllComponents((Description)c);
		} else {
			throw new IllegalArgumentException("Unexpected component type: " + c.getClass().getSimpleName());
		}
	}

	public static Collection<Component> getAllComponents(Concept c, boolean includeStatedRels) {
		List<Component> components = new ArrayList<>();
		
		components.add(c);
		
		c.getInactivationIndicatorEntries().stream()
			.forEach(components::add);
		
		c.getAssociationEntries().stream()
			.forEach(components::add);
		
		c.getAxiomEntries().stream()
			.forEach(components::add);
		
		c.getRelationships().stream()
			.filter(r -> r.getCharacteristicType().equals(CharacteristicType.INFERRED_RELATIONSHIP))
			.forEach(components::add);
		
		if (includeStatedRels) {
			//Not included by default because they're normally transient.  They are persisted in axioms
			c.getRelationships().stream()
			.filter(r -> r.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP))
			.forEach(components::add);
		}
		
		//Descriptions and their associated indicators, associations and langrefsets
		c.getDescriptions().stream()
			.forEach(d -> {
				components.add(d);
				components.addAll(getAllComponents(d));
			});

		components.addAll(c.getOtherRefsetMembers());
		components.addAll(c.getComponentAnnotationEntries());
		components.addAll(c.getAlternateIdentifiers());
		return components;
	}
	
	public static Collection<Component> getAllComponents(Description d) {
		List<Component> components = new ArrayList<>();
		components.addAll(d.getLangRefsetEntries());
		components.addAll(d.getInactivationIndicatorEntries());
		components.addAll(d.getAssociationEntries());
		components.addAll(d.getComponentAnnotationEntries());
		return components;
	}
	
	public static Collection<RefsetMember> getAllRefsetMembers(Concept c) {
		List<RefsetMember> refsetMembers = new ArrayList<>();
		
		c.getInactivationIndicatorEntries().stream()
			.forEach(refsetMembers::add);
		
		c.getAssociationEntries().stream()
			.forEach(refsetMembers::add);
		
		c.getAxiomEntries().stream()
			.forEach(refsetMembers::add);
		
		c.getDescriptions().stream()
			.flatMap(d ->  d.getLangRefsetEntries().stream())
			.forEach(refsetMembers::add);
		
		c.getDescriptions().stream()
			.flatMap(d ->  d.getInactivationIndicatorEntries().stream())
			.forEach(refsetMembers::add);
		
		c.getDescriptions().stream()
			.flatMap(d ->  d.getAssociationEntries().stream())
			.forEach(refsetMembers::add);
		
		return refsetMembers;
	}
	
	public static Map<String, Component> getAllComponentsMap(Concept c) {
		return getAllComponents(c).stream()
				.collect(Collectors.toMap(Component::getId, Function.identity()));
	}

	public static boolean hasExtensionSCTID(Component c) {
		return isExtensionSCTID(c.getId());
	}
	
	public static boolean isExtensionSCTID(String sctId) {
		//3rd to last character tells us if we're using a namespace or not
		int idx = sctId.length() - 3;
		return sctId.charAt(idx) == '1';
	}
	
	public static boolean isEnglishDialect(RefsetMember rm) {
		for (String englishRefset : ENGLISH_DIALECTS) {
			if (rm.getRefsetId().contentEquals(englishRefset)) {
				return true;
			}
		}
		return false;
	}

	public static int downgradeTermToAcceptable(Description d) {
		//Get a list of all refsets that we have acceptability for and ensure
		//that it's acceptable
		int changesMade = 0;
		Map<String, Acceptability> acceptablityMap = d.getAcceptabilityMap();
		for (String refsetId : acceptablityMap.keySet()) {
			if (acceptablityMap.get(refsetId).equals(Acceptability.PREFERRED)) {
				changesMade++;
				acceptablityMap.put(refsetId, Acceptability.ACCEPTABLE);
			}
		}
		return changesMade;
	}

	public static int upgradeTermToPreferred(Description d) {
		//Get a list of all refsets that we have acceptability for and ensure
		//that it's acceptable
		int changesMade = 0;
		Map<String, Acceptability> acceptablityMap = d.getAcceptabilityMap();
		for (String refsetId : acceptablityMap.keySet()) {
			if (acceptablityMap.get(refsetId).equals(Acceptability.ACCEPTABLE)) {
				changesMade++;
				acceptablityMap.put(refsetId, Acceptability.PREFERRED);
			}
		}
		return changesMade;
	}

	/**
	 * @return true if the specific attribute can be found in a group OTHER than the one specified
	 */
	public static boolean hasAttributeInAnotherGroup(Concept c, RelationshipTemplate findMe, int notInGroupId) {
		for (RelationshipGroup g : c.getRelationshipGroups(findMe.getCharacteristicType())) {
			if (g.getGroupId() != notInGroupId && g.containsTypeValue(findMe)) {
				return true;
			}
		}
		return false;
	}
	
	public static Concept getHierarchySafely(GraphLoader gl, Concept c) {
		try {
			return getHierarchy(gl, c);
		} catch (TermServerScriptException e) {
			LOGGER.error("Failed to determine hierarchy of " + c, e);
		}
		return NULL_CONCEPT;
	}
		
	
	public static Concept getHierarchy(GraphLoader gl, Concept c) throws TermServerScriptException {
		TransitiveClosure tc = gl.getTransitiveClosure();
		
		if (c.equals(ROOT_CONCEPT)) {
			return c;
		}

		if (!c.isActiveSafely() || c.getDepth() == NOT_SET) {
			if (c.getDepth() == NOT_SET) {
				LOGGER.warn("Depth of " + c + " not set.  Is that expected?");
			}
			return null;  //Hopefully the previous release will know
		} 
		
		if (c.getDepth() == 1) {
			return c;
		} 
		
		for (Long sctId : tc.getAncestors(c)) {
			Concept a = gl.getConcept(sctId);
			if (a.getDepth() == 1) {
				return a;
			} else if (a.getDepth() == NOT_SET) {
				//Is this a full concept or have we picked it up from a relationship?
				if (a.getFsn() == null) {
					LOGGER.warn(a + " encountered as ancestor of " + c + " has partial existence");
				} else {
					throw new TermServerScriptException ("Depth not populated in Hierarchy for " + c.toString() + "\nDefined as: "+ a.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
				}
			}
		}
		throw new TermServerScriptException("Unable to determine hierarchy for " + c.toString() + "\nDefined as: "+ c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP));
	}

	public static String toString(Map<String, Acceptability> acceptabilityMap, boolean lineFeed) throws TermServerScriptException {
		String str = "{ ";
		boolean isFirst = true;
		for (Map.Entry<String, Acceptability> entry : acceptabilityMap.entrySet()) {
			if (!isFirst) {
				str += lineFeed?",\n":", ";
			} else {
				isFirst = false;
			}
			str += entry.getKey() + " --> " + translateAcceptability(entry.getValue());
		}
		return str + " }";
	}

	public static ExtractType getExtractType(String filename) {
		if (filename.contains("Delta")) {
			return ExtractType.DELTA;
		} else if (filename.contains("Snapshot")) {
			return ExtractType.SNAPSHOT;
		} else if (filename.contains("Full")) {
			return ExtractType.FULL;
		}
		return null;
	}
	
	public static String getExtractTypeString (ExtractType type) {
		switch (type) {
		case DELTA: return "Delta";
		case SNAPSHOT : return "Snapshot";
		case FULL : return "Full";
		default : throw new IllegalArgumentException("Unknown Extract Type: " + type);
		}
	}
	
	public static Concept getTopLevel(Concept thisConcept) throws TermServerScriptException {
		//Is this itself a top level concept?
		if (thisConcept.getDepth() == 1 || thisConcept.getDepth() == 0) {
			return thisConcept;
		}
		
		Set<Concept> ancestors = thisConcept.getAncestors(NOT_SET);
		for (Concept ancestor : ancestors) {
			if (ancestor.getDepth() == 1) {
				return ancestor;
			}
		}
		return null;
	}
	
	private static Map<Concept, Set<String>> semanticTagHierarchyMap;
	private static Set<String> allKnowActiveSemanticTags;
	
	private static void populateSemanticTagHierarchyMap(GraphLoader gl) throws TermServerScriptException {
		semanticTagHierarchyMap = new HashMap<>();
		allKnowActiveSemanticTags = new HashSet<>();
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActiveSafely()) {
				continue;
			}
			String semTag = deconstructFSN(c.getFsn())[1];
			if (!allKnowActiveSemanticTags.contains(semTag)) {
				allKnowActiveSemanticTags.add(semTag);
				Concept hierarchy = getTopLevel(c);
				Set<String> semTagsInHierarchy = semanticTagHierarchyMap.get(hierarchy);
				if (semTagsInHierarchy == null) {
					semTagsInHierarchy = new HashSet<>();
					semanticTagHierarchyMap.put(hierarchy, semTagsInHierarchy);
				}
				semTagsInHierarchy.add(semTag);
			}
		}
	}
		
	public static boolean isActiveSemanticTag(String semTag, GraphLoader gl) throws TermServerScriptException {
		if (allKnowActiveSemanticTags == null) {
			populateSemanticTagHierarchyMap(gl);
		}
		return allKnowActiveSemanticTags.contains(semTag);
	}
	
	public static Set<String> getSemanticTagsUsedInHierarchy(Concept hierarchy, GraphLoader gl) throws TermServerScriptException {
		if (semanticTagHierarchyMap == null) {
			populateSemanticTagHierarchyMap(gl);
		}
		return semanticTagHierarchyMap.get(hierarchy);
	}

	public static Set<Concept> getRecentlyTouchedConcepts(Collection<Concept> concepts) {
		return concepts.stream()
				.filter(c -> hasNewChanges(c))
				.collect(Collectors.toSet());
	}

	/**
	 * @return TRUE if the concept or any descriptions, relationships have a null effective Time
	 */
	public static boolean hasNewChanges(Concept c) {
		if (StringUtils.isEmpty(c.getEffectiveTime())) {
			return true;
		}
		for (Description d: c.getDescriptions()) {
			if (hasNewChanges(d)) {
				return true;
			}
		}
		//We won't check inferred modelling since that can change without an author
		//touching the concept
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			if (StringUtils.isEmpty(r.getEffectiveTime())) {
				return true;
			}
		}

		for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE)) {
			if (StringUtils.isEmpty(a.getEffectiveTime())) {
				return true;
			}
		}

		for (InactivationIndicatorEntry i : c.getInactivationIndicatorEntries(ActiveState.ACTIVE)) {
			if (StringUtils.isEmpty(i.getEffectiveTime())) {
				return true;
			}
		}
		return false;
	}

	public static boolean hasNewChanges(Description d) {
		if (StringUtils.isEmpty(d.getEffectiveTime())) {
			return true;
		}
		for (LangRefsetEntry l : d.getLangRefsetEntries()) {
			if (StringUtils.isEmpty(l.getEffectiveTime())) {
				return true;
			}
		}
		return false;
	}

	/**Initial very basic implementation will not consider grouping 
	 */
	public static String getModelDifferences(Concept lhs, Concept rhs, CharacteristicType charType) {
		String differences = "";
		for (Relationship lhsR : lhs.getRelationships(charType, ActiveState.ACTIVE)) {
			//Do we have this relationship on the RHS?
			if (rhs.getRelationships(charType, lhsR.getType(), lhsR.getTarget(), ActiveState.ACTIVE).isEmpty()) {
				differences += lineFeed(differences) + "Missing: " + lhsR.toShortPrettyString();
			}
		}
		//Now do we have any on the other side that are considered 'extra'?
		for (Relationship rhsR : rhs.getRelationships(charType, ActiveState.ACTIVE)) {
			//Do we have this relationship on the RHS?
			if (lhs.getRelationships(charType, rhsR.getType(), rhsR.getTarget(), ActiveState.ACTIVE).isEmpty()) {
				differences += lineFeed(differences) + "Additional: " + rhsR.toShortPrettyString();
			}
		}
		return differences;
	}

	private static String lineFeed(String str) {
		if (!str.isEmpty()) {
			return ",\n";
		}
		return "";
	}
	
	public static String toExpression(DefinitionStatus definitionStatus, List<RelationshipGroup> groups) {
		String expression = definitionStatus.equals(DefinitionStatus.FULLY_DEFINED) ? "=== " : "<<< ";
		
		//Parents may not be maintained if we're working with a loaded concept.
		//Work with active IS_A relationships instead
		for (RelationshipGroup group : groups) {
			expression += group.getRelationships().stream()
					.filter(r -> r.getType().equals(IS_A))
					.map(r -> r.getTarget())
					.map(Object::toString)
					.collect(Collectors.joining (" + \n"));
		}

		expression += " : \n";

		boolean isFirstGroup = true;
		for (RelationshipGroup group : groups) {
			if (group.isAllISA()) {
				continue;
			}
			
			if (isFirstGroup) {
				isFirstGroup = false;
			} else {
				expression += ",\n";
			}
			expression += group.isGrouped() ? "{" : "";
			expression += group.getRelationships().stream()
					.filter(r -> !r.getType().equals(IS_A))
					.map(p -> "  " + p)
					.collect(Collectors.joining (",\n"));
			expression += group.isGrouped() ? " }" : "";
		}
		return expression;
	}

	public static String correctRoundedCheckDigit(String sctId) throws TermServerScriptException {
		if (!SnomedUtils.isValid(sctId, PartitionIdentifier.CONCEPT, false)) {
			String idWithoutCheck = sctId.substring(0, sctId.length() -1);
			try {
				String msg = sctId + " rounding failure corrected to: ";
				sctId = idWithoutCheck + verhoeffCheck.calculate(idWithoutCheck);
				System.err.println(msg + sctId);
			} catch (CheckDigitException e) {
				throw new TermServerScriptException(e);
			}
		}
		return sctId;
	}

	public static Concept findCommonAncestor(Set<Concept> concepts, AncestorsCache ancestorsCache) throws TermServerScriptException {
		//Find the first common ancestor of these equiDepthConcepts
		Set<Concept> commonAncestors = null;
		for (Concept c : concepts) {
			if (commonAncestors == null) {
				commonAncestors = new HashSet<>(ancestorsCache.getAncestors(c));
			} else {
				Set<Concept> theseAncestors = ancestorsCache.getAncestors(c);
				if (!theseAncestors.contains(ROOT_CONCEPT)) {
					throw new TermServerScriptException(c + " failed to report the root concept as an ancestor");
				}
				commonAncestors.retainAll(theseAncestors);
			}
		}
		return findDeepestConcept(commonAncestors);
	}

	public static Concept findDeepestConcept(Collection<Concept> concepts) throws TermServerScriptException {
		return findDeepestConcept(concepts, true);
	}

	public static Concept findDeepestConcept(Collection<Concept> concepts, boolean ensureUnique) throws TermServerScriptException {
		if (concepts == null || concepts.size() == 0) {
			throw new TermServerScriptException("Request to find maximum depth, but no set of concepts given to work with");
		}
		
		//Find the greatest depth indicator
		Integer maximumDepth = null;
		for (Concept c : concepts) {
			if (maximumDepth == null || c.getDepth() > maximumDepth) {
				maximumDepth = c.getDepth();
			}
		}
		
		if (maximumDepth == null) {
			throw new TermServerScriptException("Unable to determine maximum depth from set of " + concepts.size() + " concepts");
		}

		//What all concepts are at that depth?
		final int maxDepth = maximumDepth;
		Set<Concept> siblings = concepts.stream()
				.filter(c -> c.getDepth() == maxDepth)
				.collect(Collectors.toSet());

		if (ensureUnique && siblings.size() != 1) {
			throw new TermServerScriptException("Unable to find single deepest concept from " + concepts);
		}
		return siblings.iterator().next();
	}

	public static Concept findShallowestConcept(Collection<Concept> concepts, boolean ensureUnique) throws TermServerScriptException {
		//Find the greatest depth indicator
		Integer minimumDepth = null;
		for (Concept c : concepts) {
			if (minimumDepth == null || c.getDepth() < minimumDepth) {
				minimumDepth = c.getDepth();
			}
		}

		//What all concepts are at that depth?
		final int minDepth = minimumDepth;
		Set<Concept> siblings = concepts.stream()
				.filter(c -> c.getDepth() == minDepth)
				.collect(Collectors.toSet());

		if (ensureUnique && siblings.size() != 1) {
			throw new TermServerScriptException("Unable to find single shallowest concept from " + concepts);
		}
		return siblings.iterator().next();
	}

	public static RelationshipGroup createRelationshipGroup(GraphLoader gl, String[][] concepts) throws TermServerScriptException {
		RelationshipGroup group = new RelationshipGroup(NOT_SET);
		for (String[] concept : concepts) {
			group.addRelationship(createRelationshipTemplate(gl, concept[0], concept[1]));
		}
		return group;
	}

	public static RelationshipTemplate createRelationshipTemplate(GraphLoader gl, String c1, String c2) throws TermServerScriptException {
		return new  RelationshipTemplate(gl.getConcept(c1), gl.getConcept(c2));
	}

	public static Set<Concept> getHistoricalAssocationTargets(Concept c, GraphLoader gl) throws TermServerScriptException {
		Set<Concept> targets = new HashSet<>();
		for (AssociationEntry h : c.getAssociationEntries(ActiveState.ACTIVE)) {
			targets.add(gl.getConcept(h.getTargetComponentId()));
		}
		return targets;
	}

	//The array structure returned as pairs shows components from the left either
	//changed or not present on the right, and visa versa.
	public static List<ComponentComparisonResult> compareComponents(Concept left, Concept right, Set<ComponentType> skipForComparison) {
		List<ComponentComparisonResult> changeSet = new ArrayList<>();
		//Find a match for every component, or list it.  Left is existing concept, right is the new one
		//There's no point in comparing Langrefset components just yet, because the new concept doesn't know the IDs
		//for the descriptions, so there's no way to match them up.
		//TODO work out the "best match" that we can say has been modified
		Collection<Component> leftComponents = SnomedUtils.getAllComponents(left)
				.stream()
				.filter(lc -> !skipForComparison.contains(lc.getComponentType()))
				.toList();
		Collection<Component> rightComponents = SnomedUtils.getAllComponents(right).stream()
				.filter(rc -> !skipForComparison.contains(rc.getComponentType()))
				.toList();

		//Once we've matched with a component, we don't need to keep checking it again.
		Set<Component> beenMatchedRight = new HashSet<>();

		nextLeftComponent:
		for (Component leftComponent : leftComponents) {
			for (Component rightComponent : rightComponents) {
				if (!beenMatchedRight.contains(rightComponent) && leftComponent.getClass().equals(rightComponent.getClass())) {
					if (leftComponent.matchesMutableFields(rightComponent)) {
						changeSet.add(new ComponentComparisonResult(leftComponent, rightComponent).matches());
						beenMatchedRight.add(rightComponent);
						continue nextLeftComponent;
					} else if (hasSingleType(leftComponent)
						|| areRefsetMembersForSameReferencedComponent(leftComponent, rightComponent)
						|| areAltIdsForSameSchemeAndReferencedComponent(leftComponent, rightComponent)) {
						//A modified OWL axiom will not match on mutable fields, but we'll consider them 'the same object' on the assumption that there will be only one
						//Similarly a concept may change from primitive to defined, but we'll consider them the same object.
						//We can also say this for two refset members that are for the same referenced component + same refset
						changeSet.add(new ComponentComparisonResult(leftComponent, rightComponent).differs());
						beenMatchedRight.add(rightComponent);
						continue nextLeftComponent;
					}
				}
			}
			//If we're here, then our left component (prior existing), no longer
			//has a matching component in the latest version and should be removed
			//unless the existing component is already inactive
			if (leftComponent.isActiveSafely()) {
				changeSet.add(new ComponentComparisonResult(leftComponent, null));
			}
		}

		nextRightComponent:
		for (Component rightComponent : rightComponents) {
			if (beenMatchedRight.contains(rightComponent)) {
				continue;
			}
			for (Component leftComponent : leftComponents) {
				if (rightComponent.getClass().equals(leftComponent.getClass())) {
					if (rightComponent.matchesMutableFields(leftComponent)
							|| hasSingleType(rightComponent)
							|| areRefsetMembersForSameReferencedComponent(leftComponent, rightComponent)) {
						//We don't need to store this, since we'll already have a left -> right 
						//comparison that matched.
						continue nextRightComponent;
					}
				}
			}
			//If we're here, then our right component (new) did not previously exist
			//so will be added as new
			changeSet.add(new ComponentComparisonResult(null, rightComponent));
		}
		return changeSet;
	}

	private static boolean areRefsetMembersForSameReferencedComponent(Component leftComponent, Component rightComponent) {
		//If these two components are refset members, and they are for the same referenced component, then they are considered the same
		//basic refset member, although the values contained may differ
		return (leftComponent instanceof RefsetMember leftRM && rightComponent instanceof RefsetMember rightRM
			&& leftRM.getRefsetId().equals(rightRM.getRefsetId())
			&& leftRM.getReferencedComponentId().equals(rightRM.getReferencedComponentId()));
	}

	private static boolean areAltIdsForSameSchemeAndReferencedComponent(Component leftComponent, Component rightComponent) {
		//If these two components are refset members, and they are for the same referenced component, then they are considered the same
		//basic refset member, although the values contained may differ
		return (leftComponent instanceof AlternateIdentifier leftRM && rightComponent instanceof AlternateIdentifier rightRM
				&& leftRM.getIdentifierSchemeId().equals(rightRM.getIdentifierSchemeId())
				&& leftRM.getReferencedComponentId().equals(rightRM.getReferencedComponentId()));
	}

	private static boolean hasSingleType(Component c) {
		return c instanceof AxiomEntry || c instanceof Concept || c instanceof AlternateIdentifier;
	}

	public static String translateActiveStateToRF2(Component c) {
		return c.isActiveSafely() ? "1" : "0";
	}

	public static List<String> extractSCTIDs(String input) {
		// List to store the extracted numbers
		List<String> sctIds = new ArrayList<>();

		// Create a Matcher object
		Matcher matcher = sctIdPattern.matcher(input);

		// Find and add all matches to the list
		while (matcher.find()) {
			sctIds.add(matcher.group());
		}

		// Return the list of extracted numbers
		return sctIds;
	}

	public static String translateActiveState(Component c) {
		return c.isActiveSafely() ? "Y" : "N";
	}

	public static boolean isTargetDescriptionType(List<String> targetTypes, Description d) {
		if (d.getType().equals(DescriptionType.FSN) && targetTypes.contains("FSN")) {
			return true;
		}

		if (d.getType().equals(DescriptionType.SYNONYM) && d.isPreferred() && targetTypes.contains("PT")) {
			return true;
		}

		if (d.getType().equals(DescriptionType.SYNONYM) && !d.isPreferred() && targetTypes.contains("SYN")) {
			return true;
		}

		return d.getType().equals(DescriptionType.TEXT_DEFINITION) && targetTypes.contains("DEFN");
	}

	public static void setAllComponentsDirty(Concept c, boolean includeStatedRels) {
		getAllComponents(c, includeStatedRels).stream().forEach(Component::setDirty);
	}

	/**
	 * Works with Acceptability maps
	 * If a term is preferred in one dialect, make it preferred in the other.
	 * If it's acceptable, make it acceptable in the other.
	 */
	public static void levelUpAcceptability(Description d) {
		if (d.isPreferred()) {
			d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_BOTH));
		} else {
			d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
		}
	}

	public static String getPartition(String id) {
		//return the last 3 characters of the SCTID, but not the last character
		return id.substring(id.length() - 3, id.length() - 1);
	}

	public static Long getSequenceNumber(String id) {
		//Do we expect to have a namespace?
		int chopPoint = id.length() - 3;
		if (getPartition(id).charAt(0) == '1') {
			chopPoint -= 7;
		}
		return Long.parseLong(id.substring(0, chopPoint));
	}

	public static String getAlternateIdentifiers(Concept c, boolean includeScheme) {
		return c.getAlternateIdentifiers().stream()
				.map(a -> (includeScheme? "" : (a.getIdentifierSchemeId() +  ": ")) + a.getAlternateIdentifier())
				.collect(Collectors.joining(", \n"));
	}
}
