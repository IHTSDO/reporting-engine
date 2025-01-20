package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * INFRA-2133
 * Class to replace relationships with alternatives
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelCongenitalAbnormality extends DeltaGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(ModelCongenitalAbnormality.class);

	String subHierarchyStr = "276654001"; // | Congenital malformation (disorder) |
	Concept findingSite;
	Concept occurrence;
	Concept pathologicalProcess;
	Concept associatedMorphology;
	
	List<RelationshipTemplate> findRelationshipsForReplace = new ArrayList<>();
	List<RelationshipTemplate> replacements = new ArrayList<>();
	
	List<RelationshipTemplate> findRelationshipsForAdd = new ArrayList<>();
	List<RelationshipTemplate> addRelationships = new ArrayList<>();


	public static void main(String[] args) throws TermServerScriptException {
		ModelCongenitalAbnormality delta = new ModelCongenitalAbnormality();
		try {
			delta.runStandAlone = true;
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(true);  //Just FSN, not working with all descriptions here
			//We won't include the project export in our timings
			delta.prep(); //Further initiation once hierarchy is available
			delta.startTimer();
			delta.process();
			delta.flushFiles(false); //Need to flush files before zipping
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
		}
	}

	@Override
	protected void init (String[] args) throws TermServerScriptException {
		super.init(args);
		
		findRelationshipsForReplace.add(createTemplate("116676008", "107656002")); //|Associated morphology (attribute)| -> |Congenital anomaly (morphologic abnormality)| 
		findRelationshipsForReplace.add(createTemplate("116676008", "21390004")); //|Associated morphology (attribute)| -> |Developmental anomaly (morphologic abnormality)
		
		//Note that this is not a 1 for 1 replacement.  Either of the relationships above will be 
		//replaced by BOTH relationships below.
		replacements.add(createTemplate("116676008", "49755003")); //|Associated morphology (attribute)| ->  |Morphologically abnormal structure (morphologic abnormality)|
		replacements.add(createTemplate("370135005","308490002")); //  |Pathological process (attribute)| -> |Pathological developmental process (qualifier value)|
		
		//these should be grouped with the finding site and the occurrence.
		//Where multiple finding sites exist, multiple role groups should be created
		//Occurrence should be restated from ancestor.
		//Every concept under congenital malformation, should have the pathological process attribute.
	}
	
	protected void prep() throws TermServerScriptException {
		
		findingSite = gl.getConcept("363698007"); // |Finding site (attribute)|
		occurrence = gl.getConcept("246454002");  // |Occurrence (attribute)|
		pathologicalProcess = gl.getConcept("370135005");  // |Pathological Process (attribute)|
		associatedMorphology = gl.getConcept("116676008");  //|Associated morphology (attribute)|
		
		addRelationships.add(createTemplate("370135005","308490002")); //  |Pathological process (attribute)| -> |Pathological developmental process (qualifier value)|
		
		LOGGER.info("Finding relationships to add...");
		Concept valueSubHierarchy = gl.getConcept("21390004"); //|Developmental anomaly (morphologic abnormality)
		Set<Concept> valuesToMatch = valueSubHierarchy.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);
		for (Concept thisValue : valuesToMatch) {
			findRelationshipsForAdd.add(createTemplate("116676008", thisValue.getConceptId()));  //Associated Morphology -> subtype of developmental anomaly
		}
	}

	@Override
	protected void process() throws TermServerScriptException {
		LOGGER.info("Processing...");
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept> concepts = subHierarchy.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);
		concepts.add(subHierarchy);  //Descendants and Self
		addSummaryInformation("Concepts considered", concepts.size());
		for (Concept concept : concepts) {
			//If the concept has no modelling, or no we'll skip it.
			if (concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, associatedMorphology, ActiveState.ACTIVE).isEmpty()) {
				String msg = "Concept has no stated associated morphology, skipping";
				report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.NO_CHANGE, msg);
			} else {
				int changesMade = 0;
				int firstFreeGroup = SnomedUtils.getFirstFreeGroup(concept);
				//If we move the finding site, also try to mvoe the occurrence
				changesMade += moveGroup0Attribute(concept, findingSite, firstFreeGroup);
				if (changesMade > 0) {
					changesMade += moveGroup0Attribute(concept, occurrence, firstFreeGroup);
				}
				changesMade += checkOccurrences (concept);
				changesMade += processRelationships(concept, findRelationshipsForReplace, true);
				changesMade += processRelationships(concept, findRelationshipsForAdd, false);
				changesMade += checkFindingSite(concept);
				
				if (changesMade > 0) {
					concept.setModified();
					incrementSummaryInformation("Concepts modified");
					outputRF2(concept);  //Will only output dirty fields.
				}
			}
		}
	}

	private int moveGroup0Attribute(Concept concept, Concept type, int targetGroup) throws TermServerScriptException {
		int changesMade = 0;
		//Now find our attributes of interest
		Set<Relationship> matchingGroup0Attribs = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, ActiveState.ACTIVE);

		//Move any group 0 finding site into its own group where it will pick up 
		//occurrence and pathological process
		for (Relationship r : matchingGroup0Attribs) {
			if (r.getGroupId() == 0) {
				Relationship movedRel = r.clone(relIdGenerator.getSCTID());
				movedRel.setGroupId(targetGroup);
				concept.addRelationship(movedRel);
				r.setActive(false);
				String msg = "Recreated group 0 attribute of type " + type + " in group " + movedRel.getGroupId();
				report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, msg);
				changesMade++;
			}
		}
		return changesMade;
	}

	private int checkFindingSite(Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		//Check each finding site has a pathological process, add one if not
		Set<Relationship>findingSites = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, findingSite, ActiveState.ACTIVE);
		for (Relationship f : findingSites) {
			if (!existsInGroup(concept, pathologicalProcess, f.getGroupId())) {
				for (RelationshipTemplate addMe : addRelationships) {
					Relationship r = addMe.createRelationship(concept, f.getGroupId(), relIdGenerator.getSCTID());
					concept.addRelationship(r);
					String msg = "Ensuring finding site grouped with " + r;
					report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, msg);
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private int processRelationships(Concept concept, List<RelationshipTemplate> findRelationships, boolean replace) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		
		//Work through the relationships looking for matches
		Set<Relationship> matchedRelationships = new HashSet<>();
		Set<Integer>groupsAffected = new HashSet<>();
		for (Relationship rel : rels) {
			for (RelationshipTemplate findRel : findRelationships) {
				if (findRel.equalsTypeAndTargetValue(rel)) {
					if (groupsAffected.contains(rel.getGroupId())) {
						String msg = "Concept has two matching relationships in same group: " + rel.getGroupId();
						report(concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
						return changesMade;
					} else {
						matchedRelationships.add(rel);
					}
				}
			}
		}
		
		//Now make additions or replacements to the matching relationships as required
		for (Relationship thisMatch : matchedRelationships) {
			if (thisMatch.getGroupId() == 0) {
				String msg = "Relationship matched is in group 0: " + thisMatch;
				report(concept, concept.getFSNDescription(), Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, msg);
				//TODO If this comes up, move the relationships into a free group
				//and remove "else"
			} else {
				if (replace) {
					changesMade += replaceRelationship (thisMatch);
				} else {
					changesMade += addRelationship(thisMatch);
				}
			}
		}
		return changesMade;
	}

	/**
	 * @return true if a relationship with the specified type exists in the specified group
	 */
	private boolean existsInGroup(Concept c, Concept type, long groupId) {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, ActiveState.ACTIVE)) {
			if (r.getGroupId() == groupId) {
				return true;
			}
		}
		return false;
	}

	private int checkOccurrences(Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		//Make sure we have all the occurrences stated by all parents
		Set<Concept> existingOccurrenceTargets = getRelationshipTargets(concept, occurrence );
		Set<Concept> allParentOccurrenceTargets = new HashSet<>();
		for (Concept parent : concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			allParentOccurrenceTargets.addAll(getRelationshipTargets(parent, occurrence ));
		}
		
		//Which occurrences are we missing?
		allParentOccurrenceTargets.removeAll(existingOccurrenceTargets);
		if (allParentOccurrenceTargets.size() > 1) {
			LOGGER.warn (concept + " is gaining " + allParentOccurrenceTargets.size() + " occurrances");
		}
		
		for (Concept requiredOccurrenceTarget : allParentOccurrenceTargets) {
			//We need an occurrence for each active group
			for (Integer groupId : SnomedUtils.getActiveGroups(concept)) {
				Relationship addMe = new Relationship(concept, occurrence, requiredOccurrenceTarget, groupId);
				addMe.setRelationshipId(relIdGenerator.getSCTID());
				addMe.setDirty();
				concept.addRelationship(addMe);
				report(concept, concept.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, addMe.toString());
				changesMade++;
			}
		}
		return changesMade;
	}

	private Set<Concept> getRelationshipTargets(Concept c, Concept type) {
		Set<Concept> targets = new HashSet<>();
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, ActiveState.ACTIVE)) {
			targets.add(r.getTarget());
		}
		return targets;
	}

	private int replaceRelationship(Relationship replaceMe) throws TermServerScriptException {
		int changesMade = 0;
		replaceMe.setActive(false);
		Concept source = replaceMe.getSource();
		boolean firstReplacement = true;
		for (RelationshipTemplate replacementTemplate : replacements) {
			if (!checkForExistingRelationship(source, replacementTemplate, replaceMe.getGroupId())) {
				Relationship replacement = replacementTemplate.toRelationship(replaceMe, relIdGenerator.getSCTID());
				replaceMe.setActive(false);
				source.addRelationship(replacement);
				changesMade++;
				String msg = (firstReplacement ?"Replaced " + replaceMe :"Also") + " with " + replacement;
				report(source, source.getFSNDescription(), Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg);
				firstReplacement = false;
			}
		}
		return changesMade;
	}
	
	private int addRelationship(Relationship addToMe) throws TermServerScriptException {
		int changesMade = 0;
		Concept source = addToMe.getSource();
		for (RelationshipTemplate addTemplate : addRelationships) {
			if (!checkForExistingRelationship(source, addTemplate, addToMe.getGroupId())) {
				Relationship addition = addTemplate.toRelationship(addToMe,relIdGenerator.getSCTID());
				source.addRelationship(addition);
				changesMade++;
				String msg = "Added " + addition + " due to presence of " + addToMe;
				report(source, source.getFSNDescription(), Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, msg);
			}
		}
		return changesMade;
	}

	private boolean checkForExistingRelationship(Concept c,
			RelationshipTemplate rt, long group) throws TermServerScriptException {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			if (r.getGroupId() == group && rt.equalsTypeAndTargetValue(r)) {
				report(c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Replacement relationship already exists as " + r);
				return true;
			}
		}
		return false;
	}

	private RelationshipTemplate createTemplate(String typeStr, String targetStr) throws TermServerScriptException {
		Concept type = gl.getConcept(typeStr);
		Concept target = gl.getConcept(targetStr);
		CharacteristicType ct = CharacteristicType.STATED_RELATIONSHIP;
		return new RelationshipTemplate(type, target, ct);
	}
	
}
