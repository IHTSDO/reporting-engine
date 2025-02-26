package org.ihtsdo.termserver.scripting.fixes.one_offs;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate.Mode;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.CaseSensitivityUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-1165
 */
public class QI1165_AddPrimaryNeoplasmSubtypes extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(QI1165_AddPrimaryNeoplasmSubtypes.class);
	
	private Set<String> exclusions;
	private RelationshipTemplate relTemplate;
	private RelationshipTemplate workAroundToRemove;
	String inclusionText = "primary";
	private Map<Concept, Concept> replaceValuesMap;
	CaseSensitivityUtils nounHelper;
	
	protected QI1165_AddPrimaryNeoplasmSubtypes(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		QI1165_AddPrimaryNeoplasmSubtypes fix = new QI1165_AddPrimaryNeoplasmSubtypes(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.runStandAlone = true;
			fix.populateTaskDescription = false;
			fix.selfDetermining = false;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.overrideEclBranch = "MAIN";
			fix.nounHelper = CaseSensitivityUtils.get();
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subsetECL = "(<< 372087000 |Primary malignant neoplasm (disorder)| {{ D term != \"primary\", type = fsn}})" + 
					" MINUS << 269475001 |Malignant tumor of lymphoid, hemopoietic AND/OR related tissue (disorder)|";
		relTemplate = new RelationshipTemplate(PATHOLOGICAL_PROCESS, gl.getConcept("1234914003 |Malignant proliferation of primary neoplasm|"));
		workAroundToRemove = new RelationshipTemplate(ASSOC_MORPH, gl.getConcept("86049000 |Malignant neoplasm, primary (morphologic abnormality)|"));
		exclusions = new HashSet<>();
		replaceValuesMap = new HashMap<>();
		replaceValuesMap.put(gl.getConcept("86049000 |Malignant neoplasm, primary|"), gl.getConcept("1240414004 |Malignant neoplasm morphology|"));
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		try {
			if (c.getId().equals("1156409003")) {
				LOGGER.debug("here");
			}
			//Check for edge case where 'primary' is present to some degree
			if (anyDescriptionContains(c, "primary")) {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Agnostic case identified - 'primary' present in some description");
			}
			
			Concept clone = createPrimarySubtype(t, c);
			Description usPT = clone.getPreferredSynonym(US_ENG_LANG_REFSET);
			Description gbPT = clone.getPreferredSynonym(GB_ENG_LANG_REFSET);
			if (!usPT.equals(gbPT)) {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "US/GB Variance check", usPT + "\n" + gbPT);
			}
			//Does this concept already exist?
			Concept existingConcept = gl.findConcept(clone.getFsn());
			
			if (existingConcept != null) {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Primary concept already exists", existingConcept);
				return NO_CHANGES_MADE;
			}
			addAttribute(t, clone);
			String expression = clone.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			Concept savedClone = createConcept(t, clone, null);
			report(t, c, Severity.LOW, ReportActionType.CONCEPT_ADDED, savedClone, expression);
		} catch (ValidationFailure v) {
			report(t, c, v);
		} catch (Exception e) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return CHANGE_MADE;
	}

	private Concept createPrimarySubtype(Task t, Concept c) throws TermServerScriptException {
		Concept clone = c.cloneAsNewConcept();
		for (Description d : clone.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getType().equals(DescriptionType.TEXT_DEFINITION)) {
				continue;
			}
			if (true);
			//Do we need to keep that initial capital letter
			if (nounHelper.startsWithKnownCaseSensitiveTerm(c, d.getTerm())) {
				d.setTerm("Primary " + d.getTerm());
			} else {
				d.setTerm("Primary " + StringUtils.decapitalizeFirstLetter(d.getTerm()));
			}
			
			if (d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
				//We've added 'Primary' which is not case sensitive, so switch to cI
				d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
				report(t, c, Severity.LOW, ReportActionType.CASE_SIGNIFICANCE_CHANGE_MADE, "CS -> cI", d);
			}
		}
		clone.setFsn(null);  // Clear this separate field out so we look it up again
		return clone;
	}

	private boolean anyDescriptionContains(Concept c, String str) {
		return c.getDescriptions().stream()
				.anyMatch(d -> d.getTerm().toLowerCase().contains(str));
	}

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		boolean groupRemoved = false;
		if (isExcluded(c)) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept Excluded due to lexical rule");
		} else {
			List<RelationshipGroup> groupsToProcess = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, FINDING_SITE);
			for (RelationshipGroup g : groupsToProcess) {
				boolean thisGroupRemoved = false;
				//Is this an additional group that we need to remove?
				if (groupsToProcess.size() > 1 && g.containsTypeValue(workAroundToRemove)) {
					//Does the finding site in this group match the finding site in another group?
					//Or also remove if self grouped
					Concept thisFindingSiteValue = g.getValueForType(FINDING_SITE, true);
					RelationshipTemplate findMe = new RelationshipTemplate(FINDING_SITE, thisFindingSiteValue);
					if (g.size() == 1 || SnomedUtils.hasAttributeInAnotherGroup(c, findMe, g.getGroupId())) {
						changesMade += removeRelationshipGroup(t, c, g);
						groupRemoved = true;
						thisGroupRemoved = true;
					}
				}
				
				if (!thisGroupRemoved) {
					Relationship attrib = relTemplate.createRelationship(c, g.getGroupId(), null);
					changesMade += addRelationship(t, c, attrib, Mode.UNIQUE_TYPE_IN_THIS_GROUP);
				}
			}
			
			//Self grouped would not be picked up in above due to lack of finding site
			//We can also add in the pathological process here
			for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ASSOC_MORPH)) {
				if (g.size() == 1) {
				if (g.containsTypeValue(workAroundToRemove)) {
						changesMade +=  removeRelationshipGroup(t, c, g);
						groupRemoved = true;
					} else {
						Relationship attrib = relTemplate.createRelationship(c, g.getGroupId(), null);
						changesMade += addRelationship(t, c, attrib, Mode.UNIQUE_TYPE_IN_THIS_GROUP);
					}
				}
			}
			
			if (groupRemoved) {
				shuffleDown(t, c);
			}
			
			//Now swap out all our attribute values that we're replacing - if they're still here
			for (Concept targetTarget : replaceValuesMap.keySet()) {
				//Switch all stated relationships from the findValue to the replaceValue
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getTarget().equals(targetTarget)) {
						Relationship replacement = r.clone();
						replacement.setTarget(replaceValuesMap.get(targetTarget));
						changesMade += replaceRelationship(t, c, r, replacement);
					}
				}
			}
			return changesMade;
		}
		return NO_CHANGES_MADE;
	}

	private boolean isExcluded(Concept c) {
		String fsn = " " + c.getFsn().toLowerCase();
		for (String exclusionWord : exclusions) {
			if (fsn.contains(exclusionWord)) {
				return true;
			}
		}
		return false;
	}

	/*@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return findConcepts(subsetECL).parallelStream()
				//.filter(c -> c.getFsn().toLowerCase().contains(inclusionText))
				.filter(c -> !isExcluded(c))
				.filter(c -> !gl.isOrphanetConcept(c))
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}*/
}
