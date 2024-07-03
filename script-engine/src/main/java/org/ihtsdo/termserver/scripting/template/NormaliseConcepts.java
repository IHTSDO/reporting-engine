package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * Normalize concepts selected via ECL
 * That is to say, copy all the inferred relationships into the stated form
 * and set the proximal primitive parent
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NormaliseConcepts extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(NormaliseConcepts.class);

	Concept ppp;
	Boolean useStatedECL = false;
	//private String ecl = "<< 66191007 |Transient arthropathy (disorder)|";
	//private String ecl = "<! 267038008 |Edema (finding)|";
	//private String ecl = "<! 416940007 |Past history of procedure (situation)|";
	//private String ecl = "<! 409063005 |Counseling (procedure)|";
	//private String ecl = "<< 10942006 |Plication (procedure)|  ";
	//private String ecl = "<< 64572001 |Disease (disorder)| : 116676008 |Associated morphology (attribute)| = << 408737001 |Malposition|";
	//private String ecl = "<! 416940007 |Past history of procedure (situation)|";
	//private String ecl = "< 3253007 |Discoloration of skin (finding)|";
	//private String ecl = "< 67889009 |Irrigation (procedure)|";
	//private String ecl = "< 11429006 |Consultation (procedure)| ";
	//private String ecl = "< 14766002 |Aspiration (procedure)| ";
	//private String ecl = "<! 59108006 |Injection (procedure)|";
	private String ecl = "<<782901001 |Insertion procedure (procedure)| MINUS (<<42825003 |Cannulation (procedure)| OR <<45211000 |Catheterization (procedure)| OR << 77343006 |Angiography (procedure)| OR << 418285008 |Angioplasty of blood vessel (procedure)| OR  << 363687006 |Endoscopic procedure (procedure)| OR  << 1296925008 |Insertion of stent (procedure)| OR 77465005 |Transplantation (procedure)|)";
	
	public NormaliseConcepts(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		NormaliseConcepts app = new NormaliseConcepts(null);
		try {
			ReportSheetManager.targetFolderId = "1Ay_IwhPD1EkeIYWuU6q7xgWBIzfEf6dl";  // QI/Normalization
			app.classifyTasks = false;
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			LOGGER.info("Failed to NormaliseTemplateCompliantConcepts due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		reportNoChange = false;
		selfDetermining = true;
		summaryTabIdx = SECONDARY_REPORT;
		super.init(args);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"TASK_KEY, TASK_DESC, SCTID, FSN, ConceptType, Severity, ActionType, CharacteristicType, MatchedTemplate, Detail, Detail, Detail",
				"Report Metadata, Detail, Detail"};
		String[] tabNames = new String[] {	"Normalization Processing",
				"Metadata"};
		//ppp = gl.getConcept("64572001 |Disease (disorder)|");
		//ppp = gl.getConcept("404684003 |Clinical finding (finding)|");
		//ppp = gl.getConcept("243796009 |Situation with explicit context (situation)|");
		//ppp = gl.getConcept("71388002 |Procedure (procedure)|");
		//This can be passed through as null now, and calculated from the top level hierarchy
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		if ((loadedConcept.getGciAxioms() != null && loadedConcept.getGciAxioms().size() > 0) 
				|| (loadedConcept.getAdditionalAxioms() != null && loadedConcept.getAdditionalAxioms().size() > 0)) {
			throw new ValidationFailure(task, loadedConcept, "Concept uses axioms");
		}
		int changesMade = removeRedundandRelationships(task, loadedConcept);
		changesMade += normaliseConcept(task, loadedConcept);
		changesMade += removeRedundandGroups(task, loadedConcept);
		if (changesMade > 0) {
			updateConcept(task, loadedConcept, info);
		}
		return changesMade;
	}

	private int normaliseConcept(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;

		changesMade += checkAndSetProximalPrimitiveParent(t, c, null, false, false);
		
		//Remove any redundant relationships, or they'll be missing from the inferred view
		changesMade += removeRedundandRelationships(t,c);
		
		//Restate inferred relationships as stated where required
		changesMade += restateInferredRelationships(t,c);
		
		//Remove stated ungrouped relationships where they're not also inferred
		changesMade += removeUngroupedRelationships(t,c);
		
		return changesMade;
	}
	
	public int restateInferredRelationships(Task t, Concept c) throws TermServerScriptException {
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
		Set<Relationship> ungrouped = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, UNGROUPED).getRelationships();
		for (Relationship r : ungrouped) {
			Set<Relationship> inferredMatches = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, r);
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
			changesMade += addRelationship(t, c, newRel, RelationshipTemplate.Mode.PERMISSIVE);
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//First pass attempt to remodel because we don't want to batch anything that results 
		//in no changes being made.
		Set<Concept> changesRequired = new HashSet<>();
		Set<Concept> noChangesRequired = new HashSet<>();
		
		setQuiet(true);
		CharacteristicType charType = useStatedECL ? CharacteristicType.STATED_RELATIONSHIP : CharacteristicType.INFERRED_RELATIONSHIP;
		for (Concept concept : findConcepts(ecl, true, charType)) {
			/*if (concept.getId().equals("428979007")) {
				LOGGER.debug("here");
			}*/
			//Make changes to a clone of the concept so we don't affect our local copy
			Concept clone = concept.cloneWithIds();
			int changesMade = normaliseConcept(null, clone);
			if (changesMade > 0) {
				changesRequired.add(concept);
			} else {
				noChangesRequired.add(concept);
			}
		}
		setQuiet(false);
		addSummaryInformation("Concepts no change required", noChangesRequired.size());
		return asComponents(changesRequired);
	}
	
}
