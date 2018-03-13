package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.otf.authoringtemplate.domain.logical.Attribute;
import org.ihtsdo.otf.authoringtemplate.domain.logical.AttributeGroup;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

/**
 * QI-6 Force a concept to align to a template
 * @author Peter
 *
 */
public class AlignToTemplate extends TemplateFix {
	

	protected AlignToTemplate(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		AlignToTemplate app = new AlignToTemplate(null);
		try {
			app.subHierarchyStr = "128294001"; // |Chronic inflammatory disorder (disorder)|"
			app.selfDetermining = true;
			app.runStandAlone = true;
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			Batch batch = app.formIntoBatch();
			app.batchProcess(batch);
		} catch (Exception e) {
			info("Failed to align concepts to template due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	protected void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept(subHierarchyStr);
		templates.add(loadTemplate('A', "Chronic Inflammatory Disorder.json"));
		info(templates.size() + " Templates loaded successfully");

	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = alignConceptToTemplate(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return 0;
	}
	
	private int alignConceptToTemplate(Task t, Concept c) throws TermServerScriptException {
		Template template = templates.get(0);
		int changesMade = alignParent(t, c, template);
		changesMade += alignUngroupedAttributes(t, c, template.getLogicalTemplate().getUngroupedAttributes());
		for (AttributeGroup g : template.getLogicalTemplate().getAttributeGroups()) {
			changesMade += alignAttributes(t, c, g);
		}
		return changesMade;
	}

	private int alignParent(Task t, Concept c, Template template) throws TermServerScriptException {
		Concept targetParent = gl.getConcept(template.getLogicalTemplate().getFocusConcepts().get(0));
		Relationship newParentRel = new Relationship(c, IS_A, targetParent, UNGROUPED);
		return replaceParents(t, c, newParentRel, null);
	}

	private int alignUngroupedAttributes(Task t, Concept c, List<Attribute> ungroupedAttributes) throws TermServerScriptException {
		int changesMade = 0;
		//Ensure that each attribute listed is present in group 0
		for (Attribute a : ungroupedAttributes) {
			changesMade += ensureAttributePresent(t, c, a, UNGROUPED);
		}
		return changesMade;
	}	

	private int alignAttributes(Task t, Concept c, AttributeGroup templateGroup) throws TermServerScriptException {
		int changesMade = 0;
		//Each group in the concept must features all the attributes from the template group.  Not Group 0
		for (RelationshipGroup relGroup : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false)) {
			for (Attribute a : templateGroup.getAttributes()) {
				if (!isSpecialCircumstance(t, c, a, relGroup)) {
					changesMade += ensureAttributePresent (t, c, a, relGroup.getGroupId());
				}
			}
		}
		return changesMade;
	}

	private boolean isSpecialCircumstance(Task t, Concept c, Attribute a, RelationshipGroup relGroup) throws TermServerScriptException {
		//Having to include to account for a particular circumstance
		//Concepts with an Associated morphology that is a descendant of 
		//84499006 |Chronic inflammation (morphologic abnormality)| 
		//do not require an Associated morphology change
		Concept chronicInflammation = gl.getConcept("84499006");
		//Does this group have any of those?
		if (a.getType().equals(ASSOC_MORPH.getConceptId())) {
			List<Relationship> specialRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
																ASSOC_MORPH,
																ActiveState.ACTIVE);
			for (Relationship r : specialRels) {
				if (descendantsCache.getDescendents(chronicInflammation).contains(r.getTarget())) {
					report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Existing morphology a descendant of " + chronicInflammation + " : " + r.getTarget());
					return true;
				}
			}
		}
		return false;
	}

	private int ensureAttributePresent(Task t, Concept c, Attribute a, int groupId) throws TermServerScriptException {
		Concept type = gl.getConcept(a.getType());
		Concept value = gl.getConcept(a.getValue());
		return replaceRelationship(t, c, type, value, groupId, false);  //Not unique, allow in other groups
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return asComponents(subHierarchy.getDescendents(NOT_SET));
	}

}
