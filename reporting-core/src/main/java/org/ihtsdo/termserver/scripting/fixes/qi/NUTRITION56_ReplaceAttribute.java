package org.ihtsdo.termserver.scripting.fixes.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * NUTRITION-56 Batch change of Modified substance diet 
 */
public class NUTRITION56_ReplaceAttribute extends BatchFix {

	private RelationshipTemplate matchTemplate;
	private RelationshipTemplate replaceTemplate;
	private List<RelationshipTemplate> groupWith;
	
	protected NUTRITION56_ReplaceAttribute(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		NUTRITION56_ReplaceAttribute fix = new NUTRITION56_ReplaceAttribute(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//subsetECL = "<< 182922004 |Dietary regime (regime/therapy)| : 424361007 |Using substance (attribute)| = *";
		subsetECL = " << 435581000124102 |Carbohydrate modified diet (regime/therapy)| OR " + 
				" << 435671000124101 |Cholesterol modified diet (regime/therapy)| OR " + 
				" << 435711000124102 |Fat modified diet (regime/therapy)| OR " + 
				" << 435721000124105 |Fiber modified diet (regime/therapy)| OR " + 
				" << 435781000124109 |Mineral modified diet (regime/therapy)| OR " + 
				" << 435691000124100 |Diet modified for specific foods or ingredients (regime/therapy)| OR " + 
				" << 762104002 |Modified fluid diet (regime/therapy)| OR " + 
				" << 435791000124107 |Protein modified diet (regime/therapy)| OR " + 
				" << 435811000124106 |Vitamin modified diet (regime/therapy)|";
		matchTemplate = new RelationshipTemplate(gl.getConcept("424361007 |Using substance (attribute)|"), null);
		replaceTemplate = new RelationshipTemplate(gl.getConcept("260686004 |Method (attribute)| "), 
				gl.getConcept("129445006 |Administration - action (qualifier value)|"));
		groupWith = new ArrayList<>();
		groupWith.add(new RelationshipTemplate(gl.getConcept("363701004 |Direct substance (attribute)|"), null));
		groupWith.add(new RelationshipTemplate(gl.getConcept("363702006 |Has focus (attribute)|"), null));
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade += addAttribute(task, loadedConcept);
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

	private int addAttribute(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		//Actually we'll remove this relationship anyway and then potentially add 
		//the replacement in a different group if we have a groupWith attribute available
		for (Relationship r : c.getRelationships(matchTemplate, ActiveState.ACTIVE)) {
			changesMade += removeRelationship(t, c, r);
		}
		//If the match is not found, we will add in the new attribute anyway, if it does not exist.
		int bestTargetGroup = getBestTargetGroup(t, c);
		changesMade += addRelationship(t, c, replaceTemplate, bestTargetGroup);
		return changesMade;
	}

	private int getBestTargetGroup(Task t, Concept c) throws TermServerScriptException {
		//If we have any of the "groupWith" attributes present then pitch in with that.
		//otherwise the default is to self group
		for (RelationshipTemplate potentialGroupWith : groupWith) {
			Set<Relationship> matches = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, potentialGroupWith);
			if (matches.size() > 0) {
				if (matches.size() > 1) {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Please check - multiple potential groups for new attribute");
				}
				return matches.iterator().next().getGroupId();
			}
		}
		return SELFGROUPED;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return new ArrayList<>(findConcepts(subsetECL).stream()
				.filter(c -> c.getRelationships(replaceTemplate, ActiveState.ACTIVE).size() == 0)
				.collect(Collectors.toList()));
	}
}
