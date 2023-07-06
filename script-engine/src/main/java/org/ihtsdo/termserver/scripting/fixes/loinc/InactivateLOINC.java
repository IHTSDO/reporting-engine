package org.ihtsdo.termserver.scripting.fixes.loinc;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
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
public class InactivateLOINC extends BatchFix {
	
	enum REL_PART {Type, Target};
	
	//private static String loincFile = "G:\\My Drive\\018_Loinc\\2021\\loinc_2_70.csv";
	private static String loincFile = "/Volumes/GoogleDrive/My Drive/018_Loinc/2021/Loinc_2_70.csv";
	
	private static String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	
	private static enum LoincCol { LOINC_NUM,COMPONENT,PROPERTY,TIME_ASPCT,SYSTEM,SCALE_TYP,METHOD_TYP,CLASS,VersionLastChanged,CHNG_TYPE,DefinitionDescription,STATUS,CONSUMER_NAME,CLASSTYPE,FORMULA,EXMPL_ANSWERS,SURVEY_QUEST_TEXT,SURVEY_QUEST_SRC,UNITSREQUIRED,SUBMITTED_UNITS,RELATEDNAMES2,SHORTNAME,ORDER_OBS,CDISC_COMMON_TESTS,HL7_FIELD_SUBFIELD_ID,EXTERNAL_COPYRIGHT_NOTICE,EXAMPLE_UNITS,LONG_COMMON_NAME,UnitsAndRange,EXAMPLE_UCUM_UNITS,EXAMPLE_SI_UCUM_UNITS,STATUS_REASON,STATUS_TEXT,CHANGE_REASON_PUBLIC,COMMON_TEST_RANK,COMMON_ORDER_RANK,COMMON_SI_TEST_RANK,HL7_ATTACHMENT_STRUCTURE,EXTERNAL_COPYRIGHT_LINK,PanelType,AskAtOrderEntry,AssociatedObservations,VersionFirstReleased,ValidHL7AttachmentRequest,DisplayName }
	private static String DEPRECATED = "DEPRECATED";
	private static String DISCOURAGED = "DISCOURAGED";
	private static String ACTIVE = "ACTIVE";
	
	private Map<String, List<String>> loincFileMap;
	private BiMap<String, String> fsnBestLoincMap;
	private Map<String, List<String>> fsnAllLoincMap;
	private Map<String, String> deprecatedMap;
	private Map<String, String> discouragedMap;
	
	protected InactivateLOINC(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		InactivateLOINC fix = new InactivateLOINC(null);
		try {
			ReportSheetManager.targetFolderId = "1yF2g_YsNBepOukAu2vO0PICqJMAyURwh";  //LOINC
			fix.populateEditPanel = false;
			fix.selfDetermining = false;
			fix.populateEditPanel = false;
			fix.reportNoChange = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.stateComponentType = false;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, Severity, Action, LOINC Num, Details, , , "};
		String[] tabNames = new String[] {"LOINC2020 Inactivations"};
		super.postInit(tabNames, columnHeadings, false);
		loadFiles();
	}

	private void loadFiles() throws TermServerScriptException {
		loincFileMap = new HashMap<>();
		fsnBestLoincMap = HashBiMap.create();
		deprecatedMap = new HashMap<>();
		discouragedMap = new HashMap<>();
		fsnAllLoincMap = new HashMap<>();
		Set<String> checkReplacementAvailable = new HashSet<>();
		try {
			//Load the LOINC file
			info ("Loading " + loincFile);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(loincFile))) {
				String origline;
				while ((origline = br.readLine()) != null) {
					if (!isFirstLine) {
						String line = origline.replaceAll("\"", "");
						//Parsing the string takes too long, we'll do it JIT
						int cutOne = line.indexOf(',');
						String loincNum = line.substring(0,cutOne);
						loincFileMap.put(loincNum, Collections.singletonList(origline));
						
						//Columns B to G are used to generate the FSN
						int cutTwo = StringUtils.ordinalIndexOf(line, ",", 7);
						String fsn = line.substring(cutOne + 1, cutTwo);
						fsn = fsn.replaceAll(",", ":").replaceAll("::", ":");
						if (fsn.endsWith(":")) {
							fsn = fsn.substring(0, fsn.length() - 1);
						}
						
						//Maintain a map of all loincNums associate with this FSN, although we'll
						//separately store a map with our preferred row
						List<String> allLoincNums = fsnAllLoincMap.get(fsn);
						if (allLoincNums == null) {
							allLoincNums = new ArrayList<>();
							fsnAllLoincMap.put(fsn, allLoincNums);
						}
						allLoincNums.add(loincNum);
						
						String thisStatus = get(loincFileMap, loincNum, LoincCol.STATUS.ordinal());
						if (thisStatus.equals(DEPRECATED)) {
							String statusText = get(loincFileMap, loincNum, LoincCol.STATUS_TEXT.ordinal());
							deprecatedMap.put(loincNum, statusText);
						} else if (thisStatus.equals(DISCOURAGED)) {
							String statusText = get(loincFileMap, loincNum, LoincCol.STATUS_TEXT.ordinal());
							discouragedMap.put(loincNum, statusText);
						}
						
						if (!fsnBestLoincMap.containsKey(fsn) && !fsnBestLoincMap.containsValue(loincNum)) {
							fsnBestLoincMap.put(fsn, loincNum);
						} else {
							if (allLoincNums.size() > 1) {
								String duplicates = allLoincNums.stream()
										.filter(s -> !s.equals(loincNum))
										.collect(Collectors.joining(", "));
								debug ("Duplicate keys " + loincNum + " = '" + fsn + "' with " + duplicates);
							}
							
							//A duplicate FSN will usually have a later replacement with a new LOINCNum
							if (fsnBestLoincMap.containsKey(fsn)) {
								String origLoincNum = fsnBestLoincMap.get(fsn);
								String origStatus = get(loincFileMap, origLoincNum, LoincCol.STATUS.ordinal());
								
								//Is the this version newer than what we stored
								//Version doesn't matter as much as which LoincNum is still active
								if (thisStatus.equals(DEPRECATED) && (origStatus.equals(ACTIVE) || origStatus.equals(DISCOURAGED))) {
									//We can leave our current 'best' loincNum in place
								} else if ((thisStatus.equals(ACTIVE) || thisStatus.equals(DISCOURAGED)) && origStatus.equals(DEPRECATED)) {
									//In this case we want to replace the originally stored value
									fsnBestLoincMap.replace(fsn, loincNum);
								} else if (thisStatus.equals(ACTIVE) && origStatus.equals(ACTIVE)) {
									//report(QUATERNARY_REPORT, "Same FSN, both Active", fsn, getDetails(loincNum), getDetails(origLoincNum), usedHere);
								} else if (thisStatus.equals(DEPRECATED) && origStatus.equals(DEPRECATED)) {
									//Store this FSN to see if we find a replacement before the end of the file
									checkReplacementAvailable.add(fsn);
								}
							}
						}
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
		
		flushFiles(false);
	}

	private String get(Map<String, List<String>> source, String key, int idx) {
		List<String> row = source.get(key);
		//Have we yet to expand out this row?  Is costly, so working JIT
		if (row.size() == 1) {
			row = CVSUtils.csvSplit(row.get(0));
			source.put(key, row);
		}
		return row.get(idx);
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			String loincNum = getLoincNumFromDescription(c);
			inactivateConcept(t, c, null, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, loincNum);
		} catch (ValidationFailure v) {
			report(t, c, v);
		} catch (Exception e) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	

	private String getLoincNumFromDescription(Concept c) throws TermServerScriptException {
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
			if (c.getModuleId().equals(SCTID_LOINC_PROJECT_MODULE)) {
				String loincNum = getLoincNumFromDescription(c);
				String thisStatus = get(loincFileMap, loincNum, LoincCol.STATUS.ordinal());
				if (thisStatus.equals(DISCOURAGED)) {
					componentsToProcess.add(c);
				}
			}
		}
		return componentsToProcess;
	}
}
