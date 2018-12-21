package org.ihtsdo.termserver.scripting.template;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.authoringtemplate.domain.logical.*;

import com.google.common.io.Files;

/**
 * Where a concept has limited modeling, pull the most specific attributes available 
 * into group 1.  Skip any cases of multiple attributes types with values that are not in 
 * the same subhierarchy.
 */
public class GroupRemodel extends TemplateFix {
	
	Set<Concept> groupedAttributeTypes = new HashSet<>();
	Set<Concept> ungroupedAttributeTypes = new HashSet<>();
	Set<Concept> formNewGroupAround = new HashSet<>();
	
	boolean skipMultipleUngroupedFindingSites = true;
	
	File alreadyProcessedFile;

	protected GroupRemodel(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		GroupRemodel app = new GroupRemodel(null);
		try {
			ReportSheetManager.targetFolderId = "15FSegsDC4Tz7vP5NPayGeG2Q4SB1wvnr"; //QI  / Group One Remodel
			//ReportSheetManager.targetFolderId = "18xZylGhgL7ML782pu6-6u_VUw3p5Hfr7"; //QI / Development
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		selfDetermining = true;
		runStandAlone = true; 
		classifyTasks = true;
		populateEditPanel = true;
		populateTaskDescription = true;
		additionalReportColumns = "CharacteristicType, Template, AFTER Stated, BEFORE Stated, Inferred";
		//includeComplexTemplates = true;
		
		formNewGroupAround.add(FINDING_SITE);
		formNewGroupAround.add(CAUSE_AGENT);
		formNewGroupAround.add(ASSOC_MORPH);
		
		if (exclusionWords == null) {
			exclusionWords = new ArrayList<>();
		}
		/*
		subHierarchyECL =  "<< 125605004";  // QI-30 |Fracture of bone (disorder)|
		templateNames = new String[] {	"templates/Fracture of Bone Structure.json" }; /*,
										"templates/Fracture Dislocation of Bone Structure.json",
										"templates/Pathologic fracture of bone due to Disease.json"};
		
		// QI-36 Part 1 |Chronic inflammatory disorder (disorder)
		subHierarchyECL =  "<< 128294001 MINUS  (<< 128294001 : 246075003 |Causative agent (attribute)| = <<410607006 |Organism (organism)|)";
		templateNames = new String[] {"templates/Chronic Inflammatory Disorder.json"};
		exclusionWords.add("arthritis");
		
		// QI-36 Part 2 |Chronic inflammatory disorder (disorder)
		subHierarchyECL =  "<< 128294001 : 246075003 |Causative agent (attribute)| = <<410607006 |Organism (organism)|";
		templateNames = new String[] {"templates/Infectious Chronic Inflammatory Disorder.json"};
		exclusionWords.add("arthritis");
		
		subHierarchyECL =  "<< 126537000";  //QI-14 |Neoplasm of bone (disorder)|
		templateNames = new String[] {"templates/Neoplasm of Bone.json"};

		subHierarchyECL =  "<< 34014006"; //QI-15 + QI-23 |Viral disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by virus with optional bodysite.json"};

		subHierarchyECL =  "<< 87628006";  //QI-16 + QI-21 |Bacterial infectious disease (disorder)|
		templateNames = new String[] {	"templates/Infection caused by bacteria with optional bodysite.json"}; 
		
		subHierarchyECL =  "<< 95896000";  //QI-27  |Protozoan infection (disorder)|
		templateNames = new String[] {"templates/Infection caused by Protozoa with optional bodysite.json"};
		
		
		subHierarchyECL =  "<< 74627003";  //QI-48 |Diabetic Complication|
		templateNames = new String[] {	"templates/Complication co-occurrent and due to Diabetes Melitus.json",
				//"templates/Complication co-occurrent and due to Diabetes Melitus - Minimal.json"
				};
		
		subHierarchyECL =  "<< 3218000"; //QI-70 |Mycosis (disorder)|
		templateNames = new String[] {	"templates/infection/Infection caused by Fungus.json"};
		
		subHierarchyECL =  "<< 17322007"; //QI-116 |Parasite (disorder)|
		templateNames = new String[] {	"templates/Infection caused by Parasite.json"};
		
		
		subHierarchyECL =  "<< 125643001"; //QI-117 |Open wound| 
		templateNames = new String[] {	"templates/wound/wound of bodysite due to event.json" };
		exclusionWords.add("complication");
		exclusionWords.add("fracture");
		includeDueTos = true;
		
		*/
		subHierarchyECL = "<<40733004|Infectious disease|"; //QI-159
		templateNames = new String[] {	"templates/infection/Infection NOS.json" };
		setExclusions(new String[] {"87628006 |Bacterial infectious disease (disorder)|","34014006 |Viral disease (disorder)|",
				"3218000 |Mycosis (disorder)|","8098009 |Sexually transmitted infectious disease (disorder)|", 
				"17322007 |Disease caused by parasite (disorder)|", "91302008 |Sepsis (disorder)|"});
		exclusionWords.add("shock");
		alreadyProcessedFile = new File(".QI-159_already_processed.txt");
		if (!alreadyProcessedFile.exists()) {
			
		}
		super.init(args);
		
		//Ensure our ECL matching more than 0 concepts
		if (findConcepts(project.getBranchPath(), subHierarchyECL).size() == 0) {
			throw new TermServerScriptException(subHierarchyECL + " returned 0 rows");
		}
	}
	
	public void postInit() throws TermServerScriptException {
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
		
		//Record that we've looked at this file
		if (alreadyProcessedFile != null) {
			try {
				FileUtils.writeStringToFile(alreadyProcessedFile, loadedConcept.toString()+"\n", true);
			} catch (IOException e) {
				throw new TermServerScriptException("Failed to record already processed in " + alreadyProcessedFile,e);
			}
		}

		if (!loadedConcept.isActive()) {
			report(task, concept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Attempt to remodel an inactive concept");
			return NO_CHANGES_MADE;
		}
		int changesMade = remodelConcept(task, loadedConcept, templates.get(0));
		if (changesMade > 0) {
			List<String> focusConceptIds = templates.get(0).getLogicalTemplate().getFocusConcepts();
			if (focusConceptIds.size() == 1) {
				checkAndSetProximalPrimitiveParent(task, loadedConcept, gl.getConcept(focusConceptIds.get(0)));
			} else {
				report (task, concept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Cannot remodel PPP - template specifies multiple focus concepts");
			}
			
			try {
				updateConcept(task,loadedConcept,info);
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int remodelConcept(Task t, Concept c, Template template) throws TermServerScriptException {
		int changesMade = 0;
		
		if (c.getConceptId().equals("43925005")) {
		//	debug("Check me");
		}

		//Create as many groups as required, but minimum 3
		int numConceptGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP).size();
		int numTemplateGroups = template.getAttributeGroups().size();
		int maxGroups = Math.max(Math.max(numConceptGroups, numTemplateGroups),3);
		List<RelationshipGroup> groups = new ArrayList<>(maxGroups);
		
		//Map the group id in the concept to the group number in the template (and back!)
		Map<Integer, Integer> additionalGroupNums = new HashMap<>();
		
		//Get a copy of the stated and inferred modelling "before"
		String statedForm = SnomedUtils.getModel(c, CharacteristicType.STATED_RELATIONSHIP);
		String inferredForm = SnomedUtils.getModel(c, CharacteristicType.INFERRED_RELATIONSHIP);
		
		for (int groupId = 0; groupId < maxGroups; groupId++) {
			//Prepare for groups 0 and 1 anyway
			RelationshipGroup conceptGroup = c.getRelationshipGroup(CharacteristicType.STATED_RELATIONSHIP, groupId);
			//Clone so that we're able to modify actual relationship once we've finished moving rels around
			if (conceptGroup != null) {
				groups.add(conceptGroup.clone());
			} else if (groups.size() <= groupId) {
				groups.add(new RelationshipGroup(groupId));
			}
		}
		
		//Remove any ungrouped relationship types that exist in the template as grouped.  They'll get picked up from 
		//inferred if they're still needed.  Note that this just empties out the clone, not the original rel
		removeGroupedTypes(c, groups.get(UNGROUPED), template);
		
		//Similarly, remove any multiple attribute types
		removeMultiples(groups);
		
		//Work through the attribute groups in the template and see if we can satisfy them
		for (int templateGroupId = 0; templateGroupId <  template.getAttributeGroups().size(); templateGroupId++) {
			//Are we adding more groups as we find attributes to group around?  Successive template groups
			//need to be mapped to later (+n) groups in the concept, otherwise we'll see, like Interprets
			//merged in with causative agents
			AttributeGroup templateGroup = template.getAttributeGroups().toArray(new AttributeGroup[0])[templateGroupId];
			boolean templateGroupOptional = templateGroup.getCardinalityMin().equals("0");
			for (Attribute a : templateGroup.getAttributes()) {
				changesMade += findAttributeToState(t, template, c, a, groups, templateGroupId, CharacteristicType.INFERRED_RELATIONSHIP, templateGroupOptional, additionalGroupNums);
			}

			//Have we formed an additional group? Find different attributes if so
			//TODO Check group 1 has 1 to many cardinality before assuming we can replicate it
			if (templateGroupId == 1) {
				Iterator<Integer> additionalGroupItr = additionalGroupNums.keySet().iterator();
				while (additionalGroupItr.hasNext()) {
					RelationshipGroup additionalGroup = groups.get(additionalGroupItr.next());
					if (additionalGroup != null && !additionalGroup.isEmpty()) {
						for (Attribute a : templateGroup.getAttributes()) {
							//Does this group ALREADY satisfy the template attribute?
							if (TemplateUtils.containsMatchingRelationship(additionalGroup, a, gl.getDescendantsCache())) {
								continue;
							}
							//Don't check stated relationships because we could then run code to work with a single relationship when 
							//we should be choosing between multiple of the same type available in the inferred view
							changesMade += findAttributeToState(t, template, c, a, groups, 2, CharacteristicType.INFERRED_RELATIONSHIP, templateGroupOptional, additionalGroupNums);
						}
					}
				}
			}
		}
		
		int ignoreUngroupedMoves = changesMade;
		
		//Now work through all relationship and move any ungrouped attributes out of groups
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getGroupId() != UNGROUPED && ungroupedAttributeTypes.contains(r.getType())) {
				int originalGroup = r.getGroupId();
				r.setGroupId(UNGROUPED);
				report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, "Ungrouped relationship moved out of group " + originalGroup +": " + r);
				changesMade++;
			}
		}
		
		//Do we end up with any single relationships grouped?  Make ungrouped if so
		for (RelationshipGroup group : groups) {
			if (group.getGroupId() != UNGROUPED) {
				if (group.getRelationships().size() == 1) {
					Relationship lonelyRelationship = group.getRelationships().get(0);
					groups.set(group.getGroupId(), new RelationshipGroup(group.getGroupId()));
					lonelyRelationship.setGroupId(UNGROUPED);
					if (!groups.get(UNGROUPED).containsTypeValue(lonelyRelationship)) {
						groups.get(UNGROUPED).addRelationship(lonelyRelationship);
					}
				}
			}
		}
		
		if (ignoreUngroupedMoves > 0) {
			changesMade += applyRemodelledGroups(t,c,groups);
			changesMade += removeRedundandGroups(t,c);
			String modifiedForm = SnomedUtils.getModel(c, CharacteristicType.STATED_RELATIONSHIP);
			
			//Now check that we've actually ended up making actual changes
			if (modifiedForm.equals(statedForm)) {
				throw new ValidationFailure(c, "Stated modelling unchanged");
			}
			report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_GROUP_ADDED, modifiedForm, statedForm, inferredForm);
		}
		return changesMade;
	}

	private void removeMultiples(List<RelationshipGroup> groups) {
		for (RelationshipGroup group : groups) {
			Set<Concept> typesSeen = new HashSet<>();
			for (Relationship r : new ArrayList<>(group.getRelationships())) {
				if (typesSeen.contains(r.getType())) {
					group.removeRelationship(r);
				} else {
					typesSeen.add(r.getType());
				}
			}
		}
		
	}

	private void removeGroupedTypes(Concept c, RelationshipGroup group0, Template t) {
		//Work out all attribute types grouped in the template
		List<Concept> allowTypes = new ArrayList<>();
		List<Concept> removeTypes = new ArrayList<>();
		boolean ungrouped = true;
		for (AttributeGroup attribGroup : t.getAttributeGroups()) {
			if (ungrouped == true) {
				//ignore any that are also ungrouped
				allowTypes.addAll(attribGroup.getAttributes().stream()
						.map(a -> gl.getConceptSafely(a.getType()))
						.collect(Collectors.toList()));
				ungrouped = false;
			} else {
				removeTypes.addAll(attribGroup.getAttributes().stream()
						.map(a -> gl.getConceptSafely(a.getType()))
						.collect(Collectors.toList()));
			}
		}
		removeTypes.removeAll(allowTypes);
		for (Relationship r : new ArrayList<Relationship>(group0.getRelationships())) {
			if (removeTypes.contains(r.getType())) {
				group0.removeRelationship(r);
			}
		}
	}

/*	private void removeDuplicateTypes(Concept c, RelationshipGroup group) {
		List<Relationship> originalRels = new ArrayList<>(group.getRelationships());
		for (Relationship potentialTypeDup : originalRels) {
			List<Relationship> dupTypes = group.getRelationships().stream()
											.filter(r -> r.getType().equals(potentialTypeDup.getType()))
											.sorted((Relationship r1, Relationship r2) -> r1.getType().getFsn().compareTo(r2.getType().getFsn()))
											.collect(Collectors.toList());
			if (dupTypes.size() > 1) {
				//We'll keep the first element
				dupTypes.remove(0);
				warn ("Removing duplicate type " + dupTypes.get(0) + "  in " + c);
				group.getRelationships().removeAll(dupTypes);
			}
		}
	}*/



	private int findAttributeToState(Task t, Template template, Concept c, Attribute a, List<RelationshipGroup> groups, int groupOfInterest, CharacteristicType charType, boolean templateGroupOptional, Map<Integer, Integer> additionalGroupNums) throws TermServerScriptException {
		RelationshipGroup group = groups.get(groupOfInterest);
		
		//Do we have this attribute type in the inferred form?
		Concept type = gl.getConcept(a.getType());
		
		//Is this value hard coded in the template?  Only want to do this where the template group is required.
		if (a.getValue() != null && !templateGroupOptional) {
			Relationship constantRel = new Relationship(c, type, gl.getConcept(a.getValue()), group.getGroupId());
			//Don't add if our group already contains this, or something more specific
			if (!group.containsTypeValue(type, gl.getDescendantsCache().getDescendentsOrSelf(a.getValue()))) {
				//Does this constant already exist ungrouped?
				if (groups.get(UNGROUPED).containsTypeValue(constantRel)) {
					Relationship ungroupedConstant = groups.get(UNGROUPED).getTypeValue(constantRel);
					groups.get(UNGROUPED).removeRelationship(ungroupedConstant);
					ungroupedConstant.setGroupId(group.getGroupId());
					group.addRelationship(ungroupedConstant);
					report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, "Template specified constant, moved from group 0: " + ungroupedConstant);
				} else {
					group.addRelationship(constantRel);
					report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, "Template specified constant: " + constantRel);
				}
				removeRedundancies(constantRel, group);
				return CHANGE_MADE;
			}
			return NO_CHANGES_MADE;
		}
		
		//Otherwise attempt to satisfy from existing relationships
		Set<Concept> values = SnomedUtils.getTargets(c, new Concept[] {type}, charType);
		
		//Remove the less specific values from this list
		if (values.size() > 1) {
			SnomedUtils.removeRedundancies(values);
		}
		
		//Do we have a single value, or should we consider creating an additional grouping?
		boolean additionNeeded = true;
		if (values.size() == 0) {
			return NO_CHANGES_MADE;
		} else if (values.size() == 1) {
			//If we're pulling from stated rels, move any group 0 instances to the new group id
			if (charType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
				//List<Relationship> ungroupedRels = c.getRelationships(charType, type, UNGROUPED); <- Was allowing same rel into mutiple groups
				List<Relationship> ungroupedRels = groups.get(UNGROUPED).getRelationships().stream()
						.filter(r -> r.getType().equals(type))
						.collect(Collectors.toList());
				if (ungroupedRels.size() == 1) {
					Relationship ungroupedRel = ungroupedRels.get(0);
					groups.get(UNGROUPED).removeRelationship(ungroupedRel);
					//Make a clone so we're not immediately affecting the underlying concept
					ungroupedRel = ungroupedRel.clone(ungroupedRel.getId());
					ungroupedRel.setGroupId(group.getGroupId());  //We want to retain the SCTID
					group.addRelationship(ungroupedRel); 
					report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_MODIFIED, "Ungrouped relationship moved to group " + group.getGroupId() + ": " + ungroupedRel);
					return CHANGE_MADE;
				} else if (ungroupedRels.size() > 1) {
					warn ("Consider this case");
				}
			}
			Concept value = values.iterator().next();
			if (value == null) {
				warn ("Should not be null");
			}
			//If this relationship type/value doesn't already exist in this group, stated, add it
			Relationship r = new Relationship (c, type, value, group.getGroupId());
			if (additionNeeded && !group.containsTypeValue(r)) {
				group.addRelationship(r);
				//Remove any attributes already present that are less specific than this one
				removeRedundancies(r, group);
				return CHANGE_MADE;
			} else {
				return NO_CHANGES_MADE;
			}
		} else if (values.size() > 2) {
			//We can only deal with 2
			throw new ValidationFailure (c , "Multiple non-subsuming " + type + " values : " + values.stream().map(v -> v.toString()).collect(Collectors.joining(" and ")));
		} else {
			//Has our group been populated with attributes that we could use to work out which (inferred) group we should pull from?
			return considerAdditionalGrouping(t, template, c, type, groups, groupOfInterest, charType, values, groupOfInterest == UNGROUPED, additionalGroupNums);
		}
	}

	private int considerAdditionalGrouping(Task t, Template template, Concept c, Concept type, List<RelationshipGroup> groups,
			int groupOfInterest, CharacteristicType charType, Set<Concept> values, boolean ungrouped, Map<Integer, Integer> additionalGroupNums) throws TermServerScriptException {
		//Sort the values alphabetically to ensure the right one goes into the right group
		List<Concept> disjointAttributeValues = new ArrayList<>(values);
		disjointAttributeValues.sort(Comparator.comparing(Concept::getFsn));
		
		//If it's a finding site and either attribute is in group 0, we'll leave it there
		//This is specific to diabetes.  When putting in back in, we need to find a way to 
		//retain these relationships in group 0, because if they're not modelled, they'll be removed.
		//Actually, we could just skip them in the first place.
		/*if (type.equals(FINDING_SITE) && (( c.getRelationships(charType, type, disjointAttributeValues.get(0), UNGROUPED, ActiveState.ACTIVE).size() > 0) ||
				c.getRelationships(charType, type, disjointAttributeValues.get(1), UNGROUPED, ActiveState.ACTIVE).size() > 0 )) {
			debug ("Finding site in group 0, not forming 2nd group: " + c );
			return NO_CHANGES_MADE;
		}*/
		
		//Does the template specify one of the values specifically in group 0?  No need to move
		//to group 2 in that case.
		for ( Attribute a : template.getLogicalTemplate().getUngroupedAttributes()) {
			if (a.getValue() != null && a.getType().equals(type.getConceptId())) {
				for (Concept value : values) {
					if (a.getValue().equals(value.getConceptId())) {
						debug ( type + "= " + value + " specified in template, no additional group required." );
						return NO_CHANGES_MADE;
					}
				}
			}
		}
		
		//Is this an attribute that we might want to form a new group around?
		if (formNewGroupAround.contains(type)) {
			//Sort values so they remain with other attributes they're already grouped with
			Concept[] affinitySorted = sortByAffinity(disjointAttributeValues, type, c, groups, ungrouped);
			//Add the attributes to the appropriate group.  We only need to do this for the first
			//pass, since that will assign both values
			int groupId = 0;
			int changesMade = 0;
			for (Concept value : affinitySorted) {
				if (groupId >= groups.size()) {
					warn("Check here!");
				}
				
				if (value != null) {
					if (groups.get(groupId) == null) {
						groups.set(groupId, new RelationshipGroup(groupId));
					}
					
					if (groups.get(groupId).isEmpty()) {
						//Record that we're starting an additional group here
						additionalGroupNums.put(groupId, groupOfInterest);
					}
					
					Relationship r = new Relationship(c,type,value, groupId);
					if (!groups.get(groupId).containsTypeValue(r)) {
						groups.get(groupId).addRelationship(r);
						
						//Can we remove a less specific relationship?
						for (Relationship possibleAncestor : new ArrayList<>(groups.get(groupId).getRelationships())) {
							if (possibleAncestor.getType().equals(r.getType()) && gl.getAncestorsCache().getAncestors(r.getTarget()).contains(possibleAncestor.getTarget())) {
								groups.get(groupId).removeRelationship(possibleAncestor);
							}
						}
						changesMade++;
					}
				}
				if (!ungrouped) {
					//Don't need separate groups when considering ungrouped attributes
					groupId++;
				}
			}
			return changesMade;
		} else { 
			//If we've *already* formed a new group, then we could use it.
			if (!additionalGroupNums.isEmpty()) {
				//TODO We'll need to loop through all the additional groups created for this template
				RelationshipGroup additionalGroup = groups.get(additionalGroupNums.values().iterator().next());
				Concept[] affinitySorted = sortByAffinity(disjointAttributeValues, type, c, groups, ungrouped);
				Relationship r = new Relationship(c,type,affinitySorted[1], 2);
				if (!additionalGroup.containsTypeValue(r)) {
					additionalGroup.addRelationship(r);
					return CHANGE_MADE;
				} else {
					return NO_CHANGES_MADE;
				}
			}
		}
		return NO_CHANGES_MADE;
	}

	/* Sort the two attribute values so that they'll "chum up" with other attributes that they're
	 * already grouped with in the inferred form.
	 */
	private Concept[] sortByAffinity(List<Concept> values, Concept type, Concept c, List<RelationshipGroup> groups, boolean ungrouped) throws TermServerScriptException {
		Concept[] sortedValues = new Concept[groups.size()];
		//Do we already have attributes in group 1 or 2 which should be grouped with one of our values?
		nextValue:
		for (Concept value : values) {
			Relationship proposedRel = new Relationship (type, value);
			
			//In fact, we might already have one of these values stated in a group, in which case the affinity is already set
			//Or a less specific one that we could replace
			for (RelationshipGroup group : groups) {
				if (group!= null && (group.containsTypeValue(proposedRel) || group.containsTypeValue(type, gl.getAncestorsCache().getAncestorsOrSelf(value)))) {
					//If we're considering grouped attributes, and we find one ungrouped, move it up
					if (group.getGroupId() == UNGROUPED && !ungrouped) {
						sortedValues[1] = value;
					} else {
						sortedValues[group.getGroupId()] = value;
					}
					break nextValue; //If we've set one, the other value has no choice where it goes.
				}
			}
			
			for (int groupId = ungrouped ? 0 : 1 ; groupId < groups.size(); groupId++) {
				//Loop through other attributes already set in this stated group, and see if we can find them
				//grouped in the inferred form with our proposed new relationship
				RelationshipGroup group = groups.get(groupId);
				if (group != null) {
					for (Relationship r : group.getRelationships()) {
						//If this rel's type and value exist in multiple groups, it's not a good candiate for determining affinity.  Skip
						if (SnomedUtils.appearsInGroups(c, r, CharacteristicType.INFERRED_RELATIONSHIP).size() > 0) {
							continue;
						}
						
						if (!r.equalsTypeValue(proposedRel) && SnomedUtils.isGroupedWith(r, proposedRel, c, CharacteristicType.INFERRED_RELATIONSHIP)) {
							if (sortedValues[groupId] == null) {
								sortedValues[groupId] = value;
								continue nextValue;
							} else {
								throw new IllegalStateException("In " + c + "inferred, " + r + " is grouped with: \n" + proposedRel + "\n but also \n" + new Relationship (type, sortedValues[groupId - 1]));
							}
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
		for (int i = ungrouped ? 0 : 1; i < sortedValues.length; i++) {
			if (sortedValues[i] == null && iter.hasNext()) {
				sortedValues[i] = iter.next();
			}
		}
		return sortedValues;
	}

	private void removeRedundancies(Relationship r, RelationshipGroup group) throws TermServerScriptException {
		//Do we have other attributes of this type that are less specific than r?
		for (Relationship potentialRedundancy : new ArrayList<>(group.getRelationships())) {
			if (SnomedUtils.isMoreSpecific(r, potentialRedundancy, gl.getAncestorsCache())) {
				group.getRelationships().remove(potentialRedundancy);
			}
		}
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find concepts that only have ungrouped attributes, or none at all.
		List<Concept> processMe = new ArrayList<>();
		
		//Have we already processed concepts in this area?
		Set<Concept> alreadyProcessed = new HashSet<>();
		if (alreadyProcessedFile.exists()) {
			try {
				for (String conceptStr : Files.readLines(alreadyProcessedFile, Charset.forName("utf-8"))) {
					alreadyProcessed.add(gl.getConcept(conceptStr));
				}
			} catch (Exception e) {
				throw new TermServerScriptException("Unable to read already processed concepts from " + alreadyProcessedFile, e);
			}
			info ("Skipping " + alreadyProcessed.size() + " concepts declared as already processed in " + alreadyProcessedFile);
		}
		
		for (Concept c : findConcepts(project.getBranchPath(), subHierarchyECL)) {
			if (!c.getConceptId().equals("187137009")) {
				//continue;
			}
			if (alreadyProcessed.contains(c)) {
				incrementSummaryInformation("Skipped as already processed");
				continue;
			}
			Concept potentialCandidate = null;
			if (!isExcluded(c)) {
				//Actually, at this point in the evolution of this code, 
				//if we have more inferred attributes than stated ones,
				//it's probably worth taking a look at
				int statedAttrbs = SnomedUtils.countAttributes(c, CharacteristicType.STATED_RELATIONSHIP);
				int inferdAttrbs = SnomedUtils.countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP);
				if (inferdAttrbs > statedAttrbs) {
					potentialCandidate = c;
				} else {
					boolean hasGroupedAttributes = false;
					for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
						//If the concept has no grouped attributes we want it, and also 
						//if there are any ungrouped attributes in group 1, or grouped attributes
						//in group 0 (even if group 1 also exists!)
						if (r.getGroupId() == UNGROUPED) {
							if (groupedAttributeTypes.contains(r.getType())) {
								potentialCandidate = c;
								break;
							}
						} else if (ungroupedAttributeTypes.contains(r.getType())) {
							potentialCandidate = c;
							break;
						}
						
						if (r.getGroupId() != UNGROUPED) {
							hasGroupedAttributes = true;
						}
					}
					
					if (!hasGroupedAttributes) {
						potentialCandidate = c;
					}
				}
			}
			
			if (potentialCandidate != null) {
				//just check we don't match any of the other templates in the stated form
				//Eg having two ungrouped finding sites for complications of diabetes
				boolean isFirst = true;
				boolean matchesSubsequentTemplate = false;
				for (Template template : templates) {
					if (isFirst) {
						isFirst = false;
					} else {
						if (TemplateUtils.matchesTemplate(potentialCandidate, template, 
								gl.getDescendantsCache(), 
								CharacteristicType.STATED_RELATIONSHIP, 
								true //Do allow additional unspecified ungrouped attributes
								)) {
							matchesSubsequentTemplate = true;
							break;
						}
					}
				}
				if (!matchesSubsequentTemplate) {
					processMe.add(potentialCandidate);
				}
			}
		}
		processMe.sort(Comparator.comparing(Concept::getFsn));
		List<Component> firstPassComplete = firstPassRemodel(processMe);
		return firstPassComplete;
	}
	
	protected boolean isExcluded(Concept c) {
		if (skipMultipleUngroupedFindingSites) {
			if (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, FINDING_SITE, UNGROUPED).size() > 1) {
				warn("Excluding due to multiple ungrouped finding sites: " + c);
				return true;
			}
		}
		return super.isExcluded(c);
	}

	private List<Component> firstPassRemodel(List<Concept> processMe) throws TermServerScriptException {
		setQuiet(true);
		List<Component> firstPassComplete = new ArrayList<>();
		List<ValidationFailure> failures = new ArrayList<>();
		Set<Concept> noChangesMade = new HashSet<>();
		for (Concept c : processMe) {
			try {
				Concept cClone = c.cloneWithIds();
				int changesMade = remodelConcept(null, cClone, templates.get(0));
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
			report ((Task)null, unchanged, Severity.NONE, ReportActionType.NO_CHANGE, "");
		}
		info("First pass attempt at remodel complete, " + firstPassComplete.size() + " concepts identified to change from an initial " + processMe.size() + ". " + noChangesMade.size() + " had no changes.");
		return firstPassComplete;
	}

}
