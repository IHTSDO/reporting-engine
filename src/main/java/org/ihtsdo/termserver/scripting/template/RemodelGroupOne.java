package org.ihtsdo.termserver.scripting.template;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import org.snomed.authoringtemplate.domain.logical.*;

/**
 * QI-21 (Bacterial), QI-23 (Viral), QI-30 (Bone)
 * Where a concept has limited modeling, pull the most specific attributes available 
 * into group 1.  Skip any cases of multiple attributes types with values that are not in 
 * the same subhierarchy.
 */
public class RemodelGroupOne extends TemplateFix {
	
	String[] whitelist = new String[] { /*"co-occurrent"*/ };
	Set<Concept> groupedAttributeTypes = new HashSet<>();
	Set<Concept> ungroupedAttributeTypes = new HashSet<>();
	Set<Concept> formNewGroupAround = new HashSet<>();

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
		
		subHierarchyStr = "125605004";  // QI-30 |Fracture of bone (disorder)|
		templateNames = new String[] {	"Fracture of Bone Structure.json" }; /*,
										"Fracture Dislocation of Bone Structure.json",
										"Pathologic fracture of bone due to Disease.json"};
		/*
		subHierarchyStr =  "128294001";  // QI-9 |Chronic inflammatory disorder (disorder)
		templateNames = new String[] {"Chronic Inflammatory Disorder.json"};
		
		subHierarchyStr =  "126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {"Neoplasm of Bone.json"};
		*/
		formNewGroupAround.add(FINDING_SITE);
		formNewGroupAround.add(CAUSE_AGENT);
		formNewGroupAround.add(ASSOC_MORPH);
		/*
		subHierarchyStr =  "34014006"; //QI-15 + QI-23 |Viral disease (disorder)|
		templateNames = new String[] {	"Infection caused by virus with optional bodysite.json"};

		subHierarchyStr =  "87628006";  //QI-16 + QI-21 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"Infection caused by bacteria with optional bodysite.json"}; 
		
		subHierarchyStr =  "95896000";  //QI-27  |Protozoan infection (disorder)|
		templateNames = new String[] {"Infection caused by Protozoa with optional bodysite.json"};
		 */
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
		/*if (loadedConcept.getConceptId().equals("72294005")) {
			debug("Check me");
		}*/
		int changesMade = remodelGroupOne(task, loadedConcept, templates.get(0));
		if (changesMade > 0) {
			List<String> focusConceptIds = templates.get(0).getLogicalTemplate().getFocusConcepts();
			if (focusConceptIds.size() == 1) {
				checkAndSetProximalPrimitiveParent(task, loadedConcept, gl.getConcept(focusConceptIds.get(0)));
			} else {
				report (task, concept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Cannot remodel PPP - template specifies multiple focus concepts");
			}
			
			try {
				updateConcept(task,loadedConcept,"");
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
			int statedChangeMade = findAttributeToState(t, template, c, a, groupOne, CharacteristicType.STATED_RELATIONSHIP);
			if (statedChangeMade == NO_CHANGES_MADE) {
				changesMade += findAttributeToState(t, template, c, a, groupOne, CharacteristicType.INFERRED_RELATIONSHIP);
			} else {
				changesMade += CHANGE_MADE;
			}
		}
		
		//Have we formed a second group?  Find different attributes if so
		RelationshipGroup groupTwo = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, 2);
		if (groupTwo != null) {
			for (Attribute a : firstTemplateGroup.getAttributes()) {
				int statedChangeMade = findAttributeToState(t, template, c, a, groupTwo, CharacteristicType.STATED_RELATIONSHIP);
				if (statedChangeMade == NO_CHANGES_MADE) {
					changesMade += findAttributeToState(t, template, c, a, groupTwo, CharacteristicType.INFERRED_RELATIONSHIP);
				} else {
					changesMade += CHANGE_MADE;
				}
			}
		}
		
		if (changesMade > 0) {
			c.addRelationshipGroup(groupOne);
			if (groupTwo != null) {
				c.addRelationshipGroup(groupTwo);
			}
			String modifiedForm = SnomedUtils.getModel(c, CharacteristicType.STATED_RELATIONSHIP);
			report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_GROUP_ADDED, modifiedForm, statedForm, inferredForm);
		}
		
		//Now work through all relationship and move any ungrouped attributes out of groups
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getGroupId() != UNGROUPED && ungroupedAttributeTypes.contains(r.getType())) {
				int originalGroup = r.getGroupId();
				r.setGroupId(UNGROUPED);
				report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, "Ungrouped relationship moved out of group " + originalGroup +": " + r);
				changesMade++;
			}
		}
		return changesMade;
	}

	private int findAttributeToState(Task t, Template template, Concept c, Attribute a, RelationshipGroup group, CharacteristicType charType) throws TermServerScriptException {
		//Do we have this attribute type in the inferred form?
		Concept type = gl.getConcept(a.getType());
		
		//Otherwise attempt to satisfy from existing relationships
		Set<Concept> values = SnomedUtils.getTargets(c, new Concept[] {type}, charType);
		
		//Remove the less specific values from this list
		removeRedundancies(values);
		
		//Do we have a single value?  Can't model otherwise
		boolean additionNeeded = true;
		if (values.size() == 0) {
			//Is this value hard coded in the template?  Only do this for stated characteristic type, so we don't duplicate
			if (a.getValue() != null && charType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
				Relationship constantRel = new Relationship(c, type, gl.getConcept(a.getValue()), group.getGroupId());
				group.addRelationship(constantRel);
				report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, "Template specified constant: " + constantRel);
				return CHANGE_MADE;
			} else {
				return NO_CHANGES_MADE;
			}
		} else if (values.size() == 1) {
			//If we're pulling from stated rels, move any group 0 instances to the new group id
			if (charType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
				List<Relationship> ungroupedRels = c.getRelationships(charType, type, UNGROUPED);
				if (ungroupedRels.size() == 1) {
					Relationship ungroupedRel = ungroupedRels.get(0);
					ungroupedRel.setGroupId(group.getGroupId());
					report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, "Ungrouped relationship moved to group " + group.getGroupId() + ": " + ungroupedRel);
					return CHANGE_MADE;
				}
			}
			
			//If this relationship type doesn't already exist in this group, stated, add it
			if (additionNeeded && c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, group.getGroupId()).size() == 0) {
				Relationship r = new Relationship (c, type, values.iterator().next(), group.getGroupId());
				group.addRelationship(r);
				return CHANGE_MADE;
			} else {
				return NO_CHANGES_MADE;
			}
		} else if (values.size() > 2) {
			//We can only deal with 2
			throw new ValidationFailure (c , "Multiple non-subsuming " + type + " values : " + values.stream().map(v -> v.toString()).collect(Collectors.joining(" and ")));
		} else {
			return considerSecondGrouping(t, template, c, type, group, charType, values);
		}
	}

	private int considerSecondGrouping(Task t, Template template, Concept c, Concept type, RelationshipGroup group,
			CharacteristicType charType, Set<Concept> values) throws TermServerScriptException {
		//Sort the values alphabetically to ensure the right one goes into the right group
		List<Concept> disjointAttributeValues = new ArrayList<>(values);
		disjointAttributeValues.sort(Comparator.comparing(Concept::getFsn));
		
		//If it's a finding site and either attribute is in group 0, we'll leave it there
		if (type.equals(FINDING_SITE) && ( c.getRelationships(charType, type, disjointAttributeValues.get(0), UNGROUPED, ActiveState.ACTIVE).size() > 0) ||
				c.getRelationships(charType, type, disjointAttributeValues.get(1), UNGROUPED, ActiveState.ACTIVE).size() > 0 ) {
			debug ("Finding site in group 0, skipping: " + c );
			return NO_CHANGES_MADE;
		}
		
		//Does the template specify one of the values specifically in group 0?  No need to move
		//to group 2 in that case.
		for ( Attribute a : template.getLogicalTemplate().getUngroupedAttributes()) {
			if (a.getValue() != null && a.getType().equals(type.getConceptId())) {
				for (Concept value : values) {
					if (a.getValue().equals(value.getConceptId())) {
						debug ( type + "= " + value + " specified in template, no 2nd group required." );
						return NO_CHANGES_MADE;
					}
				}
			}
		}
		
		//Is this an attribute that we might want to form a new group around?
		if (formNewGroupAround.contains(type)) {
			Concept[] affinitySorted = sortByAffinity(disjointAttributeValues, type, c);
			//Add the attributes to the appropriate group.  We only need to do this for the first
			//pass, since that will assign both values
			int groupId = 1;
			int changesMade = 0;
			for (Concept value : affinitySorted) {
				changesMade += replaceRelationship(t, c, type, value, groupId, false);
				groupId++;
			}
			return changesMade;
		} else { 
			//If we've *already* formed a new group, then we could use it.
			if (group.getGroupId() == 2 && c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, group.getGroupId()).size() == 0) {
				Concept[] affinitySorted = sortByAffinity(disjointAttributeValues, type, c);
				Relationship r = new Relationship (c, type, affinitySorted[1], group.getGroupId());
				group.addRelationship(r);
				return CHANGE_MADE;
			}
		}
		return NO_CHANGES_MADE;
	}

	/* Sort the two attribute values so that they'll "chum up" with other attributes that they're
	 * already grouped with in the inferred form.
	 */
	private Concept[] sortByAffinity(List<Concept> values, Concept type, Concept c) {
		Concept[] sortedValues = new Concept[2];
		//Do we already have attributes in group 1 or 2 which should be grouped with one of our values?
		nextValue:
		for (Concept value : values) {
			Relationship proposedRel = new Relationship (type, value);
			for (int groupId = 1; groupId <= 2; groupId++) {
				int idx = groupId -1;  //Group 1 -> idx 0, Group 2 -> idx 1
				for (Relationship r : c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, groupId).getRelationships()) {
					if (SnomedUtils.isGroupedWith(r, proposedRel, c, CharacteristicType.INFERRED_RELATIONSHIP)) {
						if (sortedValues[idx] == null) {
							sortedValues[idx] = value;
							continue nextValue;
						} else {
							throw new IllegalStateException("In " + c + "inferred, " + r + " is grouped with " + proposedRel + " but also " + new Relationship (type, sortedValues[idx]));
						}
					}
				}
			}
		}
		
		//Remove any we've assigned from the list
		values.removeAll(Arrays.asList(sortedValues));
		
		//If we've assigned one, we can auto assign the other.
		//Or if neither, keep the original order ie alphabetical
		Iterator<Concept> iter = values.iterator();
		for (int i=0; i <= 1; i++) {
			if (sortedValues[i] == null) {
				sortedValues[i] = iter.next();
			}
		}
		return sortedValues;
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
		List<Concept> processMe = new ArrayList<>();
		nextConcept:
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			/*if (!c.getConceptId().equals("195911009")) {
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
		processMe.sort(Comparator.comparing(Concept::getFsn));
		List<Component> firstPassComplete = firstPassRemodel(processMe);
		return firstPassComplete;
	}

	private List<Component> firstPassRemodel(List<Concept> processMe) throws TermServerScriptException {
		setQuiet(true);
		List<Component> firstPassComplete = new ArrayList<>();
		List<ValidationFailure> failures = new ArrayList<>();
		Set<Concept> noChangesMade = new HashSet<>();
		for (Concept c : processMe) {
			try {
				Concept cClone = c.cloneWithIds();
				int changesMade = remodelGroupOne(null, cClone, templates.get(0));
				if (changesMade > 0) {
					firstPassComplete.add(c);
				} else {
					noChangesMade.add(c);
				}
			} catch (ValidationFailure vf) {
				failures.add(vf);
			}
		}
		setQuiet(false);
		for (ValidationFailure failure : failures) {
			report (failure);
		}
		for (Concept unchanged : noChangesMade) {
			report (null, unchanged, Severity.NONE, ReportActionType.NO_CHANGE, "");
		}
		return firstPassComplete;
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
