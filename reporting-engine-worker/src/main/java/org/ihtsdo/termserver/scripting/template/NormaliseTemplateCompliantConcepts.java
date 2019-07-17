package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
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
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		NormaliseTemplateCompliantConcepts app = new NormaliseTemplateCompliantConcepts(null);
		try {
			ReportSheetManager.targetFolderId = "1Ay_IwhPD1EkeIYWuU6q7xgWBIzfEf6dl";  // QI/Normalization
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			info("Failed to NormaliseTemplateCompliantConcepts due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		//safetyProtocols = false;
		reportNoChange = false;
		selfDetermining = true;
		runStandAlone = true;
		classifyTasks = true;
		includeSummaryTab = true;
		//offlineMode(true);
		
		if (!safetyProtocols) {
			//We're expecting to exceed limits if the safeties are off
			populateEditPanel = false;
			populateTaskDescription = false;
		}
		
		if (exclusionWords == null) {
			exclusionWords = new ArrayList<>();
		}
		
		if (inclusionWords == null) {
			inclusionWords = new ArrayList<>();
		}
		
		/*
		subHierarchyECL = "<<125605004";  // QI-17 |Fracture of bone (disorder)|
		templateNames = new String[] {	"templates/fracture/Fracture of Bone Structure.json",
										"templates/fracture/Fracture Dislocation of Bone Structure.json",
										//"templates/fracture/Pathologic fracture of bone due to Disease.json",
										//"templates/fracture/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json",
										//"templates/fracture/Traumatic abnormality of spinal cord structure co-occurrent and due to fracture morphology of vertebral bone structure.json",
										//"Injury of finding site due to birth trauma.json"
										 };
		
		subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"};
		
		subHierarchyStr =  "126537000";  //QI-31 |Neoplasm of bone (disorder)|
		templateNames = new String[] {	"templates/Neoplasm of Bone.json",
										"templates/Pathologic fracture morphology of bone structure co-occurrent and due to Neoplasm of bone.json"};
		
		subHierarchyStr =  "34014006"; //QI-125 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by virus with optional bodysite.json"};
		
		subHierarchyStr =  "87628006";  //QI-16 + QI-21 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by bacteria with optional bodysite.json"}; 
		
		subHierarchyStr =  "95896000";  //QI-19 + QI-27  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/Infection caused by Protozoa with optional bodysite.json"};
		
		subHierarchyStr =  "125666000";  //QI-37  |Burn (disorder)|
		templateNames = new String[] {
				"templates/burn/Burn of body structure.json",
				"templates/burn/Epidermal burn of body structure.json",
				"templates/burn/Partial thickness burn of body structure.json",
				"templates/burn/Full thickness burn of body structure.json",
				"templates/burn/Deep partial thickness burn of body structure.json",
				"templates/burn/Superficial partial thickness burn of body structure.json"};
		
		subHierarchyECL = "<<8098009";	// QI-120 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		
		subHierarchyECL = "<<95896000";  //QI-19  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/Infection caused by Protozoa with optional bodysite.json"};
		
		subHierarchyECL = "<<416886008"; //QI-129 |Closed wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite.json",
										"templates/wound/closed wound of bodysite.json"};
		
		subHierarchyECL = "<<8098009";	// QI-130 |Sexually transmitted infectious disease (disorder)| 
		templateNames = new String[] {	"templates/Sexually transmitted Infection with optional bodysite.json"};
		
		subHierarchyECL = "<<17322007"; //QI-163 |Parasite (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Parasite.json"};
		
		subHierarchyStr =  "128294001";  // QI-164 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"}; 
		setExclusions(new String[] {"40733004|Infectious disease|"});
		exclusionWords.add("arthritis");
		
		subHierarchyECL = "<<283682007"; // QI-169 |Bite - wound (disorder)|
		includeDueTos = true;
		templateNames = new String[] {	"templates/bite/bite of bodysite caused by bite event.json", 
										"templates/bite/bite of bodysite caused by bite event with infection.json"};
		
		subHierarchyECL = "<<399963005|Abrasion|"; //QI-170
		templateNames = new String[] {	"templates/wound/abrasion.json" ,
										"templates/Disorder due to birth trauma.json"};
		includeDueTos = true;
		
		subHierarchyECL = "<<432119003 |Aneurysm (disorder)|"; //QI-183 
		templateNames = new String[] {	"templates/Aneurysm of Cardiovascular system.json" };
		
		subHierarchyECL = "<<40733004|Infectious disease|"; //QI-193
		templateNames = new String[] {	"templates/infection/Infection NOS.json" };
		setExclusions(new String[] {"87628006 |Bacterial infectious disease (disorder)|","34014006 |Viral disease (disorder)|",
				"3218000 |Mycosis (disorder)|","8098009 |Sexually transmitted infectious disease (disorder)|", 
				"17322007 |Disease caused by parasite (disorder)|", "91302008 |Sepsis (disorder)|"});
		exclusionWords.add("shock");
		
		subHierarchyECL = "<<125643001"; //QI-207 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite due to event.json" };
		exclusionWords.add("complication");
		exclusionWords.add("fracture");
		setExclusions(new String[] {"399963005 |Abrasion (disorder)|", "312608009 |Laceration - injury|"});
		includeDueTos = true;
		
		subHierarchyECL = "<<3218000"; //QI-208 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Fungus.json"};
		
		subHierarchyECL = "<<312608009 |Laceration - injury|"; //QI-226
		templateNames = new String[] {	"templates/wound/laceration.json" };
		includeDueTos = true;
		
		subHierarchyECL = "<< 416462003 |Wound (disorder)|"; //QI-235
		setExclusions(new String[] {"125643001 |Open Wound|", 
									"416886008 |Closed Wound|",
									"312608009 |Laceration|",
									"283682007 |Bite Wound|",
									"399963005 |Abraision|",
									"125670008 |Foreign Body|",
									"125667009 |Contusion (disorder)|"});
		templateNames = new String[] {	"templates/wound/wound of bodysite.json",
										"templates/wound/wound of bodysite due to event.json"};
		includeDueTos = true;
		
		subHierarchyECL = "<<52515009"; //QI-236 |Hernia of abdominal cavity (disorder)|
		templateNames = new String[] {	"templates/hernia/Hernia of Body Structure.json"};
		excludeHierarchies = new String[] { "236037000 |Incisional hernia (disorder)|" };
		exclusionWords = new ArrayList<String>();
		exclusionWords.add("gangrene");
		exclusionWords.add("obstruction");
		
		subHierarchyECL = "<< 128477000 |Abscess (disorder)|"; //QI-214
		templateNames = new String[] {	"templates/Abscess.json",
										"templates/Abscess with Cellulitis.json"};
		
		
		subHierarchyECL = "<<428794004 |Fistula (disorder)|"; //QI-186
		templateNames = new String[] {	"templates/Fistula.json" };
		includeDueTos = true;
		
		subHierarchyECL = "< 64572001 |Disease (disorder)|"; 
		templateNames = new String[] {	"templates/Disease.json" };
		
		subHierarchyECL = "<<441457006 |Cyst|"; //QI-182
		templateNames = new String[] {	"templates/Cyst.json" };
		
		subHierarchyECL = "<< 109355002 |Carcinoma in situ (disorder)|"; //QI-231
		templateNames = new String[] {	"templates/Carcinoma in Situ.json" };
		
		subHierarchyECL = "<< 247441003 |Erythema|"; //QI-240
		templateNames = new String[] {	"templates/Erythema of body structure.json" };
		inclusionWords.add("finding");
		
		subHierarchyECL = "<< 3723001 |Arthritis (disorder)|"; //QI-167
		templateNames = new String[] {	"templates/Arthritis.json" };
		
		subHierarchyECL = "< 429040005 |Ulcer (disorder)|"; //QI-288
		templateNames = new String[] {	"templates/Ulcer.json" };
		
		subHierarchyECL = "< 125670008 |Foreign body (disorder)|"; //QI-291
		templateNames = new String[] {	"templates/Foreign body.json" };
		
		subHierarchyECL = "<< 125667009 |Contusion (disorder)|"; //QI-245
		templateNames = new String[] {	"templates/wound/contusion.json" };
		
		subHierarchyECL = "< 193570009 |Cataract (disorder)|"; //QI-220
		templateNames = new String[] {	"templates/Cataract.json" };
		includeComplexTemplates = true;
		
		subHierarchyECL = "< 7890003 |Contracture of joint (disorder)|"; //QI-262
		templateNames = new String[] {	"templates/Contracture of joint minus.json" };
		includeComplexTemplates = true;
		
		subHierarchyECL = "<<118616009"; //QI-253 |Neoplastic disease of uncertain behavior| 
		templateNames = new String[] {	"templates/Neoplastic Disease.json"};
		
		//QI-317 Various neoplasms
		subHierarchyECL = "<400178008 |Lymphangioma (disorder)|"; // << 115236002 |Lymphatic vessel tumor (morphologic abnormality)|
		//subHierarchyECL = "< 400210000 |Hemangioma (disorder)|"; // << 253053003 |Benign hemangioma (morphologic abnormality)|
		//subHierarchyECL = "< 205562004 |Angiomatosis (disorder)|";  //<< 14350002 |Angiomatosis (morphologic abnormality)|
		templateNames = new String[] {	"templates/Misc Neoplasms.json"};
		
		subHierarchyECL = "<20376005 |Benign neoplastic disease|"; //QI-272
		templateNames = new String[] {	"templates/Benign Neoplastic Disease.json"};
		
		subHierarchyECL = "< 233776003 |Tracheobronchial disorder|"; //QI-268
		templateNames = new String[] {	"templates/Tracheobronchial.json" };

		subHierarchyECL = "<< 298180004 |Finding of range of joint movement (finding)| MINUS << 7890003 |Contracture of joint (disorder)|"; //QI-284
		templateNames = new String[] {	"templates/Finding of range of joint movement.json" };
		includeComplexTemplates = true;
		
		subHierarchyECL = "< 400006008 |Hamartoma (disorder)|"; //QI-296
		templateNames = new String[] {	"templates/Harmartoma.json" };
		
		subHierarchyECL = "<<87628006";  //QI-338 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by bacteria.json"};
		*/
		
		//QI-331
		subHierarchyECL = "<<362975008 |Degenerative disorder (disorder)|: 116676008 |Associated morphology (attribute)| = << 69251000 |Depletion (morphologic abnormality)|";
		templateNames = new String[] {	"templates/infection/Infection caused by bacteria.json"};
		
		super.init(args);
		
		//Ensure our ECL matches more than 0 concepts.  This will also cache the result
		boolean expectLargeResults = !safetyProtocols;
		boolean useLocalStoreIfSimple = false;
		if (!getArchiveManager().allowStaleData && findConcepts(subHierarchyECL, false, expectLargeResults, useLocalStoreIfSimple).size() == 0) {
			throw new TermServerScriptException(subHierarchyECL + " returned 0 rows");
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"TASK_KEY, TASK_DESC, SCTID, FSN, CONCEPT_TYPE, SEVERITY, ACTION_TYP, CharacteristicType, MatchedTemplate, Template Diagnostic",
				"Report Metadata", "SCTID, FSN, SemTag, Reason"};
		String[] tabNames = new String[] {	"Mismatched Concepts",
				"Metadata",
				"Excluded Concepts"};
		super.postInit(tabNames, columnHeadings, false);
		outputMetaData();
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		if ((loadedConcept.getGciAxioms() != null && loadedConcept.getGciAxioms().size() > 0) 
				|| (loadedConcept.getAdditionalAxioms() != null && loadedConcept.getAdditionalAxioms().size() > 0)) {
			throw new ValidationFailure(task, loadedConcept, "Concept uses axioms");
		}
		int changesMade = removeRedundandRelationships(task, loadedConcept);
		changesMade += normaliseConceptToTemplate(task, loadedConcept, conceptToTemplateMap.get(concept));
		changesMade += removeRedundandGroups(task, loadedConcept);
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
		
		//Remove stated ungrouped relationships where they're not also inferred
		changesMade += removeUngroupedRelationships(t,c);
		
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
	
	private int removeUngroupedRelationships(Task t, Concept c) throws TermServerScriptException {
		//Work through stated ungrouped relationships and remove them if they don't also exist inferred, ungrouped
		
		//If there are no ungrouped relationships, then nothing to do here
		if (c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED) == null) {
			return NO_CHANGES_MADE;
		}
		
		int changesMade = 0;
		List<Relationship> ungrouped = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships();
		for (Relationship r : ungrouped) {
			List<Relationship> inferredMatches = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r);
			if (inferredMatches.size() == 0) {
				removeRelationship(t, c, r, "Redundant ungrouped stated: ");
				changesMade++;
			}
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
		Collection<Concept> potentialMatches = findConcepts(subHierarchyECL);
		//Collection<Concept> potentialMatches = Collections.singleton(gl.getConcept("7890003 |Contracture of Joint|"));
		addSummaryInformation("Concepts matching ECL", potentialMatches.size());
		
		info ("Identifying concepts aligned to template");
		for (Template template : templates) {
			alignedConcepts.addAll(findTemplateMatches(template, potentialMatches, TERTIARY_REPORT));
		}
		
		//So how many did NOT align?
		int misalignedCount = potentialMatches.size() - alignedConcepts.size();
		addSummaryInformation("Concepts misaligned or excluded", misalignedCount);
		
		//Now first pass attempt to remodel because we don't want to batch anything that results 
		//in no changes being made.
		Set<Concept> changesRequired = new HashSet<>();
		Set<Concept> noChangesRequired = new HashSet<>();
		setQuiet(true);
		for (Concept alignedConcept : alignedConcepts) {
			if (inclusionWords.size() > 0) {
				if (!containsInclusionWord(alignedConcept)) {
					incrementSummaryInformation("Skipped as doesn't contain inclusion word");
					continue;
				}
			}
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
