package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

/**
 * QI2018
 * For concepts that align to a given template, we can normalise them.
 * That is to say, copy all the inferred relationships into the stated form
 * and set the proximal primitive parent - if it matches the template expectation
 */
public class NormaliseTemplateCompliantConcepts extends TemplateFix {

	protected NormaliseTemplateCompliantConcepts(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		NormaliseTemplateCompliantConcepts app = new NormaliseTemplateCompliantConcepts(null);
		try {
			app.reportNoChange = false;
			app.selfDetermining = true;
			app.additionalReportColumns = "CharacteristicType, Attribute";
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			Batch batch = app.formIntoBatch();
			app.batchProcess(batch);
		} catch (Exception e) {
			info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = normaliseConceptToTemplate(task, loadedConcept, conceptToTemplateMap.get(concept));
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
		return changesMade;
	}
	
	private int normaliseConceptToTemplate(Task t, Concept c, Template template) throws TermServerScriptException {
		int changesMade = 0;
		if (t == null) {
			report (t, c, Severity.HIGH, ReportActionType.API_ERROR, "No template found for use with concept");
			return changesMade;
		}
		
		//If the proximal primitive parent matches the template, we can set that
		List<Concept> ppps = determineProximalPrimitiveParents(c);
		if (template.getLogicalTemplate().getFocusConcepts().size() != 1) {
			report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Template " + template.getId() + " does not have 1 focus concept.  Cannot remodel.");
		} else if (ppps.size() != 1) {
			report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept found to have " + ppps + " proximal primitive parents.  Cannot remodel.");
		} else {
			Concept ppp = ppps.get(0);
			Concept templatePPP = gl.getConcept(template.getLogicalTemplate().getFocusConcepts().get(0));
			if (ppp.equals(templatePPP)) {
				setProximalPrimitiveParent(t, c, ppp);
			} else {
				report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Calculated PPP " + ppp + " does not match that suggested by template: " + templatePPP + ", cannot remodel.");
			}
			
		}
		
		return changesMade;
	}

	private int setProximalPrimitiveParent(Task t, Concept c, Concept newParent) throws TermServerScriptException {
		int changesMade = 0;
		List<Relationship> parentRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		//Do we in fact need to do anything?
		if (parentRels.size() == 1 && parentRels.get(0).getTarget().equals(newParent)) {
			report (t, c, Severity.NONE, ReportActionType.NO_CHANGE, "Concept already has template PPP: " + newParent);
		} else {
			boolean doAddition = true;
			for (Relationship r : parentRels) {
				if (r.getTarget().equals(newParent)) {
					doAddition = false;
				} else {
					removeParentRelationship(t, r, c, newParent.toString());
					changesMade++;
				}
			}
			
			if (doAddition) {
				Relationship newParentRel = new Relationship(c, IS_A, newParent, 0);
				c.addRelationship(newParentRel);
				changesMade++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Start with the whole subHierarchy and remove concepts that match each of our templates
		Set<Concept> alignedConcepts = new HashSet<>();
		Set<Concept> ignoredConcepts = new HashSet<>();
		
		for (Template template : templates) {
			alignedConcepts.addAll(findTemplateMatches(template));
		}
		alignedConcepts.removeAll(ignoredConcepts);
		return asComponents(alignedConcepts);
	}
	
}
