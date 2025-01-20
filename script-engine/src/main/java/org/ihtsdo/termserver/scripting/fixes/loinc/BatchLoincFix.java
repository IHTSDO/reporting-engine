package org.ihtsdo.termserver.scripting.fixes.loinc;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.snomed.otf.script.utils.CVSUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * LOINC-394 Inactivate LOINC concepts marked as deprecated
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BatchLoincFix extends BatchFix {

	protected static final Logger LOGGER = LoggerFactory.getLogger(BatchLoincFix.class);

	protected static final String LOINC_FILE = "/Volumes/GoogleDrive/My Drive/018_Loinc/2021/Loinc_2_70.csv";
	
	protected static final String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	
	protected enum LoincCol { LOINC_NUM,COMPONENT,PROPERTY,TIME_ASPCT,SYSTEM,SCALE_TYP,METHOD_TYP,CLASS,VERSION_LAST_CHANGED,CHNG_TYPE,DEFINITION_DESCRIPTION,STATUS,CONSUMER_NAME,CLASSTYPE,FORMULA,EXMPL_ANSWERS,SURVEY_QUEST_TEXT,SURVEY_QUEST_SRC,UNITSREQUIRED,SUBMITTED_UNITS,RELATEDNAMES2,SHORTNAME,ORDER_OBS,CDISC_COMMON_TESTS,HL7_FIELD_SUBFIELD_ID,EXTERNAL_COPYRIGHT_NOTICE,EXAMPLE_UNITS,LONG_COMMON_NAME,UNITS_AND_RANGE,EXAMPLE_UCUM_UNITS,EXAMPLE_SI_UCUM_UNITS,STATUS_REASON,STATUS_TEXT,CHANGE_REASON_PUBLIC,COMMON_TEST_RANK,COMMON_ORDER_RANK,COMMON_SI_TEST_RANK,HL7_ATTACHMENT_STRUCTURE,EXTERNAL_COPYRIGHT_LINK,PANEL_TYPE,ASK_AT_ORDER_ENTRY,ASSOCIATED_OBSERVATIONS,VERSION_FIRST_RELEASED,VALID_HL7_ATTACHMENT_REQUEST,DISPLAY_NAME }
	protected static final String DEPRECATED = "DEPRECATED";
	protected static final String DISCOURAGED = "DISCOURAGED";
	protected static final String ACTIVE = "ACTIVE";
	
	protected Map<String, String> discouragedMap = new HashMap<>();
	protected Map<String, String> deprecatedMap = new HashMap<>();
	protected Map<String, List<String>> fsnAllLoincMap = new HashMap<>();
	protected BiMap<String, String> fsnBestLoincMap = HashBiMap.create();
	protected Map<String, List<String>>loincFileMap = new HashMap<>();
	protected Set<String> checkReplacementAvailable = new HashSet<>();
	
	protected BatchLoincFix(BatchFix clone) {
		super(clone);
	}

	public void standardExecution(String[] args) throws TermServerScriptException {
		try {
			ReportSheetManager.setTargetFolderId("1yF2g_YsNBepOukAu2vO0PICqJMAyURwh");  //LOINC
			populateEditPanel = false;
			reportNoChange = false;
			selfDetermining = true;
			runStandAlone = false;
			stateComponentType = false;
			init(args);
			loadProjectSnapshot(false);
			postInit();
			processFile();
		} finally {
			finish();
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, SemTag, Severity, Action, Detail, Details, , , "};
		String[] tabNames = new String[] {"LOINC2020 Inactivations"};
		super.postInit(tabNames, columnHeadings, false);
		loadFiles();
	}

	protected void loadFiles() throws TermServerScriptException {
		try {
			//Load the LOINC file
			LOGGER.info("Loading {}", LOINC_FILE);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(LOINC_FILE))) {
				String origline;
				while ((origline = br.readLine()) != null) {
					if (!isFirstLine) {
						processLine(origline);
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
		
		flushFiles(false);
	}

	private void processLine(String origline) {
		String line = origline.replace("\"", "");
		//Parsing the string takes too long, we'll do it JIT
		int cutOne = line.indexOf(',');
		String loincNum = line.substring(0,cutOne);
		
		loincFileMap.put(loincNum, Collections.singletonList(origline));
		
		String fsn = extractFsn(line, cutOne);
		//Maintain a map of all loincNums associate with this FSN, although we'll
		//separately store a map with our preferred row
		List<String> allLoincNums = fsnAllLoincMap.computeIfAbsent(fsn, k -> new ArrayList<>());
		allLoincNums.add(loincNum);
		
		String thisStatus = populateDeprecatedMap(loincNum);
		
		determineBestLoincNum(fsn, loincNum, thisStatus, allLoincNums);
		
	}

	private void determineBestLoincNum(String fsn, String loincNum, String thisStatus, List<String> allLoincNums) {
		if (!fsnBestLoincMap.containsKey(fsn) && !fsnBestLoincMap.containsValue(loincNum)) {
			fsnBestLoincMap.put(fsn, loincNum);
		} else {
			if (allLoincNums.size() > 1) {
				String duplicates = allLoincNums.stream()
						.filter(s -> !s.equals(loincNum))
						.collect(Collectors.joining(", "));
				LOGGER.debug("Duplicate keys {} = '{}' with {}", loincNum, fsn, duplicates);
			}
			
			//A duplicate FSN will usually have a later replacement with a new LOINCNum
			if (fsnBestLoincMap.containsKey(fsn)) {
				processDuplicateFsn(loincNum, fsn, thisStatus);
			}
		}
	}

	private void processDuplicateFsn(String loincNum, String fsn, String thisStatus) {
		String origLoincNum = fsnBestLoincMap.get(fsn);
		String origStatus = get(loincFileMap, origLoincNum, LoincCol.STATUS.ordinal());
		
		//Is this version newer than what we stored
		//Version doesn't matter as much as which LoincNum is still active
		if (thisStatus.equals(DEPRECATED) && (origStatus.equals(ACTIVE) || origStatus.equals(DISCOURAGED))) {
			//We can leave our current 'best' loincNum in place
		} else if ((thisStatus.equals(ACTIVE) || thisStatus.equals(DISCOURAGED)) && origStatus.equals(DEPRECATED)) {
			//In this case we want to replace the originally stored value
			fsnBestLoincMap.replace(fsn, loincNum);
		} else if (thisStatus.equals(ACTIVE) && origStatus.equals(ACTIVE)) {
			// report(QUATERNARY_REPORT, "Same FSN, both Active", fsn, getDetails(loincNum), getDetails(origLoincNum), usedHere)
		} else if (thisStatus.equals(DEPRECATED) && origStatus.equals(DEPRECATED)) {
			//Store this FSN to see if we find a replacement before the end of the file
			checkReplacementAvailable.add(fsn);
		}
		
	}

	private String populateDeprecatedMap(String loincNum) {
		String thisStatus = get(loincFileMap, loincNum, LoincCol.STATUS.ordinal());
		if (thisStatus.equals(DEPRECATED)) {
			String statusText = get(loincFileMap, loincNum, LoincCol.STATUS_TEXT.ordinal());
			deprecatedMap.put(loincNum, statusText);
		} else if (thisStatus.equals(DISCOURAGED)) {
			String statusText = get(loincFileMap, loincNum, LoincCol.STATUS_TEXT.ordinal());
			discouragedMap.put(loincNum, statusText);
		}
		return thisStatus;
	}

	private String extractFsn(String line, int cutOne) {
		//Columns B to G are used to generate the FSN
		int cutTwo = StringUtils.ordinalIndexOf(line, ",", 7);
		String fsn = line.substring(cutOne + 1, cutTwo);
		fsn = fsn.replace(",", ":").replace("::", ":");
		if (fsn.endsWith(":")) {
			fsn = fsn.substring(0, fsn.length() - 1);
		}
		return fsn;
	}

	protected String get(Map<String, List<String>> source, String key, int idx) {
		List<String> row = source.get(key);
		//Have we yet to expand out this row?  Is costly, so working JIT
		if (row.size() == 1) {
			row = CVSUtils.csvSplit(row.get(0));
			source.put(key, row);
		}
		return row.get(idx);
	}

	protected String getLoincNumFromDescription(Concept c) throws TermServerScriptException {
		return getLoincNumDescription(c).getTerm().substring(LOINC_NUM_PREFIX.length());
	}
	
	private Description getLoincNumDescription(Concept c) throws TermServerScriptException {
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().startsWith(LOINC_NUM_PREFIX)) {
				return d;
			}
		}
		throw new TermServerScriptException(c + " does not specify a LOINC num");
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.isActiveSafely() && c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE)) {
				String loincNum = getLoincNumFromDescription(c);
				String thisStatus = get(loincFileMap, loincNum, LoincCol.STATUS.ordinal());
				if (thisStatus.equals(DEPRECATED)) {
					componentsToProcess.add(c);
				}
			}
		}
		return componentsToProcess;
	}
}
