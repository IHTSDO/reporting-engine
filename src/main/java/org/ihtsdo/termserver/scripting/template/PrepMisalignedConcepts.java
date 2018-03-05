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
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONObject;

public class PrepMisalignedConcepts extends TemplateFix {

	protected PrepMisalignedConcepts(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		PrepMisalignedConcepts app = new PrepMisalignedConcepts(null);
		try {
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
		//We're not currently able to programmatically fix template infractions, so we'll save
		//the concept unaltered so it appears in the task description and for review.
		try {
			String conceptSerialised = gson.toJson(loadedConcept);
			debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
			}
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return 0;
	}

	/*private void reportUnlignedConcepts() throws TermServerScriptException {	
		//Get the template as an ECL Expression and recover concepts which do NOT meet this criteria
		String ecl = TemplateUtils.covertToECL(template);
		//Take the inverse to find all concepts that DO NOT match one of our templates
		String inverseEcl = "<<" + subHierarchyStr + " MINUS (" + ecl + ")";
		List<Concept> concepts = findConcepts("MAIN", inverseEcl);
		for (Concept c : concepts) {
			debug (c);
		}
	}*/
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Start with the whole subHierarchy and remove concepts that match each of our templates
		Set<Concept> unalignedConcepts = cache.getDescendentsOrSelf(subHierarchy);
		Set<Concept> ignoredConcepts = new HashSet<>();
		
		for (Template template : templates) {
			Set<Concept> matches = findTemplateMatches(template);
			unalignedConcepts.removeAll(matches);
		}
		
		for (Concept c : unalignedConcepts) {
			if (!isIngnored(c)) {
				debug (c + " - " + c.getIssues());
				for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					debug ("    " + g);
				}
				incrementSummaryInformation("Concepts identified as not matching any template");
			} else {
				ignoredConcepts.add(c);
			}
		}
		unalignedConcepts.removeAll(ignoredConcepts);
		return asComponents(unalignedConcepts);
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}

}
