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
 * Look through all LOINC expressions and fix whatever needs worked on
 * LOINC-384
 */
public class UpdateLOINC extends BatchFix {
	
	enum REL_PART {Type, Target};
	
	private static String loincFile = "G:\\My Drive\\018_Loinc\\2021\\loinc_2_70.csv";
	//private static String loincFile = "/Volumes/GoogleDrive/My Drive/018_Loinc/2021/Loinc_2_70.csv";
	
	private static String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	
	private static enum LoincCol { LOINC_NUM,COMPONENT,PROPERTY,TIME_ASPCT,SYSTEM,SCALE_TYP,METHOD_TYP,CLASS,VersionLastChanged,CHNG_TYPE,DefinitionDescription,STATUS,CONSUMER_NAME,CLASSTYPE,FORMULA,EXMPL_ANSWERS,SURVEY_QUEST_TEXT,SURVEY_QUEST_SRC,UNITSREQUIRED,SUBMITTED_UNITS,RELATEDNAMES2,SHORTNAME,ORDER_OBS,CDISC_COMMON_TESTS,HL7_FIELD_SUBFIELD_ID,EXTERNAL_COPYRIGHT_NOTICE,EXAMPLE_UNITS,LONG_COMMON_NAME,UnitsAndRange,EXAMPLE_UCUM_UNITS,EXAMPLE_SI_UCUM_UNITS,STATUS_REASON,STATUS_TEXT,CHANGE_REASON_PUBLIC,COMMON_TEST_RANK,COMMON_ORDER_RANK,COMMON_SI_TEST_RANK,HL7_ATTACHMENT_STRUCTURE,EXTERNAL_COPYRIGHT_LINK,PanelType,AskAtOrderEntry,AssociatedObservations,VersionFirstReleased,ValidHL7AttachmentRequest,DisplayName }
	private static String DEPRECATED = "DEPRECATED";
	private static String DISCOURAGED = "DISCOURAGED";
	private static String ACTIVE = "ACTIVE";
	
	private Map<String, List<String>> loincFileMap;
	private BiMap<String, String> fsnBestLoincMap;
	private Map<String, List<String>> fsnAllLoincMap;
	private Map<String, String> deprecatedMap;
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	
	protected UpdateLOINC(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		UpdateLOINC fix = new UpdateLOINC(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
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
			fix.populateSummaryTab();
		} finally {
			fix.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"TaskId, TaskDesc,SCTID, FSN, SemTag, Severity, Action, Detail, Details, , , ",
				"SCTID, FSN, SemTag, LoincNum, Issue, Detail, Detail, Details",
				"Item, Count"
		};
		String[] tabNames = new String[] {
				"Updates to LOINC2020",
				"Issues Encountered",
				"Summary Counts"
		};
		super.postInit(tabNames, columnHeadings, false);
		loadFiles();
	}

	private void loadFiles() throws TermServerScriptException {
		loincFileMap = new HashMap<>();
		fsnBestLoincMap = HashBiMap.create();
		deprecatedMap = new HashMap<>();
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
								String thisReason = get(loincFileMap, loincNum, LoincCol.STATUS_REASON.ordinal());
								
								//Is the this version newer than what we stored
								//Version doesn't matter as much as which LoincNum is still active
								if (thisStatus.equals(DEPRECATED) && (origStatus.equals(ACTIVE) || origStatus.equals(DISCOURAGED))) {
									increment("Larger loincNum deprecated due to " + thisReason);
									//We can leave our current 'best' loincNum in place
								} else if ((thisStatus.equals(ACTIVE) || thisStatus.equals(DISCOURAGED)) && origStatus.equals(DEPRECATED)) {
									increment("Smaller loincNum deprecated due to " + thisReason);
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
		
		flushFiles(false, false);
	}

	private void increment(String key) {
		issueSummaryMap.merge(key.toString(), 1, Integer::sum);
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
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade += upgradeLOINCConcept(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	public int upgradeLOINCConcept(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		String loincNum = getLoincNumFromDescription(c);
		if (deprecatedMap.containsKey(loincNum)) {
			report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, loincNum, "LOINC Num marked as deprecated", deprecatedMap.get(loincNum));
		}
		return changesMade;
	}
	
	private void validateLoincConcept(Concept c) throws TermServerScriptException {
		String loincNum = getLoincNumFromDescription(c);
		
		//Compare FSN formed from LOINC Parts against what we have here
		String loincFSN = formLoincFSN(loincNum);
		
		//We use Ratio instead of IgE/IgE.total:AFr
		loincFSN = loincFSN.replace("IgE/IgE.total:AFr", "IgE:Ratio");
		
		if (!loincFSN.toLowerCase().equals(c.getPreferredSynonym().toLowerCase())) {
			String diff = org.ihtsdo.otf.utils.StringUtils.differenceCaseInsensitive(c.getPreferredSynonym(), loincFSN);
			report(SECONDARY_REPORT, c, loincNum, "LOINC Parts mismatch", loincFSN, diff);
		}
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
	
	private String formLoincFSN(String loincNum) {
		String fsn = "";
		for (int idx=1; idx<7; idx++) {
			fsn += get(loincFileMap, loincNum, idx);
			if (idx < 6) {
				fsn += ":";
			}
		}
		//Did we get anything for that last field
		if (fsn.endsWith(":")) {
			fsn = fsn.substring(0, fsn.length()-1);
		}
		return fsn;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		//setQuiet(true);
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			if (c.isActive() && c.getModuleId().equals(SCTID_LOINC_MODULE)) {
				validateLoincConcept(c);
				//Only process component if we have changes to make
				if (upgradeLOINCConcept(null, c.cloneWithIds()) > 0) {
					//componentsToProcess.add(c);
				}
			}
		}
		//setQuiet(false);
		return componentsToProcess;
	}

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
	}
}
