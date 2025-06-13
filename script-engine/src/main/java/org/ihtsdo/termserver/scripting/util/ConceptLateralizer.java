package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.otf.RF2Constants;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.delta.DeltaGenerator;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class ConceptLateralizer implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptLateralizer.class);
	private static ConceptLateralizer singleton;

	private static final String STRUCTURE = "structure";
	private static final String STRUCTURE_OF = "Structure of";
	private static final String USING = "using";
	private static final String USING_SP = " using ";
	private static final String WITH_LATERALITY_SP = " with laterality ";

	private Set<Concept> bodyStructures = null;
	private GraphLoader gl;
	private TermGenerationStrategy termStrategy;
	private DeltaGenerator parent;
	private boolean copyInferredRelationshipsToStatedWhereMissing = false;

	private ConceptLateralizer() {}

	public static ConceptLateralizer get(DeltaGenerator parent, boolean copyInferredRelationshipsToStatedWhereMissing, TermGenerationStrategy termStrategy) {
		if (singleton == null) {
			singleton = new ConceptLateralizer();
			singleton.parent = parent;
			singleton.termStrategy = termStrategy;
			singleton.gl = parent.getGraphLoader();
			singleton.copyInferredRelationshipsToStatedWhereMissing = copyInferredRelationshipsToStatedWhereMissing;
		}
		return singleton;
	}

	/**
	 * @return true if the concept was created or is already schedule to be created, false if it already exists
	 * @throws TermServerScriptException
	 */
	public boolean createLateralizedConceptIfRequired(Concept c, Concept laterality, List<Component> componentsToProcess) throws TermServerScriptException {
		boolean newConceptCreatedOrScheduled = false;
		Concept existingLateralizedConcept = findExistingLateralizedConcept(c, laterality);

		if (existingLateralizedConcept == null) {
			Concept newLateralizedConcept = createLateralizedConcept(c, laterality);
			gl.registerConcept(newLateralizedConcept);
			LOGGER.info("Lateralized concept created: {}", newLateralizedConcept);
			newConceptCreatedOrScheduled = true;
		} else {
			//If this concept is already on our list to process, we don't need to take any action.
			if (componentsToProcess.contains(existingLateralizedConcept)) {
				parent.report(c, RF2Constants.Severity.LOW, RF2Constants.ReportActionType.INFO, "Lateralized concept already scheduled for processed " + existingLateralizedConcept);
				newConceptCreatedOrScheduled = true;
			} else {
				parent.report(c, RF2Constants.Severity.MEDIUM, RF2Constants.ReportActionType.INFO, "Lateralized concept already exists for " + c + WITH_LATERALITY_SP + laterality + " as " + existingLateralizedConcept + ", adding to list to process");
			}
		}
		return newConceptCreatedOrScheduled;
	}

	private Concept findExistingLateralizedConcept(Concept c, Concept laterality) throws TermServerScriptException {
		try {
			if (laterality.equals(BILATERAL)) {
				return findBilateralDescendent(c);
			} else {
				return findLateralizedCounterpart(c, laterality, false);
			}
		} catch (TermServerScriptException e) {
			throw new TermServerScriptException("Failed to find existing lateralized concept for " + c, e);
		}
	}

	private Concept createLateralizedConcept(Concept c, Concept laterality) throws TermServerScriptException {
		Concept clone = c.cloneAsNewConcept(parent.getConIdGenerator().getSCTID());
		//Lateralize the concepts in the role groups
		Collection<RelationshipGroup> originalGroups = clone.getRelationshipGroups(RF2Constants.CharacteristicType.STATED_RELATIONSHIP);
		for (RelationshipGroup g : originalGroups) {
			lateralizeGroup(c, clone, g, laterality);
		}
		lateralizeFsn(c, clone, laterality);
		clone.setDirty();
		SnomedUtils.getAllComponents(clone).forEach(Component::setDirty);

		parent.report(clone, RF2Constants.Severity.LOW, RF2Constants.ReportActionType.CONCEPT_ADDED, SnomedUtils.getDescriptions(clone), clone.toExpression(RF2Constants.CharacteristicType.STATED_RELATIONSHIP));
		return clone;
	}

	private void lateralizeGroup(Concept c, Concept clone, RelationshipGroup g, Concept laterality) throws TermServerScriptException {
		try {
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
		} catch (IllegalArgumentException e) {
			throw new TermServerScriptException("Failed to lateralize group " + g + " for concept " + c + WITH_LATERALITY_SP + laterality, e);
		}
	}

	private void lateralizeGroup(RelationshipGroup g, Concept laterality, boolean overrideCurrentLaterality) throws TermServerScriptException {
		for (Relationship r : g.getRelationships()) {
			if (isBodyStructure(r.getTarget())) {
				Concept lateralizedBodyStructure = findLateralizedCounterpart(r.getTarget(), laterality, overrideCurrentLaterality);
				if (lateralizedBodyStructure == null) {
					String msg = "Failed to find lateralized counterpart for " + r.getTarget() + WITH_LATERALITY_SP + laterality + " - continuing with unlateralized concept.";
					parent.report(r.getSource(), RF2Constants.Severity.HIGH, RF2Constants.ReportActionType.VALIDATION_CHECK, msg);
				} else {
					r.setTarget(lateralizedBodyStructure);
				}
			}
		}
	}

	private void lateralizeFsn(Concept original, Concept clone, Concept laterality) throws TermServerScriptException {
		//Attempt progressive stategies to lateralize the FSN
		String lateralityStr = laterality.getPreferredSynonym().toLowerCase();
		if (!termStrategy.applyTermViaOverride(original, clone)
				&& !lateralizeFsnUsingBodyStructureCutPoint(original, clone, lateralityStr)
				&& !lateralizeFsnUsingAlternativeApproach(clone, lateralityStr, laterality)) {
			String proposedPT = termStrategy.suggestTerm(clone);
			applyTermAsPtAndFsn(original, clone, proposedPT);
		}
		normalizeDescriptions(clone, laterality);
	}

	public void applyTermAsPtAndFsn(Concept original, Concept clone, String proposedPT) throws TermServerScriptException {
		clone.getPreferredSynonym(US_ENG_LANG_REFSET).setTerm(proposedPT);
		String semTag = SnomedUtilsBase.deconstructFSN(original.getFsn())[1];
		clone.getFSNDescription().setTerm(proposedPT + " " + semTag);
	}

	/**
	 * @return true if FSN was successfully lateralized
	 * @throws TermServerScriptException
	 */
	private boolean lateralizeFsnUsingAlternativeApproach(Concept clone, String lateralityStr, Concept laterality) throws TermServerScriptException {
		//We'll add the lateralized body structure to the end of the FSN
		//Or, if it's "using", before "using"
		String[] fsnParts = SnomedUtilsBase.deconstructFSN(clone.getFsn());
		String lateralizedBodyStructurePT = getBodyStructurePT(clone);

		if (lateralizedBodyStructurePT == null) {
			return false;
		}

		if (laterality.equals(BILATERAL) || !lateralizedBodyStructurePT.contains("right")) {
			lateralizedBodyStructurePT = lateralizedBodyStructurePT.replace("left", "right and left");
		}

		//If the body structure was not able to be lateralized, we'll need to force the laterality for the FSN
		if (!lateralizedBodyStructurePT.contains(lateralityStr)) {
			lateralizedBodyStructurePT += " (" + lateralityStr + ")";
		}
		String laterlizedFsn = fsnParts[0] + " of " + lateralizedBodyStructurePT + " " + fsnParts[1];
		if (fsnParts[0].contains(USING)) {
			int cutPoint = fsnParts[0].indexOf(USING_SP);
			laterlizedFsn = fsnParts[0].substring(0, cutPoint) + " of " + lateralizedBodyStructurePT + fsnParts[0].substring(cutPoint) + " " + fsnParts[1];
		}
		clone.getFSNDescription().setTerm(laterlizedFsn);
		clone.setFsn(laterlizedFsn);
		return true;
	}

	private boolean lateralizeFsnUsingBodyStructureCutPoint(Concept original, Concept clone, String lateralityStr) throws TermServerScriptException {
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
		return successfulLaterlization;
	}

	private void normalizeDescriptions(Concept clone, Concept laterality) throws TermServerScriptException {
		//remove all non-FSN descriptions and add them back in based on the FSN
		clone.getDescriptions(RF2Constants.ActiveState.ACTIVE).stream()
				.filter(d -> !d.getType().equals(RF2Constants.DescriptionType.FSN))
				.forEach(clone::removeDescription);
		String pt = SnomedUtilsBase.deconstructFSN(clone.getFSNDescription().getTerm())[0];
		if (laterality.equals(BILATERAL)) {
			pt = pt.replace("right and left", "bilateral");
		}
		Description ptDesc = Description.withDefaults(pt, RF2Constants.DescriptionType.SYNONYM, RF2Constants.Acceptability.PREFERRED);
		clone.addDescription(ptDesc);

		//Bilateral concepts will have an acceptable "both" synonym
		if (laterality.equals(BILATERAL)) {
			String bothTerm = pt.replace("bilateral", "both");
			if (!bothTerm.endsWith(")")) {
				if (bothTerm.contains(" with ")) {
					bothTerm = bothTerm.replace(" with ", "s with ");
				} else if (bothTerm.contains(USING_SP)) {
					bothTerm = bothTerm.replace(USING_SP, "s using ");
				} else {
					bothTerm += "s";
				}
			}
			Description bothDesc = Description.withDefaults(bothTerm, RF2Constants.DescriptionType.SYNONYM, RF2Constants.Acceptability.ACCEPTABLE);
			clone.addDescription(bothDesc);
		}

		//Let's give these descriptions some identifiers!
		clone.getDescriptions(RF2Constants.ActiveState.ACTIVE).stream()
				.forEach(d -> {
					try {
						d.setId(parent.getDescIdGenerator().getSCTID());
						d.setConceptId(clone.getId());
						d.getLangRefsetEntries().forEach(l -> l.setReferencedComponentId(d.getId()));
					} catch (TermServerScriptException e) {
						throw new IllegalStateException(e);
					}
				});
	}

	private Set<String> getBodyStructureStrs(Concept original) throws TermServerScriptException {
		Set<String> bodyStructureStrs = new LinkedHashSet<>();  //Preserve insertion order

		if (original.getRelationshipGroups(RF2Constants.CharacteristicType.STATED_RELATIONSHIP).isEmpty()) {
			String msg = "No stated relationship groups found for " + original;
			if (!copyInferredRelationshipsToStatedWhereMissing) {
				msg += " do you need to set copyInferredRelationshipsToStatedWhereMissing to true in parent class?";
			}
			LOGGER.warn(msg);
		} else {
			for (RelationshipGroup g : original.getRelationshipGroups(RF2Constants.CharacteristicType.STATED_RELATIONSHIP)) {
				getBodyStucturesFromGroup(g, bodyStructureStrs);
			}
		}
		return bodyStructureStrs;
	}

	private void getBodyStucturesFromGroup(RelationshipGroup g, Set<String> bodyStructureStrs) throws TermServerScriptException {
		for (Relationship r : g.getRelationships()) {
			if (isBodyStructure(r.getTarget())) {
				for (Description d : r.getTarget().getDescriptions(RF2Constants.ActiveState.ACTIVE, List.of(RF2Constants.DescriptionType.SYNONYM))) {
					bodyStructureStrs.add(d.getTerm().toLowerCase());
				}
				String pt = r.getTarget().getPreferredSynonym().toLowerCase();
				getBodyStructureFromPreferredTerm(pt, bodyStructureStrs);
			}
		}
	}

	private void getBodyStructureFromPreferredTerm(String pt, Set<String> bodyStructureStrs) {
		String origPT = pt;
		bodyStructureStrs.add(pt);

		if (pt.contains(STRUCTURE)) {
			String ptWithoutStructure = pt.replace(STRUCTURE, "").replace("  ", " ").trim();
			bodyStructureStrs.add(ptWithoutStructure);
			if (ptWithoutStructure.contains(" of ")) {
				String ptWithoutStructureOf = ptWithoutStructure.replace(" of ", " ").replace("  ", " ").trim();
				bodyStructureStrs.add(ptWithoutStructureOf);
			}
		}

		//Also for the case X structure of Y, we'll try just "of Y"
		if (origPT.contains("structure of ")) {
			String[] parts = pt.split(STRUCTURE);
			bodyStructureStrs.add(parts[1]);
		}

		if (origPT.contains("structure of joint of ")) {
			String[] parts = pt.split("structure of joint ");
			bodyStructureStrs.add(parts[1]);
		}
	}

	private String getBodyStructurePT(Concept c) throws TermServerScriptException {
		for (RelationshipGroup g : c.getRelationshipGroups(RF2Constants.CharacteristicType.STATED_RELATIONSHIP)) {
			for (Relationship r : g.getRelationships()) {
				if (isBodyStructure(r.getTarget())) {
					return r.getTarget().getPreferredSynonym().toLowerCase();
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

		if (lateralizedCounterpart == null && isBodyStructure(unlaterlizedConcept) && unlateralizedFsn.startsWith(STRUCTURE_OF)) {
			String lateralizedFsn = unlateralizedFsn.replace(STRUCTURE_OF, STRUCTURE_OF + lateralityStr + " ");
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
		for (Concept child : unlaterlizedConcept.getChildren(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP)) {
			if (containsLateralityString(child.getFsn(), lateralityStr)) {
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

	private boolean containsLateralityString(String term, String lateralityStr) {
		//To avoid matching "cleft", we'll add a space and check that.
		term = " " + term;
		lateralityStr = " " + lateralityStr;
		return term.toLowerCase().contains(lateralityStr.toLowerCase());
	}


	private Concept findBilateralDescendent(Concept c) {
		//If this concept has one immediate LEFT child, and one immediate RIGHT child, then
		//if BOTH of those concepts feature the same concept, then that is likely to be the bilateral one
		Concept leftChild = findSingleLateralizedChild(c, LEFT.getPreferredSynonym().toLowerCase());
		Concept rightChild = findSingleLateralizedChild(c, RIGHT.getPreferredSynonym().toLowerCase());
		if (leftChild != null && rightChild != null) {
			Set<Concept> leftChildren = leftChild.getChildren(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP);
			Set<Concept> rightChildren = rightChild.getChildren(RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP);
			leftChildren.retainAll(rightChildren);
			if (leftChildren.size() == 1) {
				Concept bilateral = leftChildren.iterator().next();
				LOGGER.info("Sanity check bilateral detection: {}", bilateral);
				return bilateral;
			}
		}
		return null;
	}

	private Concept findBodyStructureWithFsn(String fsn) throws TermServerScriptException {
		if (bodyStructures == null) {
			bodyStructures = BODY_STRUCTURE.getDescendants(NOT_SET, RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP);
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
			bodyStructures = BODY_STRUCTURE.getDescendants(NOT_SET, RF2Constants.CharacteristicType.INFERRED_RELATIONSHIP);
		}
		return bodyStructures.contains(checkMe);
	}

}
