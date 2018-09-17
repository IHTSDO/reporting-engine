package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import us.monoid.json.JSONException;
import us.monoid.json.JSONObject;

/**
 * See https://confluence.ihtsdotools.org/display/IAP/Quality+Improvements+2018
 * Update: https://confluence.ihtsdotools.org/pages/viewpage.action?pageId=61155633
 */
public class PrepMisalignedConcepts extends TemplateFix {
	
	Map<Concept, List<String>> conceptDiagnostics = new HashMap<>();
	protected PrepMisalignedConcepts(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		PrepMisalignedConcepts app = new PrepMisalignedConcepts(null);
		try {
			ReportSheetManager.targetFolderId = "18xZylGhgL7ML782pu6-6u_VUw3p5Hfr7"; //QI/Development
			//ReportSheetManager.targetFolderId = "1uywo1VGAIh7MMY7wCn2yEj312OQCjt9J";
			app.init(args);
			//app.getArchiveManager().allowStaleData = true;
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

	protected void init(String[] args) throws TermServerScriptException, IOException {
		selfDetermining = true;
		reportNoChange = false;
		runStandAlone = true; 
		additionalReportColumns = "CharacteristicType, MatchedTemplate, Template Diagnostic";
		/*
		subHierarchyStr = "125605004";  // QI-5 |Fracture of bone (disorder)|
		templateNames = new String[] {	"fracture/Fracture of Bone Structure.json",
										"fracture/Fracture Dislocation of Bone Structure.json",
										"fracture/Pathologic fracture of bone due to Disease.json",
										"fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json",
										"fracture/Traumatic abnormality of spinal cord structure co-occurrent and due to fracture morphology of vertebral bone structure.json",
										//"Injury of finding site due to birth trauma.json"
										 };
		
		subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"Chronic Inflammatory Disorder.json"}; 
		
		subHierarchyStr =  "126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"Neoplasm of Bone.json",
										"Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};
		
		subHierarchyStr =  "34014006"; //QI-15 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Virus.json",
										"templates/Infection of bodysite caused by virus.json"};
		
		subHierarchyStr = "87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Bacteria.json",
										"templates/Infection of bodysite caused by bacteria.json"};
		 
		subHierarchyStr = "95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/Infection caused by Protozoa with optional bodysite.json"};
			
		subHierarchyStr = "125666000";  //QI-33  |Burn (disorder)|

		excludeHierarchies = new String[] { "426284001" } ; // |Chemical burn (disorder)| 
		templateNames = new String[] {
				"templates/burn/Burn of body structure.json",
				"templates/burn/Epidermal burn of body structure.json",
				"templates/burn/Partial thickness burn of body structure.json",
				"templates/burn/Full thickness burn of body structure.json",
				"templates/burn/Deep partial thickness burn of body structure.json",
				"templates/burn/Superficial partial thickness burn of body structure.json"};
		
		subHierarchyStr = "74627003";  //QI-48 |Diabetic Complication|
		templateNames = new String[] {	"templates/Complication co-occurrent and due to Diabetes Melitus.json",
										"templates/Complication co-occurrent and due to Diabetes Melitus - Minimal.json"};
		
		subHierarchyStr = "8098009";	// QI-45 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		
		subHierarchyStr = "283682007"; // QI-39 |Bite - wound (disorder)|
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		
		subHierarchyStr = "3218000"; //QI-67 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Fungus.json"};
		*/
		subHierarchyStr = "17322007"; //QI-68 |Parasite (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Parasite.json"};
		/*
		subHierarchyStr = "416886008"; //QI-106 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json"
				//"templates/wound/closed wound of bodysite.json"
				};
		
		subHierarchyStr = "125643001"; //QI-107 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json"
				//"templates/wound/open wound of bodysite.json"
				};
		*/
		super.init(args);
	}
	
	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		//We're not currently able to programmatically fix template infractions, so we'll save
		//the concept unaltered so it appears in the task description and for review.
		report(task, loadedConcept);
		try {
			if (!dryRun) {
				touchConcept(task, loadedConcept, info);
			} else {
				debug ("Skipping concept touch for " + loadedConcept);
			}
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return 0;
	}

	private void touchConcept(Task t, Concept c, String info) throws SnowOwlClientException, JSONException {
		debug ("Touching FSN CS for " + c + info);
		CaseSignificance orig = c.getFSNDescription().getCaseSignificance();
		CaseSignificance flip = orig.equals(CaseSignificance.CASE_INSENSITIVE)?CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE : CaseSignificance.CASE_INSENSITIVE;
		
		//Flip it
		c.getFSNDescription().setCaseSignificance(flip);
		String conceptSerialised = gson.toJson(c);
		tsClient.updateConcept(new JSONObject(conceptSerialised), t.getBranchPath());
		
		//Flip it back
		c.getFSNDescription().setCaseSignificance(orig);
		conceptSerialised = gson.toJson(c);
		tsClient.updateConcept(new JSONObject(conceptSerialised), t.getBranchPath());
	}

	private void report(Task t, Concept c) throws TermServerScriptException {
		//Collect the diagnostic information about why this concept didn't match any templates as a string
		String diagnosticStr = String.join("\n", conceptDiagnostics.get(c));
		report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, diagnosticStr);
	}

	/* Termserver must support ECL1.3 for this code to work
	 * private void reportUnlignedConcepts() throws TermServerScriptException {	
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
		Set<Concept> unalignedConcepts = new HashSet<>(descendantsCache.getDescendentsOrSelf(subHierarchy));
		Set<Concept> ignoredConcepts = new HashSet<>();
		
		for (Template template : templates) {
			Set<Concept> matches = findTemplateMatches(template);
			incrementSummaryInformation("Matched templates",matches.size());
			unalignedConcepts.removeAll(matches);
			unalignedConcepts.removeAll(exclusions);
		}
		
		for (Concept c : unalignedConcepts) {
			if (c.getConceptId().equals("5120006")) {
			//	debug("Check Me");
			}
			if (!isExcluded(c)) {
				List<String> diagnostics = new ArrayList<String>();
				conceptDiagnostics.put(c, diagnostics);
				String msg = "Cardinality mismatch on " +  (c.getIssues().isEmpty()?" N/A" : c.getIssues());
				debug (c + ".  " + msg);
				diagnostics.add(msg);
				diagnostics.add("Relationship Group mismatches:");
				for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)) {
					//is this group purely inferred?  Add an indicator if so 
					String purelyInferredIndicator = groupPurelyInferred(c,g)?"^":"";
					msg = "    " + purelyInferredIndicator + g;
					debug (msg);
					diagnostics.add(msg);
				}
				incrementSummaryInformation("Concepts identified as not matching any template");
			} else {
				ignoredConcepts.add(c);
			}
		}
		unalignedConcepts.removeAll(ignoredConcepts);
		return asComponents(unalignedConcepts);
	}

	//return true if this inferred group does not have a stated counterpart
	private boolean groupPurelyInferred(Concept c, RelationshipGroup ig) {
		for (RelationshipGroup sg : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			if (ig.equals(sg)) {
				return false;
			}
		}
		return true;
	}

}
