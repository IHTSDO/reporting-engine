package org.ihtsdo.termserver.scripting.template;

import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.snomed.authoringtemplate.domain.logical.*;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

/**
 * QI-6 Force a concept to align to a template
 * @author Peter
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AlignToTemplate extends TemplateFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(AlignToTemplate.class);

	protected AlignToTemplate(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		AlignToTemplate app = new AlignToTemplate(null);
		try {
			app.subHierarchyStr = "128294001"; // |Chronic inflammatory disorder (disorder)|
			app.selfDetermining = true;
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			LOGGER.info("Failed to align concepts to template due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	public void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept(subHierarchyStr);
		templates.add(loadLocalTemplate('A', "Chronic Inflammatory Disorder.json"));
		LOGGER.info(templates.size() + " Templates loaded successfully");
		super.postInit();
	}

	@Override
	protected int doFix(Task t, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = alignConceptToTemplate(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return 0;
	}
	
	private int alignConceptToTemplate(Task t, Concept c) throws TermServerScriptException {
		Template template = templates.iterator().next();
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
		for (RelationshipGroup relGroup : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			if (!relGroup.isGrouped()) {
				continue;
			}
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
			Set<Relationship> specialRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
																ASSOC_MORPH,
																ActiveState.ACTIVE);
			for (Relationship r : specialRels) {
				if (gl.getDescendantsCache().getDescendants(chronicInflammation).contains(r.getTarget())) {
					report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Existing morphology a descendant of " + chronicInflammation + " : " + r.getTarget());
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
		return asComponents(subHierarchy.getDescendants(NOT_SET));
	}

}
