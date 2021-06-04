package org.ihtsdo.termserver.scripting.fixes.loinc;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * Look through all LOINC expressions and fix whatever needs worked on
 */
public class ZoomAndEnhanceLOINC extends BatchFix {
	
	enum REL_PART {Type, Target};
	
	private static String TARGET_BRANCH = "MAIN/SNOMEDCT-LOINC/LOINC2020";
	private static String LOINC_NUM_PREFIX = "LOINC Unique ID:";
	
	private static enum LoincCol { LOINC_NUM,COMPONENT,PROPERTY,TIME_ASPCT,SYSTEM,SCALE_TYP,METHOD_TYP,CLASS,VersionLastChanged,CHNG_TYPE,DefinitionDescription,STATUS,CONSUMER_NAME,CLASSTYPE,FORMULA,EXMPL_ANSWERS,SURVEY_QUEST_TEXT,SURVEY_QUEST_SRC,UNITSREQUIRED,SUBMITTED_UNITS,RELATEDNAMES2,SHORTNAME,ORDER_OBS,CDISC_COMMON_TESTS,HL7_FIELD_SUBFIELD_ID,EXTERNAL_COPYRIGHT_NOTICE,EXAMPLE_UNITS,LONG_COMMON_NAME,UnitsAndRange,EXAMPLE_UCUM_UNITS,EXAMPLE_SI_UCUM_UNITS,STATUS_REASON,STATUS_TEXT,CHANGE_REASON_PUBLIC,COMMON_TEST_RANK,COMMON_ORDER_RANK,COMMON_SI_TEST_RANK,HL7_ATTACHMENT_STRUCTURE,EXTERNAL_COPYRIGHT_LINK,PanelType,AskAtOrderEntry,AssociatedObservations,VersionFirstReleased,ValidHL7AttachmentRequest,DisplayName }
	private static enum RefsetCol { ID,EFFECTIVETIME,ACTIVE,MODULEID,REFSETID,REFERENCEDCOMPONENTID,MAPTARGET,EXPRESSION,DEFINITIONSTATUSID,CORRELATIONID,CONTENTORIGINID }
	private static String DEPRECATED = "DEPRECATED";
	private static String ACTIVE = "ACTIVE";
	
	private Map<String, List<String>> loincFileMap;
	private BiMap<String, String> fsnLoincMap;
	private Map<String, List<String>> refsetFileMap;
	private Set<String> deprecated;
	
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
				"SCTID, FSN, SemTag, ConceptType, Severity, Action, Detail, Details, , , ",
				"SCTID, FSN, Semtag, Issue, Detail, Pub Expression, LOINC2020",
				"SCTID, FSN, Semtag, LOINC_Num, Issue",
				"Issue, FSN, Item 1, Item 2, Detail 1, Detail 2, Additional"
		};
		String[] tabNames = new String[] {	
				"Updates to LOINC2020",
				"Published vs LOINC2020",
				"LOINC2020 vs LOINC_2_69",
				"LOINC_2_69 Issues"
		};
		super.postInit(tabNames, columnHeadings, false);
		loadFiles();
	}

	private void loadFiles() throws TermServerScriptException {
		loincFileMap = new HashMap<>();
		fsnLoincMap = HashBiMap.create();
		refsetFileMap = new HashMap<>();
		deprecated = new HashSet<>();
		try {
			//Load the LOINC file
			String fileStr = "G:\\My Drive\\018_Loinc\\2021\\loinc_2_69.csv";
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
						if (!fsnLoincMap.containsKey(fsn) && !fsnLoincMap.containsValue(loincNum)) {
							fsnLoincMap.put(fsn, loincNum);
						} else {
							if (fsnLoincMap.containsValue(loincNum)) {
								debug ("Duplicate keys " + loincNum + "=" + fsn + " with " + fsnLoincMap.inverse().get(loincNum));
							}
							
							//A duplicate FSN will usually have a later replacement with a new LOINCNum
							if (fsnLoincMap.containsKey(fsn)) {
								String origLoincNum = fsnLoincMap.get(fsn);
								String origVersion = get(loincFileMap, origLoincNum, LoincCol.VersionLastChanged.ordinal());
								String origStatus = get(loincFileMap, origLoincNum, LoincCol.STATUS.ordinal());
								String thisVersion = get(loincFileMap, loincNum, LoincCol.VersionLastChanged.ordinal());
								String thisStatus = get(loincFileMap, loincNum, LoincCol.STATUS.ordinal());
								
								if (thisStatus.equals(DEPRECATED)) {
									deprecated.add(loincNum);
								}
								//Is the this version newer than what we stored
								int comparison = thisVersion.compareTo(origVersion);
								String thisReason = get(loincFileMap, loincNum, LoincCol.STATUS_REASON.ordinal());
								if (comparison == 0) {
									report(QUATERNARY_REPORT, "Same Version", fsn, loincNum + " (" + thisVersion + ")", origLoincNum+ " (" + origVersion + ")", origStatus, thisStatus, thisReason);
								} else if ( comparison > 0) {
									fsnLoincMap.replace(fsn, loincNum);
								} else {
									report(QUATERNARY_REPORT, "Bigger Num Earlier", fsn, loincNum + " (" + thisVersion + ")", origLoincNum+ " (" + origVersion + ")", thisStatus, origStatus, thisReason);
								}
							}
						}
					} else isFirstLine = false;
				}
			}
		} catch (Exception e) {
			throw new TermServerScriptException(e);
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
				report((Task)null, (Concept)r.getSource(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace " + localType + " due to lack of historical association");
				return;
			}
			
			Concept localTarget = gl.getConcept(r.getTarget().getId());
			Concept replaceTarget = replaceIfRequired(c, r, localTarget, REL_PART.Target);
			if (replaceTarget == null) {
				report((Task)null, (Concept)r.getSource(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace " + localTarget + " due to lack of historical association");
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
		
		if (loincRow == null) {
			report(TERTIARY_REPORT, c, loincNum, "Loinc file did not feature LOINC_NUM");
			//Can we find it via the FSN?
			String fsn = SnomedUtils.deconstructFSN(c.getFsn())[0];
			String newLoincNum = fsnLoincMap.get(fsn);
			if (newLoincNum != null) {
				report(TERTIARY_REPORT, c, newLoincNum, "Updated LOINC_NUM found via FSN");
			} else {
				//Lets try for a match without the method type
				String fsnCut = fsn.substring(0, fsn.lastIndexOf(':'));
				newLoincNum = fsnLoincMap.get(fsnCut);
				if (newLoincNum != null) {
					report(TERTIARY_REPORT, c, newLoincNum, "Updated LOINC_NUM found via FSN minus MethodType");
				}
			}
			return;
		}
	}
	
	private void validateAgainstPublishedLOINC(Concept c, String loincNum) throws TermServerScriptException {
		//Was this item originally published?
		if (!refsetFileMap.containsKey(loincNum)) {
			report(SECONDARY_REPORT, c, "Not Yet Published");
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
			String relStr = r.getType().getConceptId() + "=" + r.getTarget().getConceptId();
			if (workingCopy.contains(relStr)) {
				workingCopy = workingCopy.replace(relStr, "");
			} else {
				String targetId = r.getTarget().getConceptId();
				//Has this attribute type been replaced?  Check for same attribute value
				if (attributeMap.containsValue(targetId)) {
					Concept publishedType = gl.getConcept(attributeMap.inverse().get(targetId));
					String publishedRel = publishedType.getConceptId() + "=" + targetId;
					if (!publishedType.isActive()) {
						report (SECONDARY_REPORT, c, "Published REL updated", publishedRel + "\n->\n" + r, expression.replaceAll(",", ",\n"), loinc2020Exp);
						workingCopy = workingCopy.replace(publishedRel, "");
					} else {
						debug ("Check here");
					}
				} else {
					report (SECONDARY_REPORT, c, "REL Not Published", r, expression, loinc2020Exp);
				}
			}
		}
		if (workingCopy.length() > 16) {
			report (SECONDARY_REPORT, c, "Published Rel Variance", workingCopy, "", loinc2020Exp);
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
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().startsWith(LOINC_NUM_PREFIX)) {
				return d.getTerm().substring(LOINC_NUM_PREFIX.length());
			}
		}
		throw new TermServerScriptException(c + " does not specify a LOINC num");
	}

	private Concept replaceIfRequired(Concept c, Relationship r, Concept local, REL_PART relPart) throws TermServerScriptException {
		Concept replacement = local;
		if (!local.isActive()) {
			replacement = getReplacement(local);
			if (local != null) {
				report((Task)null, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, relPart.toString() + " " + local + " replaced by " + replacement);
			}
		}
		return replacement;
	}
	
	private Concept getReplacement(Concept inactiveConcept) throws TermServerScriptException {
		Set<String> assocs = inactiveConcept.getAssociationTargets().getSameAs();
		if (assocs.size() == 1) {
			return gl.getConcept(assocs.iterator().next());
		}
		
		assocs = inactiveConcept.getAssociationTargets().getReplacedBy();
		if (assocs.size() == 0) {
			return null;
		} else {
			return gl.getConcept(assocs.iterator().next());
		}
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		//Don't use local copies of concepts, they might not exist
		for (Concept c : findConceptsByCriteria("module=715515008", TARGET_BRANCH, false)) {
			Concept loadedConcept = loadConcept(c, TARGET_BRANCH);
			upgradeLOINCConcept(loadedConcept);
		}
		return componentsToProcess;
	}
}
