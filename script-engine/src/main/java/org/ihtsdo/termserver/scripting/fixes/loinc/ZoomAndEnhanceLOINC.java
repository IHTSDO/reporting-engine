package org.ihtsdo.termserver.scripting.fixes.loinc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.api.client.util.Charsets;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.CharSink;
import com.google.common.io.FileWriteMode;
import com.google.common.io.Files;

/**
 * Look through all LOINC expressions and fix whatever needs worked on
 */
public class ZoomAndEnhanceLOINC extends BatchFix {
	
	enum REL_PART {Type, Target};
	
	private static String LOINC_CONTENT_FILESTR = "G:\\My Drive\\018_Loinc\\2021\\LOINC_2020_CONTENT.tsv";
	private static String TARGET_BRANCH = "MAIN/SNOMEDCT-LOINC/LOINC2020";
	private static String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	
	private static enum LoincCol { LOINC_NUM,COMPONENT,PROPERTY,TIME_ASPCT,SYSTEM,SCALE_TYP,METHOD_TYP,CLASS,VersionLastChanged,CHNG_TYPE,DefinitionDescription,STATUS,CONSUMER_NAME,CLASSTYPE,FORMULA,EXMPL_ANSWERS,SURVEY_QUEST_TEXT,SURVEY_QUEST_SRC,UNITSREQUIRED,SUBMITTED_UNITS,RELATEDNAMES2,SHORTNAME,ORDER_OBS,CDISC_COMMON_TESTS,HL7_FIELD_SUBFIELD_ID,EXTERNAL_COPYRIGHT_NOTICE,EXAMPLE_UNITS,LONG_COMMON_NAME,UnitsAndRange,EXAMPLE_UCUM_UNITS,EXAMPLE_SI_UCUM_UNITS,STATUS_REASON,STATUS_TEXT,CHANGE_REASON_PUBLIC,COMMON_TEST_RANK,COMMON_ORDER_RANK,COMMON_SI_TEST_RANK,HL7_ATTACHMENT_STRUCTURE,EXTERNAL_COPYRIGHT_LINK,PanelType,AskAtOrderEntry,AssociatedObservations,VersionFirstReleased,ValidHL7AttachmentRequest,DisplayName }
	private static enum RefsetCol { ID,EFFECTIVETIME,ACTIVE,MODULEID,REFSETID,REFERENCEDCOMPONENTID,MAPTARGET,EXPRESSION,DEFINITIONSTATUSID,CORRELATIONID,CONTENTORIGINID }
	private static String DEPRECATED = "DEPRECATED";
	private static String DISCOURAGED = "DISCOURAGED";
	private static String ACTIVE = "ACTIVE";
	
	private Map<String, List<String>> loincFileMap;
	private BiMap<String, String> fsnBestLoincMap;
	private Map<String, List<String>> fsnAllLoincMap;
	private Map<String, List<String>> refsetFileMap;
	private Map<String, Concept> loincConceptMap;
	private Set<String> deprecated;
	private Set<Concept> expectedTypeChanges = new HashSet<>();
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	private boolean createLoincConceptMap = false;
	
	protected ZoomAndEnhanceLOINC(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ZoomAndEnhanceLOINC fix = new ZoomAndEnhanceLOINC(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.selfDetermining = false;
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
				"SCTID, FSN, Semtag, Issue, Detail, Pub Expression, LOINC2020",
				"SCTID, FSN, Semtag, LOINC2020 LoincNum, 2_70 LoincNum, Issue, Detail 1, Detail 2",
				"Issue, FSN, Item 1, Item 2, Usage",
				"Item, Count"
		};
		String[] tabNames = new String[] {
				"Updates to LOINC2020",
				"Published vs LOINC2020",
				"LOINC2020 vs LOINC_2_70",
				"LOINC_2_70 Issues",
				"Summary Counts"
		};
		super.postInit(tabNames, columnHeadings, false);
		
		info("Mapping current LOINC content");
		mapLoincContent();
		
		loadFiles();
		expectedTypeChanges.add(gl.getConcept("704318007 |Property type (attribute)|"));
	}

	private void mapLoincContent() throws TermServerScriptException {
		try {
			//Load the LOINC file

			File checkFile = new File(LOINC_CONTENT_FILESTR);
			if (checkFile.canRead()) {
				loincConceptMap = new HashMap<>();
				info ("Loading " + LOINC_CONTENT_FILESTR);
				try (BufferedReader br = new BufferedReader(new FileReader(LOINC_CONTENT_FILESTR))) {
					String line;
					while ((line = br.readLine()) != null) {
						String[] items = line.split(TAB);
						String[] conceptParts = SnomedUtils.deconstructSCTIDFsn(items[1]);
						Concept loincConcept = new Concept(conceptParts[0], conceptParts[1]);
						loincConceptMap.put(items[0], loincConcept);
					}
				}
			} else {
				createLoincConceptMap = true;
				info (LOINC_CONTENT_FILESTR + " not yet available, will create for next time");
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
	}

	private void loadFiles() throws TermServerScriptException {
		loincFileMap = new HashMap<>();
		fsnBestLoincMap = HashBiMap.create();
		refsetFileMap = new HashMap<>();
		deprecated = new HashSet<>();
		fsnAllLoincMap = new HashMap<>();
		Set<String> checkReplacementAvailable = new HashSet<>();
		try {
			//Load the LOINC file
			String fileStr = "G:\\My Drive\\018_Loinc\\2021\\loinc_2_70.csv";
			info ("Loading " + fileStr);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(fileStr))) {
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
						
						String usedHere = "Unknown";
						if (loincConceptMap != null) {
							usedHere = loincConceptMap.containsKey(loincNum) ? loincConceptMap.get(loincNum).toString() : "No";
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
								String thisStatus = get(loincFileMap, loincNum, LoincCol.STATUS.ordinal());
								String thisReason = get(loincFileMap, loincNum, LoincCol.STATUS_REASON.ordinal());
								
								if (thisStatus.equals(DEPRECATED)) {
									deprecated.add(loincNum);
								}
								
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
									report(QUATERNARY_REPORT, "Same FSN, both Active", fsn, getDetails(loincNum), getDetails(origLoincNum), usedHere);
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
		
		for (String fsn : checkReplacementAvailable) {
			String bestLoincNum = fsnBestLoincMap.get(fsn);
			String status = get(loincFileMap, bestLoincNum, LoincCol.STATUS.ordinal());
			if (status.equals(ACTIVE)) {
				increment("Double deprecated loincNum subsequently replaced");
			} else {
				for (String loincNum : fsnAllLoincMap.get(fsn)) {
					String usedHere = "Unknown";
					if (loincConceptMap != null) {
						usedHere = loincConceptMap.containsKey(loincNum) ? loincConceptMap.get(loincNum).toString() : "No";
					}
					report(QUATERNARY_REPORT, "No active replacements found", fsn, getDetails(loincNum), "", usedHere);
				}
			}
		}
		
		try {
			//Load the Refset Expression file
			String fileStr = "G:\\My Drive\\018_Loinc\\2021\\der2_sscccRefset_LOINCExpressionAssociationSnapshot_INT_20170731.txt";
			info ("Loading " + fileStr);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(fileStr))) {
				String line;
				while ((line = br.readLine()) != null) {
					if (!isFirstLine) {
						List<String> items = Arrays.asList(line.split("\t"));
						String loincNum = items.get(RefsetCol.MAPTARGET.ordinal());
						refsetFileMap.put(loincNum, items);
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
		
		flushFiles(false, false);
	}
	
	private String getDetails(String loincNum) {
		return getDetails(loincNum, "\n");
	}

	private String getDetails(String loincNum, String separator) {
		if (!loincFileMap.containsKey(loincNum)) {
			return loincNum + " not found in 2_70";
		}
		String reason = get(loincFileMap, loincNum, LoincCol.STATUS_REASON.ordinal());
		return loincNum + separator +
		get(loincFileMap, loincNum, LoincCol.VersionLastChanged.ordinal()) + separator +
		get(loincFileMap, loincNum, LoincCol.STATUS.ordinal()) +
		(StringUtils.isEmpty(reason) ? "" : separator + reason);
	}

	private void increment(String key) {
		issueSummaryMap.merge(key.toString(), 1, Integer::sum);
	}

	private String get(Map<String, List<String>> source, String key, int idx) {
		List<String> row = source.get(key);
		//Have we yet to expand out this row?  Is costly, so working JIT
		if (row.size() == 1) {
			row = org.ihtsdo.termserver.scripting.util.StringUtils.csvSplit(row.get(0));
			source.put(key, row);
		}
		return row.get(idx);
	}

	
	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			//changesMade += addAttribute(task, loadedConcept);
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
	
	public void upgradeLOINCConcept(Concept c) throws TermServerScriptException {
		String loincNum = getLoincNumFromDescription(c);
		validateAgainstCurrentLOINC(c, loincNum);
		validateAgainstPublishedLOINC(c, loincNum);
		
		Set<Relationship> origRels = new HashSet<>(c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE));
		for (Relationship r : origRels) {
			Concept localType = gl.getConcept(r.getType().getId());
			Concept replaceType = replaceIfRequired(c, r, localType, REL_PART.Type);
			
			if (replaceType == null) {
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace type " + localType + " due to lack of historical association");
				return;
			} else if (replaceType.equals(MULTI_CONCEPT)) {
				String alternatives = getReplacements(localType).stream().map(rep -> rep.toString()).collect(Collectors.joining(", "));
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace type " + localType + " due to multiple historical associations", alternatives);
				return;
			}
			
			Concept localTarget = gl.getConcept(r.getTarget().getId());
			Concept replaceTarget = replaceIfRequired(c, r, localTarget, REL_PART.Target);
			if (replaceTarget == null) {
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace target " + localTarget + " due to lack of historical association");
				return;
			} else if (replaceTarget.equals(MULTI_CONCEPT)) {
				String alternatives = getReplacements(localTarget).stream().map(rep -> rep.toString()).collect(Collectors.joining(", "));
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace target " + localTarget + " due to multiple historical associations", alternatives);
				return;
			}
			
			if (!localType.equals(replaceType) || !localTarget.equals(replaceTarget)) {
				Relationship replaceRel = r.clone();
				replaceRel.setType(replaceType);
				replaceRel.setTarget(replaceTarget);
			}
		}
	}


	private void validateAgainstCurrentLOINC(Concept c, String loincNum) throws TermServerScriptException {
		//Did LOINC tell us about this loincNum?
		List<String> loincRow = loincFileMap.get(loincNum);
		String fsn = SnomedUtils.deconstructFSN(c.getFsn())[0];
		
		if (loincRow == null) {
			report(TERTIARY_REPORT, c, getDetails(loincNum), "",  "Loinc file did not feature LOINC_NUM");
		} else {
			//Verify that our FSN matches what's in LOINC_2_70
			String loincFSN = formLoincFSN(loincNum);
			if (!fsn.equalsIgnoreCase(loincFSN)) {
				report(TERTIARY_REPORT, c, getDetails(loincNum), "", "Local/LOINC FSN Mismatch", fsn, loincFSN);
			}
		}
		
		//Was there, in fact, more than one row with this FSN?
		List<String> loincNums = fsnAllLoincMap.get(fsn);
		if (loincNums != null && loincNums.size() > 1) {
			//If the best row is what we've currently got, then it's not a problem.
			String problemIndicator = fsnBestLoincMap.get(fsn).equals(loincNum) ? " Not a problem." : " Is Problem";
			String details = loincNums.stream()
					.map(l -> getDetails(l, "|"))
					.collect(Collectors.joining("\n"));
			report(TERTIARY_REPORT, c, getDetails(loincNum), details, "Loinc file featured FSN " + loincNums.size() + " times." + problemIndicator);
		}
		
		//Can we find it, or a newer one via the FSN?
		String newLoincNum = fsnBestLoincMap.get(fsn);
		if (loincRow == null || newLoincNum == null || !loincNum.equals(newLoincNum)) {
			if (newLoincNum != null) {
				report(TERTIARY_REPORT, c, getDetails(loincNum), getDetails(newLoincNum), "Updated LOINC_NUM found via FSN");
			} else {
				report(TERTIARY_REPORT, c, getDetails(loincNum), "", "FSN could not be found in LOINC_2_70. That loinc num parts: ", formLoincFSN(loincNum));
			}
		}
		
		if (newLoincNum != null && !newLoincNum.equals(loincNum)) {
			String newTerm = LOINC_NUM_PREFIX + newLoincNum;
			Description oldDesc = getLoincNumDescription(c);
			String info = getDetails(loincNum);
			replaceDescription(null, c, oldDesc, newTerm, InactivationIndicator.OUTDATED, info);
		}
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

	private void validateAgainstPublishedLOINC(Concept c, String loincNum) throws TermServerScriptException {
		//Was this item originally published?
		if (!refsetFileMap.containsKey(loincNum)) {
			report(SECONDARY_REPORT, c, "Not Yet Published");
			increment("LOINC2020 not yet published");
			return;
		}
		String loinc2020Exp = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String expression = get(refsetFileMap, loincNum, RefsetCol.EXPRESSION.ordinal());
		String workingCopy = expression;
		//Check the focus concept matches
		int focusCut = workingCopy.indexOf(":");
		String focusConcept = workingCopy.substring(0, focusCut);
		workingCopy = workingCopy.substring(focusCut + 1);
		
		//Form a map from attribute types & targets
		BiMap<String, String> attributeMap = formAttributeMap(c, workingCopy);
		
		Set<Concept> parents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
		if (parents.size() > 1) {
			debug ("here");
		}
		Concept parent = parents.iterator().next();
		if (!focusConcept.contentEquals(parent.getConceptId())) {
			report(SECONDARY_REPORT, c, "Mismatched Focus Concept", focusConcept, parent);
		}
		//Check we've got every relationship and nothing left over
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getType().equals(IS_A)) {
				continue;
			}
			String typeId = r.getType().getConceptId();
			String targetId = r.getTarget().getConceptId();
			String relStr = typeId + "=" + targetId;
			
			if (workingCopy.contains(relStr)) {
				workingCopy = workingCopy.replace(relStr, "");
			} else {
				//Has this attribute type been replaced?  Check for same attribute value
				if (attributeMap.containsValue(targetId)) {
					Concept publishedType = gl.getConcept(attributeMap.inverse().get(targetId));
					String publishedRel = publishedType.getConceptId() + "=" + targetId;
					if (!publishedType.isActive()) {
						//Is this one of our expected changes?  Just count if so
						if (expectedTypeChanges.contains(publishedType)) {
							increment("Published Relationship updated: " + publishedType);
						} else {
							report (SECONDARY_REPORT, c, "Published Relationship updated (type change)", SnomedUtils.populateFSNs(publishedRel) + "\n->\n" + r, expression.replaceAll(",", ",\n"), loinc2020Exp);
						}
						workingCopy = workingCopy.replace(publishedRel, "");
					} else {
						report (SECONDARY_REPORT, c, "Unexpected Situation (type)", r, expression, loinc2020Exp);
					}
				} else {
					//Has this attribute VALUE been replaced?  Check for same attribute type
					if (attributeMap.containsKey(typeId)) {
						Concept publishedValue = gl.getConcept(attributeMap.get(typeId));
						String publishedRel = typeId + "=" + publishedValue.getConceptId();
						if (!publishedValue.isActive()) {
							report (SECONDARY_REPORT, c, "Published REL updated (value change)", SnomedUtils.populateFSNs(publishedRel) + "\n->\n" + r, expression.replaceAll(",", ",\n"), loinc2020Exp);
							workingCopy = workingCopy.replace(publishedRel, "");
						} else {
							report (SECONDARY_REPORT, c, "Unexpected Situation (value)", r, expression, loinc2020Exp);
						}
					}
				}	
			}
		}
		if (workingCopy.length() > 16) {
			report (SECONDARY_REPORT, c, "Published Rel Variance", SnomedUtils.populateFSNs(workingCopy), "", loinc2020Exp);
		}
	}

	private BiMap<String, String> formAttributeMap(Concept c, String expression) {
		BiMap<String, String> attributeMap = HashBiMap.create();
		for (String pair : expression.split(",")) {
			String[] parts = pair.split("=");
			if (attributeMap.containsValue(parts[1])) {
				debug (c + " has two attributes with target " + parts[1] + ": " + parts[0] + " + " + attributeMap.inverse().get(parts[1]));
			} else if (attributeMap.containsKey(parts[0])) {
				debug (c + " has two attribute types " + parts[0]);
			} else {
				attributeMap.put(parts[0], parts[1]);
			}
		}
		return attributeMap;
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

	private Concept replaceIfRequired(Concept c, Relationship r, Concept local, REL_PART relPart) throws TermServerScriptException {
		Concept replacement = local;
		if (!local.isActive()) {
			List<Concept> replacements = getReplacements(local);
			if (replacements.size() == 0) {
				return null;
			} else if (replacements.size() > 1) {
				return MULTI_CONCEPT;
			}
			replacement = replacements.get(0);
			report((Task)null, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, "Inactive rel " + relPart.toString() + " " + local + " replaced by " + replacement);
		}
		return replacement;
	}
	
	private List<Concept> getReplacements(Concept inactiveConcept) throws TermServerScriptException {
		Set<String> assocs = inactiveConcept.getAssociationTargets().getSameAs();
		if (assocs.size() > 0) {
			return assocs.stream()
					.map(s -> gl.getConceptSafely(s))
					.collect(Collectors.toList());
		}
		
		assocs = inactiveConcept.getAssociationTargets().getReplacedBy();
		if (assocs.size() > 0) {
			return assocs.stream()
					.map(s -> gl.getConceptSafely(s))
					.collect(Collectors.toList());
		}
		
		assocs = inactiveConcept.getAssociationTargets().getPossEquivTo();
		if (assocs.size() > 0) {
			return assocs.stream()
					.map(s -> gl.getConceptSafely(s))
					.collect(Collectors.toList());
		}
		
		return new ArrayList<>();
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		File loincConceptFile = new File(LOINC_CONTENT_FILESTR);
		CharSink chs = Files.asCharSink(loincConceptFile, Charsets.UTF_8, FileWriteMode.APPEND);
		//Don't use local copies of concepts, they might not exist
		for (Concept c : findConceptsByCriteria("module=715515008", TARGET_BRANCH, false)) {
			Concept loadedConcept = loadConcept(c, TARGET_BRANCH);
			if (createLoincConceptMap) {
				String line = getLoincNumFromDescription(loadedConcept) 
						+ "\t" + loadedConcept.toString() + "\r\n";
				try {
					chs.write(line);
				} catch (IOException e) {
					throw new TermServerScriptException("Unable to write to " + LOINC_CONTENT_FILESTR, e);
				}
			}
			upgradeLOINCConcept(loadedConcept);
		}
		return componentsToProcess;
	}
	
	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (QUINARY_REPORT, (Component)null, e.getKey(), e.getValue()));
	}
}
