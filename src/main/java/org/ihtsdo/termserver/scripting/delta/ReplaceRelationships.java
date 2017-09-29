package org.ihtsdo.termserver.scripting.delta;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * Class to replace relationships with alternatives
 */
public class ReplaceRelationships extends DeltaGenerator {
	
	String subHierarchyStr = "276654001"; // | Congenital malformation (disorder) |

	List<RelationshipTemplate> findRelationshipsForReplace = new ArrayList<RelationshipTemplate>();
	List<RelationshipTemplate> replaceRelationships = new ArrayList<RelationshipTemplate>();
	
	List<RelationshipTemplate> findRelationshipsForAdd = new ArrayList<RelationshipTemplate>();
	List<RelationshipTemplate> addRelationships = new ArrayList<RelationshipTemplate>();

	GraphLoader gl = GraphLoader.getGraphLoader();

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ReplaceRelationships delta = new ReplaceRelationships();
		try {
			delta.useAuthenticatedCookie = true;  //TRUE for dev & uat
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(true);  //Just FSN, not working with all descriptions here
			//We won't include the project export in our timings
			delta.prep(); //Further initiation once hierarchy is available
			delta.startTimer();
			delta.process();
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.descIdGenerator != null) {
				println(delta.descIdGenerator.finish());
			}
		}
	}

	protected void init (String[] args) throws IOException, TermServerScriptException, SnowOwlClientException, SnowOwlClientException {
		super.init(args);
		
		findRelationshipsForReplace.add(createTemplate("116676008", "107656002")); //|Associated morphology (attribute)| -> |Congenital anomaly (morphologic abnormality)| 
		findRelationshipsForReplace.add(createTemplate("116676008", "21390004")); //|Associated morphology (attribute)| -> |Developmental anomaly (morphologic abnormality)
		
		//Note that this is not a 1 for 1 replacement.  Either of the relationships above will be 
		//replaced by BOTH relationships below.
		replaceRelationships.add(createTemplate("116676008", "49755003")); //|Associated morphology (attribute)| ->  |Morphologically abnormal structure (morphologic abnormality)|
		replaceRelationships.add(createTemplate("370135005","308490002")); //  |Pathological process (attribute)| -> |Pathological developmental process (qualifier value)|
		
		//should be grouped with the finding site and the occurrence.
		
		//Every concept under congenital malformation, should have the pathological process attribute.
	}
	
	protected void prep() throws TermServerScriptException {
		println("Finding relationships to add...");
		addRelationships.add(createTemplate("370135005","308490002")); //  |Pathological process (attribute)| -> |Pathological developmental process (qualifier value)|
		Concept valueSubHierarchy = gl.getConcept("21390004"); //|Developmental anomaly (morphologic abnormality)
		Set<Concept> valuesToMatch = valueSubHierarchy.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		for (Concept thisValue : valuesToMatch) {
			findRelationshipsForAdd.add(createTemplate("116676008", thisValue.getConceptId()));  //Associated Morphology -> subtype of developmental anomaly
		}
	}
	
	private void process() throws TermServerScriptException {
		println("Processing...");
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept> concepts = subHierarchy.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		concepts.add(subHierarchy);  //Descendants and Self
		addSummaryInformation("Concepts considered", concepts.size());
		for (Concept concept : concepts) {
			processRelationships(concept, findRelationshipsForReplace, true);
			processRelationships(concept, findRelationshipsForAdd, false);
			if (concept.isModified()) {
				incrementSummaryInformation("Concepts modified", 1);
				outputRF2(concept);  //Will only ouput dirty fields.
			}
		}
	}

	private void processRelationships(Concept concept, List<RelationshipTemplate> findRelationships, boolean replace) throws TermServerScriptException {
		List<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		//Work through the relationships looking for matches
		List<Relationship> matchedRelationships = new ArrayList<Relationship>();
		Set<Integer>groupsAffected = new HashSet<Integer>();
		for (Relationship rel : rels) {
			for (RelationshipTemplate findRel : findRelationships) {
				if (findRel.matches(rel)) {
					if (groupsAffected.contains(rel.getGroupId())) {
						String msg = "Concept has two matching relationships in same group: " + rel.getGroupId();
						report (concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
						return;
					} else {
						matchedRelationships.add(rel);
					}
				}
			}
		}
		
		for (Relationship thisMatch : matchedRelationships) {
			if (thisMatch.getGroupId() == 0) {
				String msg = "Relationship matched is in group 0: " + thisMatch;
				report (concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
			} else {
				if (thisMatch.getEffectiveTime().isEmpty()) {
					String msg = "Matched relationship already showing as modified: " + thisMatch;
					report (concept, concept.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
				} else {
					if (replace) {
						replaceRelationship (thisMatch);
					} else {
						addRelationship(thisMatch);
					}
				}
			}
		}
		
		if (matchedRelationships.size() > 0) {
			concept.setModified();
		}
	}

	private void replaceRelationship(Relationship replaceMe) throws TermServerScriptException {

		replaceMe.setActive(false);
		Concept source = replaceMe.getSource();
		for (RelationshipTemplate replacementTemplate : replaceRelationships) {
			if (!checkForExistingRelationship(source, replacementTemplate, replaceMe.getGroupId())) {
				Relationship replacement = replaceMe.clone(relIdGenerator.getSCTID());
				replaceMe.setActive(false);
				replacement.setActive(true);
				replacement.setType(replacementTemplate.getType());
				replacement.setTarget(replacementTemplate.getTarget());
				source.addRelationship(replacement);
				String msg = "Replaced " + replaceMe + " with " + replacement;
				report (source, source.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, msg);
			}
		}
	}
	
	private void addRelationship(Relationship addToMe) throws TermServerScriptException {

		Concept source = addToMe.getSource();
		for (RelationshipTemplate addTemplate : addRelationships) {
			if (!checkForExistingRelationship(source, addTemplate, addToMe.getGroupId())) {
				Relationship addition = addToMe.clone(relIdGenerator.getSCTID());
				addition.setActive(true);
				addition.setType(addTemplate.getType());
				addition.setTarget(addTemplate.getTarget());
				source.addRelationship(addition);
				String msg = "Added " + addition + " due to presence of " + addToMe;
				report (source, source.getFSNDescription(), Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, msg);
			}
		}
	}

	private boolean checkForExistingRelationship(Concept c,
			RelationshipTemplate rt, long group) {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			if (r.getGroupId() == group && rt.matches(r)) {
				report (c, c.getFSNDescription(), Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Replacement relationship already exists as " + r);
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

	@Override
	protected Concept loadLine(String[] lineItems)
			throws TermServerScriptException {
		return null;
	}

}
