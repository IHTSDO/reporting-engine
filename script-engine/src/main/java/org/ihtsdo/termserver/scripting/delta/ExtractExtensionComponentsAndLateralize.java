package org.ihtsdo.termserver.scripting.delta;

import org.ihtsdo.otf.exception.TermServerScriptException;
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

	protected void doAdditionalProcessing(Concept c) throws TermServerScriptException {
		LOGGER.info("Creating lateralized concepts for " + c);
		try {
			createLateralizedConcept(c, LEFT);
			createLateralizedConcept(c, RIGHT);
			createLateralizedConcept(c, BILATERAL);
		} catch (TermServerScriptException e) {
			report(c,  Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to create lateralized concepts for " + c + " due to " + e.getMessage());
		}
	}

	private void createLateralizedConcept(Concept c, Concept laterality) throws TermServerScriptException {
		Concept clone = c.cloneAsNewConcept();
		//Lateralize the concepts in the role groups
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			if (laterality.equals(LEFT) || laterality.equals(BILATERAL)) {
				lateralizeGroup(g, LEFT);
			}
			if (laterality.equals(RIGHT)) {
				lateralizeGroup(g, RIGHT);
			}
			if (laterality.equals(BILATERAL)) {
				RelationshipGroup g2 = g.clone(SnomedUtils.getFirstFreeGroup(clone));
				lateralizeGroup(g2, RIGHT);
			}
		}
		lateralizeFsn(c, clone, laterality);
		report(clone, Severity.LOW, ReportActionType.CONCEPT_ADDED, SnomedUtils.getDescriptions(clone), clone.toExpression(CharacteristicType.STATED_RELATIONSHIP));
	}

	private void lateralizeGroup(RelationshipGroup g, Concept laterality) throws TermServerScriptException {
		for (Relationship r : g.getRelationships()) {
			if (isBodyStructure(r.getTarget())) {
				Concept lateralizedBodyStructure = findLateralizedCounterpart(r.getTarget(), laterality);
				if (lateralizedBodyStructure == null) {
					throw new TermServerScriptException("Failed to find lateralized counterpart for " + r.getTarget() + " with laterality " + laterality);
				}
				r.setTarget(lateralizedBodyStructure);
			}
		}
	}

	private void lateralizeFsn(Concept original, Concept clone, Concept laterality) throws TermServerScriptException {
		String lateralityStr = laterality.getPreferredSynonym().toLowerCase();
		boolean successfulLaterlization = false;
		//Can we find the cut point for the specified body structure in the fsn?
		for (String bodyStructureStr : getBodyStructureStrs(original)) {
			if (clone.getFsn().contains(bodyStructureStr)) {
				String laterlizedFsn = clone.getFsn().replace(bodyStructureStr, lateralityStr + " " + bodyStructureStr);
				clone.getFSNDescription().setTerm(laterlizedFsn);
				successfulLaterlization = true;
			}
		}
		
		if (!successfulLaterlization) {
			String allBodyStructures = getBodyStructureStrs(original).stream().collect(Collectors.joining(", "));
			throw new TermServerScriptException("Failed to lateralize FSN for " + original + " with laterality " + laterality + " and body structures " + allBodyStructures);
		}
        normalizeDescriptions(clone, laterality);
	}

    private void normalizeDescriptions(Concept clone, Concept laterality) {
        //remove all non-FSN descriptions and add them back in based on the FSN
		clone.getDescriptions(ActiveState.ACTIVE).stream()
				.filter(d -> !d.getType().equals(DescriptionType.FSN))
				.forEach(d -> clone.removeDescription(d));
		String pt = SnomedUtils.deconstructFSN(clone.getFsn())[0];
		Description ptDesc = Description.withDefaults(pt, DescriptionType.SYNONYM, Acceptability.PREFERRED);
		clone.addDescription(ptDesc);

		//Bilateral concepts will have an acceptable "both" synonym
		if (laterality.equals(BILATERAL)) {
			String bothTerm = pt.replace("bilateral", "both") + "s";
			Description bothDesc = Description.withDefaults(bothTerm, DescriptionType.SYNONYM, Acceptability.ACCEPTABLE);
			clone.addDescription(bothDesc);
		}
    }

    private List<String> getBodyStructureStrs(Concept original) throws TermServerScriptException {
		List<String> bodyStructureStrs = new ArrayList<>();
		for (RelationshipGroup g : original.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			for (Relationship r : g.getRelationships()) {
				if (isBodyStructure(r.getTarget())) {
					bodyStructureStrs.add(r.getTarget().getPreferredSynonym().toLowerCase());
				}
			}
		}
		return bodyStructureStrs;
	}


	private Concept findLateralizedCounterpart(Concept unlaterlizedConcept, Concept laterality) {
		//Ideally our concept would be "Structure of X" and we'd look for "Structure of left X"
		//So try that first, and if not, we'll try the laterality in every place.
		String unlateralizedFsn = unlaterlizedConcept.getFsn();
		String lateralityStr = laterality.getPreferredSynonym().toLowerCase();
		Concept lateralizedBodyStructure = null;
		if (unlateralizedFsn.startsWith("Structure of ")) {
			String lateralizedFsn = unlateralizedFsn.replace("Structure of ", "Structure of " + lateralityStr + " ");
			lateralizedBodyStructure = findBodyStructureWithFsn(lateralizedFsn);
			if (lateralizedBodyStructure == null) {
				lateralizedBodyStructure = findLateralizedCounterpartExhaustive(unlaterlizedConcept, lateralityStr);
			}
		}
		return lateralizedBodyStructure;
	}

	private Concept findLateralizedCounterpartExhaustive(Concept unlaterlizedConcept, String lateralityStr) {
		//Try the laterality in every place
		String unlateralizedFsn = unlaterlizedConcept.getFsn();
		Concept lateralizedBodyStructure = null;
		String[] fsnParts = unlateralizedFsn.split(" ");
		for (int position = 0; position < fsnParts.length; position++) {
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

		return lateralizedBodyStructure;

	}

	private Concept findBodyStructureWithFsn(String fsn) {
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
