package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
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
 * QI-21, QI-23,
 * Where a concept has limited modeling, pull the most specific attributes available 
 * into group 1.  Skip any cases of multiple attributes types with values that are not in 
 * the same subhierarchy.
 */
public class RemodelGroupOne extends TemplateFix {
	
	String[] whitelist = new String[] { "co-occurrent" };
	Set<Concept> groupedAttributeTypes = new HashSet<>();
	Set<Concept> ungroupedAttributeTypes = new HashSet<>();

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
		populateEditPanel = false;
		populateTaskDescription = false;
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
		templateNames = new String[] {	"Infection caused by virus with optional bodysite.json"};*/
		
		subHierarchyStr =  "87628006";  //QI-16 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"Infection caused by bacteria with optional bodysite.json"}; 
		
		//subHierarchyStr =  "95896000";  //QI-19  |Protozoan infection (disorder)|
		//templateNames = new String[] {"Infection caused by protozoa.json"};
		
		super.init(args);
	}
	
	protected void postInit() throws TermServerScriptException {
		super.postInit();
		
		//Populate grouped and ungrouped attributes
		Iterator<AttributeGroup> groupIterator = templates.get(0).getAttributeGroups().iterator();

		ungroupedAttributeTypes = groupIterator.next().getAttributes().stream()
							.map(a -> gl.getConceptSafely(a.getType()))
							.collect(Collectors.toSet());
		
		groupedAttributeTypes = groupIterator.next().getAttributes().stream()
				.map(a -> gl.getConceptSafely(a.getType()))
				.collect(Collectors.toSet());
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		if (loadedConcept.getConceptId().equals("72294005")) {
			debug("Check me");
		}
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
		
		RelationshipGroup groupOne = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, 1);
		if (groupOne == null) {
			groupOne = new RelationshipGroup(1);
		}
		
		//Work through the attributes in the template and see if we can satisfy those from the ungrouped stated
		//or inferred relationships on the concept
		AttributeGroup firstTemplateGroup = template.getAttributeGroups().toArray(new AttributeGroup[0])[1];
		for (Attribute a : firstTemplateGroup.getAttributes()) {
			int statedChangeMade = findAttributeToState(t, c, a, groupOne, CharacteristicType.STATED_RELATIONSHIP);
			if (statedChangeMade == NO_CHANGES_MADE) {
				changesMade += findAttributeToState(t, c, a, groupOne, CharacteristicType.INFERRED_RELATIONSHIP);
			} else {
				changesMade += CHANGE_MADE;
			}
		}
		
		if (changesMade > 0) {
			c.addRelationshipGroup(groupOne);
			String modifiedForm = SnomedUtils.getModel(c, CharacteristicType.STATED_RELATIONSHIP);
			report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_GROUP_ADDED, modifiedForm, statedForm, inferredForm);
		}
		
		//Now work through all relationship and move any ungrouped attributes out of groups
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getGroupId() != UNGROUPED && ungroupedAttributeTypes.contains(r.getType())) {
				Relationship moved = r.clone(null);
				moved.setGroupId(UNGROUPED);
				c.addRelationship(moved);
				if (r.isReleased()) {
					r.setActive(false);
				} else {
					c.removeRelationship(r);
				}
				report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, "Ungrouped relationship moved out of group: " + r);
				changesMade++;
			}
		}
		return changesMade;
	}

	private int findAttributeToState(Task t, Concept c, Attribute a, RelationshipGroup group, CharacteristicType charType) throws TermServerScriptException {
		//Do we have this attribute type in the inferred form?
		Concept type = gl.getConcept(a.getType());
		
		//Otherwise attempt to satisfy from existing relationships
		Set<Concept> values = SnomedUtils.getTargets(c, new Concept[] {type}, charType);
		
		//Remove the less specific values from this list
		removeRedundancies(values);
		
		//Do we have a single value?  Can't model otherwise
		if (values.size() == 0) {
			//Is this value hard coded in the template?  Only do this for stated characteristic type, so we don't duplicate
			if (a.getValue() != null && charType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
				Relationship constantRel = new Relationship(c, type, gl.getConcept(a.getValue()), group.getGroupId());
				c.addRelationship(constantRel);
				report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, "Template specified constant: " + constantRel);
				return CHANGE_MADE;
			} else {
				return NO_CHANGES_MADE;
			}
		} else if (values.size() == 1) {
			//If we're pulling from stated rels, remove any group 0 instances
			if (charType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
				List<Relationship> ungroupedRels = c.getRelationships(charType, type, UNGROUPED);
				if (ungroupedRels.size() == 1) {
					Relationship ungroupedRel = ungroupedRels.get(0);
					if (ungroupedRel.isReleased()) {
						ungroupedRel.setActive(false);
					} else {
						c.removeRelationship(ungroupedRel);
					}
				}
			}
			
			//If this relationship type doesn't already exist in this group, stated, add it
			if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, group.getGroupId()).size() == 0) {
				Relationship r = new Relationship (c, type, values.iterator().next(), group.getGroupId());
				group.addRelationship(r);
				return CHANGE_MADE;
			} else {
				return NO_CHANGES_MADE;
			}
		} else {
			throw new ValidationFailure (c , "Multiple non-subsuming " + type + " values : " + values.stream().map(v -> v.toString()).collect(Collectors.joining(" and ")));
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
			/*if (!c.getConceptId().equals("48113006")) {
				continue;
			}*/
			if (isWhiteListed(c)) {
				warn ("Whitelisted: " + c);
			} else {
				boolean hasGroupedAttributes = false;
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					//If the concept has no grouped attributes we want it, and also 
					//if there are any ungrouped attributes in group 1, or grouped attributes
					//in group 0 (even if group 1 also exists!)
					if (r.getGroupId() == UNGROUPED) {
						if (groupedAttributeTypes.contains(r.getType())) {
							processMe.add(c);
							continue nextConcept;
						}
					} else if (ungroupedAttributeTypes.contains(r.getType())) {
						processMe.add(c);
						continue nextConcept;
					}
					
					if (r.getGroupId() != UNGROUPED) {
						hasGroupedAttributes = true;
					}
				}
				
				if (!hasGroupedAttributes) {
					processMe.add(c);
				}
			}
		}
		return processMe;
	}

	private boolean isWhiteListed(Concept c) {
		//Does the FSN contain one of our white listed words?
		for (String word : whitelist) {
			if (c.getFsn().contains(word)) {
				return true;
			}
		}
		return false;
	}
	
}
