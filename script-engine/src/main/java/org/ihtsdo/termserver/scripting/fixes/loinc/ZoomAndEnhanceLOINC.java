package org.ihtsdo.termserver.scripting.fixes.loinc;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.pipeline.loinc.LoincUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * Look through all LOINC expressions and fix whatever needs worked on
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ZoomAndEnhanceLOINC extends BatchLoincFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(ZoomAndEnhanceLOINC.class);

	enum REL_PART {TYPE, TARGET}
	
	private static String publishedRefsetFile = "G:\\My Drive\\018_Loinc\\2021\\der2_sscccRefset_LOINCExpressionAssociationSnapshot_INT_20170731.txt";
	// "/Volumes/GoogleDrive/My Drive/018_Loinc/2021/der2_sscccRefset_LOINCExpressionAssociationSnapshot_INT_20170731.txt
	
	private enum RefsetCol { ID,EFFECTIVETIME,ACTIVE,MODULEID,REFSETID,REFERENCEDCOMPONENTID,MAPTARGET,EXPRESSION,DEFINITIONSTATUSID,CORRELATIONID,CONTENTORIGINID }
	
	private Map<String, List<String>> refsetFileMap;
	
	private Set<Concept> expectedTypeChanges = new HashSet<>();
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	
	protected ZoomAndEnhanceLOINC(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		new ZoomAndEnhanceLOINC(null).standardExecution(args);
	}
	
	@Override
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
		
		LOGGER.info("Mapping current LOINC content");
		loadFiles();
		expectedTypeChanges.add(gl.getConcept("704318007 |Property type (attribute)|"));
	}

	@Override
	protected void loadFiles() throws TermServerScriptException {
		super.loadFiles();
		
		for (String fsn : checkReplacementAvailable) {
			String bestLoincNum = fsnBestLoincMap.get(fsn);
			String status = get(loincFileMap, bestLoincNum, LoincCol.STATUS.ordinal());
			if (status.equals(ACTIVE)) {
				increment("Double deprecated loincNum subsequently replaced");
			} else {
				for (String loincNum : fsnAllLoincMap.get(fsn)) {
					report(QUATERNARY_REPORT, "No active replacements found", fsn, getDetails(loincNum), "");
				}
			}
		}
		
		try {
			//Load the Refset Expression file
			LOGGER.info("Loading {}",  publishedRefsetFile);
			boolean isFirstLine = true;
			try (BufferedReader br = new BufferedReader(new FileReader(publishedRefsetFile))) {
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
		
		flushFiles(false);
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
		get(loincFileMap, loincNum, LoincCol.VERSION_LAST_CHANGED.ordinal()) + separator +
		get(loincFileMap, loincNum, LoincCol.STATUS.ordinal()) +
		(StringUtils.isEmpty(reason) ? "" : separator + reason);
	}

	private void increment(String key) {
		issueSummaryMap.merge(key, 1, Integer::sum);
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
		changesMade += validateAgainstCurrentLOINC(t, c, loincNum);
		validateAgainstPublishedLOINC(c, loincNum);
		
		Set<Relationship> origRels = new HashSet<>(c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE));
		for (Relationship r : origRels) {
			Concept localType = gl.getConcept(r.getType().getId());
			Concept replaceType = replaceIfRequired(t, c, localType, REL_PART.TYPE);
			
			if (replaceType == null) {
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace type " + localType + " due to lack of historical association");
				return NO_CHANGES_MADE;
			} else if (replaceType.equals(MULTI_CONCEPT)) {
				String alternatives = getReplacements(localType).stream().map(Object::toString).collect(Collectors.joining(", "));
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace type " + localType + " due to multiple historical associations", alternatives);
				return NO_CHANGES_MADE;
			}
			
			Concept localTarget = gl.getConcept(r.getTarget().getId());
			Concept replaceTarget = replaceIfRequired(t, c, localTarget, REL_PART.TARGET);
			if (replaceTarget == null) {
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace target " + localTarget + " due to lack of historical association");
				return NO_CHANGES_MADE;
			} else if (replaceTarget.equals(MULTI_CONCEPT)) {
				String alternatives = getReplacements(localTarget).stream().map(Object::toString).collect(Collectors.joining(", "));
				report((Task)null, c, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Unable to replace target " + localTarget + " due to multiple historical associations", alternatives);
				return NO_CHANGES_MADE;
			}
			
			if (!localType.equals(replaceType) || !localTarget.equals(replaceTarget)) {
				Relationship replaceRel = r.clone();
				replaceRel.setType(replaceType);
				replaceRel.setTarget(replaceTarget);
				changesMade = replaceRelationship(t, c, r, replaceRel);
			}
		}
		return changesMade;
	}


	private int validateAgainstCurrentLOINC(Task t, Concept c, String loincNum) throws TermServerScriptException {
		int changesMade = 0;
		//Did LOINC tell us about this loincNum?
		List<String> loincRow = loincFileMap.get(loincNum);
		String fsn = SnomedUtilsBase.deconstructFSN(c.getFsn())[0];
		
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
			String newTerm = LoincUtils.LOINC_NUM_PREFIX + newLoincNum;
			Description oldDesc = LoincUtils.getLoincNumDescription(c);
			String info = getDetails(loincNum);
			replaceDescription(t, c, oldDesc, newTerm, InactivationIndicator.OUTDATED, info);
			changesMade++;
		}
		return changesMade;
	}
	
	private String formLoincFSN(String loincNum) {
		StringBuilder fsn = new StringBuilder();
		for (int idx=1; idx<7; idx++) {
			fsn.append(get(loincFileMap, loincNum, idx));
			if (idx < 6) {
				fsn.append(":");
			}
		}
		//Did we get anything for that last field
		if (fsn.toString().endsWith(":")) {
			return fsn.substring(0, fsn.length()-1);
		}
		return fsn.toString();
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
					if (!publishedType.isActiveSafely()) {
						//Is this one of our expected changes?  Just count if so
						if (expectedTypeChanges.contains(publishedType)) {
							increment("Published Relationship updated: " + publishedType);
						} else {
							report(SECONDARY_REPORT, c, "Published Relationship updated (type change)", SnomedUtils.populateFSNs(publishedRel) + "\n->\n" + r, expression.replace(",", ",\n"), loinc2020Exp);
						}
						workingCopy = workingCopy.replace(publishedRel, "");
					} else {
						report(SECONDARY_REPORT, c, "Unexpected Situation (type)", r, expression, loinc2020Exp);
					}
				} else {
					//Has this attribute VALUE been replaced?  Check for same attribute type
					if (attributeMap.containsKey(typeId)) {
						Concept publishedValue = gl.getConcept(attributeMap.get(typeId));
						String publishedRel = typeId + "=" + publishedValue.getConceptId();
						if (!publishedValue.isActiveSafely()) {
							report(SECONDARY_REPORT, c, "Published REL updated (value change)", SnomedUtils.populateFSNs(publishedRel) + "\n->\n" + r, expression.replace(",", ",\n"), loinc2020Exp);
							workingCopy = workingCopy.replace(publishedRel, "");
						} else {
							report(SECONDARY_REPORT, c, "Unexpected Situation (value)", r, expression, loinc2020Exp);
						}
					}
				}	
			}
		}
		if (workingCopy.length() > 16) {
			report(SECONDARY_REPORT, c, "Published Rel Variance", SnomedUtils.populateFSNs(workingCopy), "", loinc2020Exp);
		}
	}

	private BiMap<String, String> formAttributeMap(Concept c, String expression) {
		BiMap<String, String> attributeMap = HashBiMap.create();
		for (String pair : expression.split(",")) {
			String[] parts = pair.split("=");
			if (attributeMap.containsValue(parts[1])) {
				String target = attributeMap.inverse().get(parts[1]);
				LOGGER.debug("{} has two attributes with target {} : {}", c,  parts[1], target);
			} else if (attributeMap.containsKey(parts[0])) {
				LOGGER.debug("{} has two attribute types {}", c, parts[0]);
			} else {
				attributeMap.put(parts[0], parts[1]);
			}
		}
		return attributeMap;
	}

	private Concept replaceIfRequired(Task t, Concept c, Concept local, REL_PART relPart) throws TermServerScriptException {
		Concept replacement = local;
		if (!local.isActiveSafely()) {
			List<Concept> replacements = getReplacements(local);
			if (replacements.isEmpty()) {
				return null;
			} else if (replacements.size() > 1) {
				return MULTI_CONCEPT;
			}
			replacement = replacements.get(0);
			report(t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, "Inactive rel " + relPart.toString() + " " + local + " replaced by " + replacement);
		}
		return replacement;
	}
	
	private List<Concept> getReplacements(Concept inactiveConcept) {
		Set<String> assocs = inactiveConcept.getAssociationTargets().getSameAs();
		if (!assocs.isEmpty()) {
			return assocs.stream()
					.map(s -> gl.getConceptSafely(s))
					.toList();
		}
		
		assocs = inactiveConcept.getAssociationTargets().getReplacedBy();
		if (!assocs.isEmpty()) {
			return assocs.stream()
					.map(s -> gl.getConceptSafely(s))
					.toList();
		}
		
		assocs = inactiveConcept.getAssociationTargets().getPossEquivTo();
		if (!assocs.isEmpty()) {
			return assocs.stream()
					.map(s -> gl.getConceptSafely(s))
					.toList();
		}
		
		assocs = inactiveConcept.getAssociationTargets().getPartEquivTo();
		if (!assocs.isEmpty()) {
			return assocs.stream()
					.map(s -> gl.getConceptSafely(s))
					.toList();
		}
		
		return new ArrayList<>();
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> componentsToProcess = new ArrayList<>();
		setQuiet(true);
		for (Concept c : LoincUtils.getActiveLoincConcepts(gl)) {
			//Only process component if we have changes to make
			if (upgradeLOINCConcept(null, c.cloneWithIds()) > 0) {
				componentsToProcess.add(c);
			}
		}
		setQuiet(false);
		return componentsToProcess;
	}
}
