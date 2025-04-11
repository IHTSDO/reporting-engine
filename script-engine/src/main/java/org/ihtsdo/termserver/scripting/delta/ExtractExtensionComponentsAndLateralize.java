package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ExtractExtensionComponentsAndLateralize extends ExtractExtensionComponents {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExtractExtensionComponents.class);

	private static Set<Concept> bodyStructures = null;

	protected void doAdditionalProcessing(Concept c, List<Component> componentsToProcess) throws TermServerScriptException {
		//Now it might be that the source extension already has this concept lateralized
		//eg 847081000000101 |Balloon dilatation of bronchus using fluoroscopic guidance (procedure)|
		LOGGER.info("Creating lateralized concepts for {}", c);
		try {
			extractOrCreateLateralizedConcept(c, LEFT, componentsToProcess);
			extractOrCreateLateralizedConcept(c, RIGHT, componentsToProcess);
			extractOrCreateLateralizedConcept(c, BILATERAL, componentsToProcess);
		} catch (TermServerScriptException e) {
			report(c,  Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to create lateralized concepts for " + c + " due to: " + e.getMessage());
		}
	}

	private void extractOrCreateLateralizedConcept(Concept c, Concept laterality, List<Component> componentsToProcess) throws TermServerScriptException {
		String lateralityStr = laterality.getPreferredSynonym().toLowerCase();
		Concept existingLateralizedConcept;
		if (laterality.equals(BILATERAL)) {
			existingLateralizedConcept = findBilateralDescendent(c);
		} else {
			existingLateralizedConcept = findLateralizedCounterpart(c, laterality, false);
		}

		if (existingLateralizedConcept == null) {
			Concept newLateralizedConcept = createLateralizedConcept(c, laterality);
			gl.registerConcept(newLateralizedConcept);
			LOGGER.info("Lateralized concept created: {}", newLateralizedConcept);
		} else {
			//If this concept is already on our list to process, we don't need to take any action.
			if (componentsToProcess.contains(existingLateralizedConcept)) {
				report(c, Severity.LOW, ReportActionType.INFO, "Lateralized concept already scheduled for processed " + existingLateralizedConcept);
				return;
			}
			report(c, Severity.MEDIUM, ReportActionType.INFO, "Lateralized concept already exists for " + c + " with laterality " + laterality + " as " + existingLateralizedConcept + ", adding to list to process");
			//Indicate that we don't need to lateralize this concept any further! doAdditionalProcessing = false
			extractComponent(existingLateralizedConcept, componentsToProcess, false);
		}
	}

	private Concept createLateralizedConcept(Concept c, Concept laterality) throws TermServerScriptException {
		Concept clone = c.cloneAsNewConcept(conIdGenerator.getSCTID());
		//Lateralize the concepts in the role groups
		Collection<RelationshipGroup> originalGroups = clone.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);
		for (RelationshipGroup g : originalGroups) {
			if (laterality.equals(LEFT) || laterality.equals(BILATERAL)) {
				lateralizeGroup(g, LEFT, false);
			}
			if (laterality.equals(RIGHT)) {
				lateralizeGroup(g, RIGHT, false);
			}
			if (laterality.equals(BILATERAL)) {
				RelationshipGroup gClone = g.clone(SnomedUtils.getFirstFreeGroup(clone));
				lateralizeGroup(gClone, RIGHT, true);
				clone.addRelationshipGroup(gClone, null);
			}
		}
		lateralizeFsn(c, clone, laterality);
		clone.setDirty();
		SnomedUtils.getAllComponents(clone).forEach(concept -> concept.setDirty());

		report(clone, Severity.LOW, ReportActionType.CONCEPT_ADDED, SnomedUtils.getDescriptions(clone), clone.toExpression(CharacteristicType.STATED_RELATIONSHIP));
		return clone;
	}

	private void lateralizeGroup(RelationshipGroup g, Concept laterality, boolean overrideCurrentLaterality) throws TermServerScriptException {
		for (Relationship r : g.getRelationships()) {
			if (isBodyStructure(r.getTarget())) {
				Concept lateralizedBodyStructure = findLateralizedCounterpart(r.getTarget(), laterality, overrideCurrentLaterality);
				if (lateralizedBodyStructure == null) {
					//throw new TermServerScriptException("Failed to find lateralized counterpart for " + r.getTarget() + " with laterality " + laterality);
					report(r.getSource(), Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Failed to find lateralized counterpart for " + r.getTarget() + " with laterality " + laterality + " - continuing with unlateralized concept.");
				} else {
					r.setTarget(lateralizedBodyStructure);
				}
			}
		}
	}

	private void lateralizeFsn(Concept original, Concept clone, Concept laterality) throws TermServerScriptException {
		String lateralityStr = laterality.getPreferredSynonym().toLowerCase();
		boolean successfulLaterlization = false;
		//Can we find the cut point for the specified body structure in the fsn?
		for (String bodyStructureStr : getBodyStructureStrs(original)) {
			if (clone.getFsn().contains(bodyStructureStr)) {
				String laterlizedFsn;
				//If it's an X of Y, then we'll do X of <left|right> Y
				if (bodyStructureStr.contains(" of ")) {
					String lateralizedBodyStructureStr = bodyStructureStr.replace(" of ", " of " + lateralityStr + " ");
					laterlizedFsn = clone.getFsn().replace(bodyStructureStr, lateralizedBodyStructureStr);
				} else {
					laterlizedFsn = clone.getFsn().replace(bodyStructureStr, lateralityStr + " " + bodyStructureStr);
				}

				clone.getFSNDescription().setTerm(laterlizedFsn);
				clone.setFsn(laterlizedFsn);
				successfulLaterlization = true;
				break;
			}
		}

		if (!successfulLaterlization) {
			//We'll add the lateralized body structure to the end of the FSN
			//Or, if it's "using", before "using"
			String[] fsnParts = SnomedUtils.deconstructFSN(clone.getFsn());
			String lateralizedBodyStructurePT = getBodyStructurePT(clone);

			if (lateralizedBodyStructurePT == null) {
				throw new TermServerScriptException("Failed to find lateralized body structure for " + clone);
			}

			if (laterality.equals(BILATERAL) || !lateralizedBodyStructurePT.contains("right")) {
				lateralizedBodyStructurePT = lateralizedBodyStructurePT.replace("left", "right and left");
			}

			//If the body structure was not able to be lateralized, we'll need to force the laterality for the FSN
			if (!lateralizedBodyStructurePT.contains(lateralityStr)) {
				lateralizedBodyStructurePT += " (" + lateralityStr + ")";
			}
			String laterlizedFsn = fsnParts[0] + " of " + lateralizedBodyStructurePT + " " + fsnParts[1];
			if (fsnParts[0].contains(" using ")) {
				int cutPoint = fsnParts[0].indexOf(" using ");
				laterlizedFsn = fsnParts[0].substring(0, cutPoint) + " of " + lateralizedBodyStructurePT + fsnParts[0].substring(cutPoint) + " " + fsnParts[1];
			}
			clone.getFSNDescription().setTerm(laterlizedFsn);
			clone.setFsn(laterlizedFsn);
		}
		
		/*if (!successfulLaterlization) {
			String allBodyStructures = getBodyStructureStrs(original).stream().collect(Collectors.joining(", "));
			if (StringUtils.isEmpty(allBodyStructures)) {
				allBodyStructures = "None found";
			}
			throw new TermServerScriptException("Failed to lateralize FSN for " + original + " with laterality " + laterality + " and body structure(s): " + allBodyStructures);
		}*/
        normalizeDescriptions(clone, laterality);
	}

    private void normalizeDescriptions(Concept clone, Concept laterality) throws TermServerScriptException {
        //remove all non-FSN descriptions and add them back in based on the FSN
		clone.getDescriptions(ActiveState.ACTIVE).stream()
				.filter(d -> !d.getType().equals(DescriptionType.FSN))
				.forEach(d -> clone.removeDescription(d));
		String pt = SnomedUtils.deconstructFSN(clone.getFSNDescription().getTerm())[0];
		if (laterality.equals(BILATERAL)) {
			pt = pt.replace("right and left", "bilateral");
		}
		Description ptDesc = Description.withDefaults(pt, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		clone.addDescription(ptDesc);

		//Bilateral concepts will have an acceptable "both" synonym
		if (laterality.equals(BILATERAL)) {
			String bothTerm = pt.replace("bilateral", "both");
			//if (!bothTerm.endsWith("structure")) {
			if (!bothTerm.endsWith(")")) {
				if (bothTerm.contains(" with ")) {
					bothTerm = bothTerm.replace(" with ", "s with ");
				} else if (bothTerm.contains(" using ")) {
					bothTerm = bothTerm.replace(" using ", "s using ");
				} else {
					bothTerm += "s";
				}
			}
			Description bothDesc = Description.withDefaults(bothTerm, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
			clone.addDescription(bothDesc);
		}

		//Let's give these descriptions some identifiers!
		clone.getDescriptions(ActiveState.ACTIVE).stream()
				.forEach(d -> {
                    try {
                        d.setId(descIdGenerator.getSCTID());
						d.setConceptId(clone.getId());
						d.getLangRefsetEntries().forEach(l -> l.setReferencedComponentId(d.getId()));
                    } catch (TermServerScriptException e) {
                        throw new RuntimeException(e);
                    }
                });
    }

    private Set<String> getBodyStructureStrs(Concept original) throws TermServerScriptException {
		Set<String> bodyStructureStrs = new LinkedHashSet<>();  //Preserve insertion order

		if (original.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP).size() == 0) {
			String msg = "No stated relationship groups found for " + original;
			if (!copyInferredRelationshipsToStatedWhereMissing) {
				msg += " do you need to set copyInferredRelationshipsToStatedWhereMissing to true? in parent class?";
			}
			throw new TermServerScriptException(msg);
		}

		for (RelationshipGroup g : original.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			for (Relationship r : g.getRelationships()) {
				if (isBodyStructure(r.getTarget())) {
					for (Description d : r.getTarget().getDescriptions(ActiveState.ACTIVE, List.of(DescriptionType.SYNONYM))) {
						bodyStructureStrs.add(d.getTerm().toLowerCase());
					}
					String pt = r.getTarget().getPreferredSynonym().toLowerCase();
					String origPT = pt;
					bodyStructureStrs.add(pt);

					if (pt.contains("structure")) {
						String ptWithoutStructure = pt.replace("structure", "").replace("  ", " ").trim();
						bodyStructureStrs.add(ptWithoutStructure);
						if (ptWithoutStructure.contains(" of ")) {
							String ptWithoutStructureOf = ptWithoutStructure.replace(" of ", " ").replace("  ", " ").trim();
							bodyStructureStrs.add(ptWithoutStructureOf);
						}
					}

					//Also for the case X structure of Y, we'll try just "of Y"
					if (origPT.contains("structure of ")) {
						String[] parts = pt.split("structure");
						bodyStructureStrs.add(parts[1]);
					}

					if (origPT.contains("structure of joint of ")) {
						String[] parts = pt.split("structure of joint ");
						bodyStructureStrs.add(parts[1]);
					}
				}
			}
		}
		return bodyStructureStrs;
	}

	private String getBodyStructurePT(Concept c) throws TermServerScriptException {
		Set<String> bodyStructureStrs = new TreeSet<>();
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			for (Relationship r : g.getRelationships()) {
				if (isBodyStructure(r.getTarget())) {
					String pt = r.getTarget().getPreferredSynonym().toLowerCase();
					return pt;
				}
			}
		}
		return null;
	}


	private Concept findLateralizedCounterpart(Concept unlaterlizedConcept, Concept laterality, boolean overrideCurrentLaterality) throws TermServerScriptException {
		//Ideally our concept would be "Structure of X" and we'd look for "Structure of left X"
		//So try that first, and if not, we'll try the laterality in every place.
		Concept lateralizedCounterpart = null;
		String lateralityStr = laterality.getPreferredSynonym().toLowerCase();
		String lateralityStrCapitalized = laterality.getPreferredSynonym();
		String unlateralizedFsn = unlaterlizedConcept.getFsn();

		if (overrideCurrentLaterality) {
			String lateralizedFsn = unlateralizedFsn.replace("left", lateralityStr)
					.replace("Left", lateralityStrCapitalized)
					.replace("right", lateralityStr)
					.replace("Right", lateralityStrCapitalized);
			lateralizedCounterpart = findBodyStructureWithFsn(lateralizedFsn);
		}

		if (lateralizedCounterpart == null && isBodyStructure(unlaterlizedConcept) && unlateralizedFsn.startsWith("Structure of ")) {
			String lateralizedFsn = unlateralizedFsn.replace("Structure of ", "Structure of " + lateralityStr + " ");
			lateralizedCounterpart = findBodyStructureWithFsn(lateralizedFsn);
		}

		if (lateralizedCounterpart == null) {
			lateralizedCounterpart = findLateralizedCounterpartExhaustive(unlaterlizedConcept, lateralityStr);
		}
		return lateralizedCounterpart;
	}

	private Concept findLateralizedCounterpartExhaustive(Concept unlaterlizedConcept, String lateralityStr) throws TermServerScriptException {
		//Try the laterality in every place
		String unlateralizedFsn = unlaterlizedConcept.getFsn();
		Concept lateralizedBodyStructure = null;
		String[] fsnParts = unlateralizedFsn.split(" ");

		//Miss out the last two parts - not going to lateralize the semantic tag!
		for (int position = 0; position < fsnParts.length -2; position++) {
			String[] fsnPartsCopy = fsnParts.clone();
			if (position == 0) {
				fsnPartsCopy[position] = StringUtils.capitalizeFirstLetter(lateralityStr) + " " + StringUtils.decapitalizeFirstLetter(fsnPartsCopy[position]);
			} else {
				fsnPartsCopy[position] = lateralityStr + " " + fsnPartsCopy[position];
			}
			String lateralizedFsn = String.join(" ", fsnPartsCopy);
			lateralizedBodyStructure = findBodyStructureWithFsn(lateralizedFsn);
			if (lateralizedBodyStructure != null) {
				return lateralizedBodyStructure;
			}
		}

		return findSingleLateralizedChild(unlaterlizedConcept, lateralityStr);
	}

	private Concept findSingleLateralizedChild(Concept unlaterlizedConcept, String lateralityStr) {
		//Now it might be that the text of the laterlized structure is different from that of the unlateralized structure
		//For example, 955009 |Bronchial structure| is lateralized to 736637009 |Structure of left bronchus|
		List<Concept> lateralizedChildren = new ArrayList<>();
		for (Concept child : unlaterlizedConcept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (child.getFsn().contains(lateralityStr) || child.getFsn().contains(lateralityStr.toLowerCase())) {
				lateralizedChildren.add(child);
			}
		}

		if (lateralizedChildren.size() > 1) {
			String allChildrenStr = lateralizedChildren.stream().map(Concept::toString).collect(Collectors.joining(", "));
			throw new IllegalArgumentException("Multiple lateralized children found for unlateralized: " + unlaterlizedConcept + ": " + allChildrenStr);
		} else if (lateralizedChildren.size() == 1) {
			return lateralizedChildren.get(0);
		}
		return null;
	}


	private Concept findBilateralDescendent(Concept c) {
		//If this concept has one immediate LEFT child, and one immediate RIGHT child, then
		//if BOTH of those concepts feature the same concept, then that is likely to be the bilateral one
		Concept leftChild = findSingleLateralizedChild(c, LEFT.getPreferredSynonym().toLowerCase());
		Concept rightChild = findSingleLateralizedChild(c, RIGHT.getPreferredSynonym().toLowerCase());
		if (leftChild != null && rightChild != null) {
			Set<Concept> leftChildren = leftChild.getChildren(CharacteristicType.INFERRED_RELATIONSHIP);
			Set<Concept> rightChildren = rightChild.getChildren(CharacteristicType.INFERRED_RELATIONSHIP);
			leftChildren.retainAll(rightChildren);
			if (leftChildren.size() == 1) {
				Concept bilateral = leftChildren.iterator().next();
				LOGGER.info("Sanity check bilateral detection: " + bilateral);
				return bilateral;
			}
		}
		return null;
	}

	private Concept findBodyStructureWithFsn(String fsn) throws TermServerScriptException {
		if (bodyStructures == null) {
			bodyStructures = BODY_STRUCTURE.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);
		}
		for (Concept c : bodyStructures) {
			if (c.getFsn().equals(fsn)) {
				return c;
			}
		}
		return null;
	}

	private boolean isBodyStructure(Concept checkMe) throws TermServerScriptException {
		if (bodyStructures == null) {
			bodyStructures = BODY_STRUCTURE.getDescendants(NOT_SET, CharacteristicType.INFERRED_RELATIONSHIP);
		}
		return bodyStructures.contains(checkMe);
	}
}
