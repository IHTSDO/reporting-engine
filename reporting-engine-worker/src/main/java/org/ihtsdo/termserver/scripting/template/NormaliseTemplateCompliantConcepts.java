package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;


/**
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
			ReportSheetManager.targetFolderId = "1Ay_IwhPD1EkeIYWuU6q7xgWBIzfEf6dl";  // QI/Normalization
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			Batch batch = app.formIntoBatch();
			app.batchProcess(batch);
		} catch (Exception e) {
			info("Failed to NormaliseTemplateCompliantConcepts due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		reportNoChange = false;
		selfDetermining = true;
		runStandAlone = true;
		classifyTasks = true;
		//validateTasks = true; Currently failing with 500 error.  Take out Resty?
		additionalReportColumns = "CharacteristicType, Template, ActionDetail";
		
		/*
		subHierarchyStr = "125605004";  // QI-17 |Fracture of bone (disorder)|
		templateNames = new String[] {	"fracture/Fracture of Bone Structure.json",
										"fracture/Fracture Dislocation of Bone Structure.json",
										"fracture/Pathologic fracture of bone due to Disease.json",
										"fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json",
										"fracture/Traumatic abnormality of spinal cord structure co-occurrent and due to fracture morphology of vertebral bone structure.json",
										//"Injury of finding site due to birth trauma.json"
										 };
		
		subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"Chronic Inflammatory Disorder.json"};
		
		subHierarchyStr =  "126537000";  //QI-31 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"Neoplasm of Bone.json",
										"Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};

		subHierarchyStr =  "34014006"; //QI-15 + QI-23 |Viral disease (disorder)|
		templateNames = new String[] {	"Infection caused by virus with optional bodysite.json"};
		
		subHierarchyStr =  "87628006";  //QI-16 + QI-21 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"Infection caused by bacteria with optional bodysite.json"}; 
		
		subHierarchyStr =  "95896000";  //QI-19 + QI-27  |Protozoan infection (disorder)|
		templateNames = new String[] {"Infection caused by Protozoa with optional bodysite.json"};
		
		subHierarchyStr =  "125666000";  //QI-37  |Burn (disorder)|
		templateNames = new String[] {
				"burn/Burn of body structure.json",
				"burn/Epidermal burn of body structure.json",
				"burn/Partial thickness burn of body structure.json",
				"burn/Full thickness burn of body structure.json",
				"burn/Deep partial thickness burn of body structure.json",
				"burn/Superficial partial thickness burn of body structure.json"};
		
		subHierarchyStr = "8098009";	// QI-120 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"Sexually transmitted Infection with optional bodysite.json"};
		
		subHierarchyStr = "95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/Infection caused by Protozoa with optional bodysite.json"};
		
		subHierarchyStr = "416886008"; //QI-129 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json",
										"templates/wound/closed wound of bodysite.json"};
		*/
		subHierarchyStr = "8098009";	// QI-130 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		
		super.init(args);
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = normaliseConceptToTemplate(task, loadedConcept, conceptToTemplateMap.get(concept));
		if (changesMade > 0) {
			updateConcept(task, loadedConcept, info);
		}
		return changesMade;
	}

	private int normaliseConceptToTemplate(Task t, Concept c, Template template) throws TermServerScriptException {
		int changesMade = 0;
		if (template == null) {
			report (t, c, Severity.HIGH, ReportActionType.API_ERROR, "No template found for use with concept");
			return changesMade;
		}
		
		//If the proximal primitive parent matches that of the matching template, we can set that
		List<String> focusConceptIds = conceptToTemplateMap.get(c).getLogicalTemplate().getFocusConcepts();
		if (focusConceptIds.size() == 1) {
			changesMade += checkAndSetProximalPrimitiveParent(t, c, gl.getConcept(focusConceptIds.get(0)));
		} else {
			report (t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Cannot remodel PPP - template specifies multiple focus concepts");
		}
		
		//Restate inferred relationships as stated where required
		changesMade += restateInferredRelationships(t,c);
		
		return changesMade;
	}

	private int restateInferredRelationships(Task t, Concept c) throws TermServerScriptException {
		//Work through all inferred groups and collect any that aren't also stated, to state
		int changesMade = 0;
		List<RelationshipGroup> toBeStated = new ArrayList<>();
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);
		
		nextInferredGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			boolean matchFound = false;
			for (RelationshipGroup statedGroup : statedGroups) {
				if (inferredGroup.equals(statedGroup)) {
					matchFound = true;
					continue nextInferredGroup;
				}
			}
			if (!matchFound) {
				toBeStated.add(inferredGroup);
			}
		}
		changesMade += stateRelationshipGroups(t, c, toBeStated);
		if (changesMade == 0) {
			report (t, c, Severity.NONE, ReportActionType.NO_CHANGE, "Stated/Inferred groups already matched " + statedGroups.size() + "/" + inferredGroups.size());
		}
		return changesMade;
	}

	private int stateRelationshipGroups(Task t, Concept c, List<RelationshipGroup> toBeStated) throws TermServerScriptException {
		int changesMade = 0;
		for (RelationshipGroup g : toBeStated) {
			//Group 0 must remain group 0.  Otherwise find an available group number
			int freeGroup = g.getGroupId()==0?0:SnomedUtils.getFirstFreeGroup(c);
			changesMade += stateRelationshipGroup(t, c, g, freeGroup);
		}
		return changesMade;
	}

	private int stateRelationshipGroup(Task t, Concept c, RelationshipGroup g, int freeGroup) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : g.getRelationships()) {
			Relationship newRel = r.clone(null);
			newRel.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
			newRel.setGroupId(freeGroup);
			changesMade += addRelationship(t, c, newRel);
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
		
		//Now first pass attempt to remodel because we don't want to batch anything that results 
		//in no changes being made.
		Set<Concept> changesRequired = new HashSet<>();
		Set<Concept> noChangesRequired = new HashSet<>();
		setQuiet(true);
		for (Concept alignedConcept : alignedConcepts) {
			//Make changes to a clone of the concept so we don't affect our local copy
			Concept alignedClone = alignedConcept.cloneWithIds();
			int changesMade = normaliseConceptToTemplate(null, alignedClone, conceptToTemplateMap.get(alignedConcept));
			if (changesMade > 0) {
				changesRequired.add(alignedConcept);
			} else {
				noChangesRequired.add(alignedConcept);
			}
		}
		setQuiet(false);
		addSummaryInformation("Concepts matching templates, no change required", noChangesRequired.size());
		return asComponents(changesRequired);
	}
	
}
