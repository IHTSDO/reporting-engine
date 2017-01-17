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

	List<RelationshipTemplate> findRelationships = new ArrayList<RelationshipTemplate>();
	List<RelationshipTemplate> replaceRelationships = new ArrayList<RelationshipTemplate>();
	GraphLoader gl = GraphLoader.getGraphLoader();

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ReplaceRelationships delta = new ReplaceRelationships();
		try {
			delta.useAuthenticatedCookie = true;  //TRUE for dev & uat
			delta.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			delta.loadProjectSnapshot(true);  //Just FSN, not working with all descriptions here
			//We won't include the project export in our timings
			delta.startTimer();
			delta.process();
			SnomedUtils.createArchive(new File(delta.outputDirName));
		} finally {
			delta.finish();
			if (delta.idGenerator != null) {
				println(delta.idGenerator.finish());
			}
		}
	}
	
	private void process() throws TermServerScriptException {
		
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept> concepts = subHierarchy.getDescendents(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
		addSummaryInformation("Concepts considered", concepts.size());
		for (Concept concept : concepts) {
			replaceRelationships(concept);
		}
	}

	private void replaceRelationships(Concept concept) throws TermServerScriptException {
		List<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE);
		//Work through the relationships looking for just one to replace
		List<Relationship> replaceAll = new ArrayList<Relationship>();
		Set<Integer>groupsAffected = new HashSet<Integer>();
		for (Relationship rel : rels) {
			for (RelationshipTemplate findRel : findRelationships) {
				if (findRel.matches(rel)) {
					if (groupsAffected.contains(rel.getGroupId())) {
						String msg = "Concept has two matching relationships in same group: " + rel.getGroupId();
						report (concept, concept.getFSNDescription(), SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
						return;
					} else {
						replaceAll.add(rel);
					}
				}
			}
		}
		
		for (Relationship replaceMe : replaceAll) {
			if (replaceMe.getGroupId() == 0) {
				String msg = "Relationship to replace is in group 0: " + replaceMe;
				report (concept, concept.getFSNDescription(), SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
			} else {
				if (replaceMe.getEffectiveTime().isEmpty()) {
					String msg = "Relationship already showing as modified: " + replaceMe;
					report (concept, concept.getFSNDescription(), SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, msg);
				} else {
					replaceRelationship (replaceMe);
				}
			}
		}
		
		
		if (replaceAll.size() > 0) {
			outputRF2(concept);
			incrementSummaryInformation("Concepts modified", 1);
		}
	}

	private void replaceRelationship(Relationship replaceMe) throws TermServerScriptException {

		replaceMe.setActive(false);
		Concept source = replaceMe.getSource();
		for (RelationshipTemplate replacementTemplate : replaceRelationships) {
			if (!checkForExistingRelationship(source, replacementTemplate, replaceMe.getGroupId())) {
				Relationship replacement = replaceMe.clone();
				replacement.setRelationshipId(idGenerator.getSCTID(PartionIdentifier.RELATIONSHIP));
				replaceMe.setActive(false);
				replacement.setActive(true);
				replacement.setType(replacementTemplate.getType());
				replacement.setTarget(replacementTemplate.getTarget());
				source.addRelationship(replacement);
				String msg = "Replaced " + replaceMe + " with " + replacement;
				report (source, source.getFSNDescription(), SEVERITY.MEDIUM, REPORT_ACTION_TYPE.RELATIONSHIP_ADDED, msg);
			}
		}
	}

	private boolean checkForExistingRelationship(Concept c,
			RelationshipTemplate rt, long group) {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
			if (r.getGroupId() == group && rt.matches(r)) {
				report (c, c.getFSNDescription(), SEVERITY.HIGH, REPORT_ACTION_TYPE.VALIDATION_ERROR, "Replacement relationship already exists as " + r);
				return true;
			}
		}
		return false;
	}

	protected void init (String[] args) throws IOException, TermServerScriptException {
		super.init(args);
		
		findRelationships.add(createTemplate("116676008", "107656002")); //|Associated morphology (attribute)| -> |Congenital anomaly (morphologic abnormality)| 
		findRelationships.add(createTemplate("116676008", "21390004")); //|Associated morphology (attribute)| -> |Developmental anomaly (morphologic abnormality)
		
		//Note that this is not a 1 for 1 replacement.  Either of the relationships above will be 
		//replaced by BOTH relationships below.
		replaceRelationships.add(createTemplate("116676008", "49755003")); //|Associated morphology (attribute)| ->  |Morphologically abnormal structure (morphologic abnormality)|
		replaceRelationships.add(createTemplate("370135005","308490002")); //  |Pathological process (attribute)| -> |Pathological developmental process (qualifier value)|
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
