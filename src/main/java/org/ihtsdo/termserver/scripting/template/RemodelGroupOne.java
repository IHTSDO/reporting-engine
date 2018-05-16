package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.ihtsdo.otf.authoringtemplate.domain.logical.Attribute;
import org.ihtsdo.otf.authoringtemplate.domain.logical.AttributeGroup;

import us.monoid.json.JSONObject;

/**
 * QI
 * Where a concept has limited modeling, pull the most specific attributes available 
 * into group 1.  Skip any cases of multiple attributes types with values that are not in 
 * the same subhierarchy.
 */
public class RemodelGroupOne extends TemplateFix {

	protected RemodelGroupOne(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		RemodelGroupOne app = new RemodelGroupOne(null);
		try {
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
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		selfDetermining = true;
		runStandAlone = true; 
		classifyTasks = true;
		additionalReportColumns = "CharacteristicType, Template, AFTER Stated, BEFORE Stated, Inferred";
		
		/*subHierarchyStr = "125605004";  // |Fracture of bone (disorder)|
		templateNames = new String[] {	"Fracture of Bone Structure.json",
										"Fracture Dislocation of Bone Structure.json",
										"Pathologic fracture of bone due to Disease.json"};*/
		//subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		//templateNames = new String[] {"Chronic Inflammatory Disorder.json"};
		
		//subHierarchyStr =  "126537000";  //QI-14 |Neoplasm of bone (disorder)|
		//templateNames = new String[] {"Neoplasm of Bone.json"};
		
		/*subHierarchyStr =  "34014006"; //QI-15 |Viral disease (disorder)|
		templateNames = new String[] {	"Infection caused by virus.json",
										"Infection of bodysite caused by virus.json"};*/
		
		subHierarchyStr =  "87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"Infection caused by bacteria with optional bodysite.json"};
		
		//subHierarchyStr =  "95896000";  //QI-19  |Protozoan infection (disorder)|
		//templateNames = new String[] {"Infection caused by protozoa.json"};
		
		super.init(args);
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = remodelGroupOne(task, loadedConcept, templates.get(0));
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

	private int remodelGroupOne(Task t, Concept c, Template template) throws TermServerScriptException {
		int changesMade = 0;
		
		//Get a copy of the stated and inferred modelling "before"
		String statedForm = SnomedUtils.getModel(c, CharacteristicType.STATED_RELATIONSHIP);
		String inferredForm = SnomedUtils.getModel(c, CharacteristicType.INFERRED_RELATIONSHIP);
		
		RelationshipGroup groupOne = new RelationshipGroup(1);
		
		//Work through the attributes in the template and see if we can satisfy those from the inferred
		//relationships on the concept
		AttributeGroup firstGroup = template.getAttributeGroups().toArray(new AttributeGroup[0])[1];
		for (Attribute a : firstGroup.getAttributes()) {
			changesMade += findAttributeToState(t, c, a, groupOne);
		}
		if (changesMade > 0) {
			c.addRelationshipGroup(groupOne);
			String modifiedForm = SnomedUtils.getModel(c, CharacteristicType.STATED_RELATIONSHIP);
			report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_GROUP_ADDED, modifiedForm, statedForm, inferredForm);
		}
		return changesMade;
	}

	private int findAttributeToState(Task t, Concept c, Attribute a, RelationshipGroup group) throws TermServerScriptException {
		//Do we have this attribute type in the inferred form?
		Concept type = gl.getConcept(a.getType());
		
		//First see if we can find this value in the stated form and move it!
		List<Relationship> existingRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, ActiveState.ACTIVE);
		if (existingRels.size() == 1) {
			//Inactivate the existing rel and move it into group 1
			Relationship existingRel = existingRels.get(0);
			Relationship moved = existingRel.clone(null);
			existingRel.setActive(false);
			moved.setGroupId(1);
			c.addRelationship(moved);
			return CHANGE_MADE;
		} else {
			//Otherwise attempt to satisfy from inferred rels.
			Set<Concept> values = SnomedUtils.getTargets(c, new Concept[] {type}, CharacteristicType.INFERRED_RELATIONSHIP);
			
			//Remove the less specific values from this list
			removeRedundancies(values);
			
			//Do we have a single value?  Can't model otherwise
			if (values.size() == 0) {
				return NO_CHANGES_MADE;
			} else if (values.size() == 1) {
				Relationship r = new Relationship (c, type, values.iterator().next(), group.getGroupId());
				group.addRelationship(r);
				return CHANGE_MADE;
			} else {
				throw new ValidationFailure (c , "Multiple " + type + " : " + values.stream().map(v -> v.toString()).collect(Collectors.joining(", ")));
			}
		}
	}

	private void removeRedundancies(Set<Concept> concepts) throws TermServerScriptException {
		Set<Concept> redundant = new HashSet<>();
		//For each concept, it is redundant if any of it's descendants are also present
		for (Concept concept : concepts) {
			Set<Concept> descendants = new HashSet<>(descendantsCache.getDescendents(concept));
			descendants.retainAll(concepts);
			if (descendants.size() > 0) {
				redundant.add(concept);
			}
		}
		concepts.removeAll(redundant);
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find concepts that only have ungrouped attributes, or none at all.
		List<Component> processMe = new ArrayList<>();
		nextConcept:
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getGroupId() != UNGROUPED) {
					continue nextConcept;
				}
			}
			processMe.add(c);
		}
		return processMe;
	}
	
}
