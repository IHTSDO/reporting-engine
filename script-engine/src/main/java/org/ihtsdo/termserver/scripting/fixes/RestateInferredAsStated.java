package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

/*
For concepts with the relevant semantic tags, 
find instances of specified attributes which exist as inferred but not as stated, 
and repeat them as stated relationships
*/

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestateInferredAsStated extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(RestateInferredAsStated.class);

	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	String targetSemanticTag = "(medicinal product form)";
	List<Concept> attributesOfInterest = new ArrayList<Concept>();
	List<Concept> conceptsAgreedToChange = new ArrayList<Concept>();
	
	protected RestateInferredAsStated(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RestateInferredAsStated fix = new RestateInferredAsStated(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.reportNoChange = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		//Populate our attributes of interest
		attributesOfInterest.add(gl.getConcept("127489000")); //Has active ingredient (attribute)|)
		attributesOfInterest.add(gl.getConcept("411116001")); //Has manufactured dose form (attribute)
		
		try {
			List<String> lines = Files.readLines(getInputFile(), Charsets.UTF_8);
			LOGGER.info("Loading concepts agreed for change from " + getInputFile());
			for (String line : lines) {
				conceptsAgreedToChange.add(gl.getConcept(line.trim()));
			}
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to load changing concepts",e);
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		//Is this one of the concepts we've been told to fix?  Skip if not
		if (!conceptsAgreedToChange.contains(concept)) {
			report(t, concept, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Instructed not to process");
			return 0;
		}
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = restateInferredRelationships(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}


	private int restateInferredRelationships(Task task, Concept loadedConcept) throws TermServerScriptException {
		Set<Relationship> missingFromStated = determineInferredMissingFromStated(loadedConcept);
		int changesMade = 0;
		for (Relationship inferred : missingFromStated) {
			//Does this inferred type exist as stated, just with a different value?
			Set<Relationship> alreadyExists = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, inferred.getType(), ActiveState.ACTIVE);
			if (alreadyExists.isEmpty()) {
				Relationship stated = inferred.clone(null);
				stated.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
				//Does this relationship already exist inactive? Reactivate if so.
				Relationship inactiveStated = checkForInactiveRel(loadedConcept, stated);
				String msg;
				if (inactiveStated == null) {
					loadedConcept.getRelationships().add(stated);
					msg = "Stated " + stated;
				} else {
					inactiveStated.setActive(true);
					msg = "Reactivated " + stated;
				}
				report(task, loadedConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, msg);
				changesMade++;
			} else {
				String msg = "Attribute type exists as stated, but with a different value - inferred " + inferred.getTarget() + " vs stated " + alreadyExists.iterator().next().getTarget();
				report(task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			}
		}
		return changesMade;
	}

	private Relationship checkForInactiveRel(Concept concept, Relationship stated) {
		Relationship inactiveStated = null;
		for (Relationship potentialReactivation : concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.INACTIVE)) {
			if (potentialReactivation.equals(stated)) {
				inactiveStated = potentialReactivation;
			}
		}
		return inactiveStated;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allPotential = GraphLoader.getGraphLoader().getConcept(subHierarchyStr).getDescendants(NOT_SET);
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept thisConcept : allPotential) {
			String semTag = SnomedUtils.deconstructFSN(thisConcept.getFsn())[1];
			if (semTag.equals(targetSemanticTag)) {
				Set<Relationship> missingFromStated = determineInferredMissingFromStated(thisConcept);
				if (missingFromStated.size() > 0) {
					allAffected.add(thisConcept);
				}
			}
		}
		LOGGER.info (allAffected.size() + " concepts affected.");
		return new ArrayList<Component>(allAffected);
	}

	private Set<Relationship> determineInferredMissingFromStated(
			Concept thisConcept) {
		//Work through all inferred attributes of interest and see if they have no stated counterpart
		Set<Relationship> missingFromStated = new HashSet<Relationship>();
		for (Relationship inferred : thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (attributesOfInterest.contains(inferred.getType())) {
				if (inferred.getGroupId() != 0) {
					LOGGER.info("Relationship being compared is not group 0: " + inferred);
				}
				boolean statedMatchFound = false;
				for (Relationship stated : thisConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (stated.equals(inferred)) {
						statedMatchFound = true;
					}
				}
				if (!statedMatchFound) {
					missingFromStated.add(inferred);
				}
			}
		}
		return missingFromStated;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

}
