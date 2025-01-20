package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * SUBST-254 using term pattern X Y where X is the base and Y is some modification words:
 * Compiles a list of all modification words, and then reports on stated IS-A
 * relationships where the base name matches and the child has a modification word, but 
 * does not have a modification attribute
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MissingModifications extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingModifications.class);

	Set<String> modificationPhrases = new HashSet<>();
	Map<Concept, Concept> substancesProductMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		MissingModifications report = new MissingModifications();
		try {
			report.additionalReportColumns = "Base, HasPhrasePrecedent, HasExistingModification, Example Product";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.findSubstancesUsedInProducts();
			report.findModificationWords();
			report.findMissingModifications();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
		}
	}

	private void findSubstancesUsedInProducts() throws TermServerScriptException {
		for (Concept product : PHARM_BIO_PRODUCT.getDescendants(NOT_SET)) {
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
				substancesProductMap.put(r.getTarget(), product);
			}
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE)) {
				substancesProductMap.put(r.getTarget(), product);
			}
		}
	}

	private void findModificationWords() throws TermServerScriptException {
		LOGGER.debug("Finding modification words");
		for (Concept substance : gl.getDescendantsCache().getDescendantsOrSelf(SUBSTANCE)) {
			//What is my X?
			String baseName = SnomedUtilsBase.deconstructFSN(substance.getFsn())[0];
			for (Concept modification : getModifications(substance)) {
				String modName = SnomedUtilsBase.deconstructFSN(modification.getFsn())[0];
				if (modName.startsWith(baseName)) {
					//What is the modification's Y - remove X
					try {
						String modificationPhrase = modName.substring(baseName.length() + 1);
						incrementSummaryInformation("Modification words: " + modificationPhrase);
						modificationPhrases.add(modificationPhrase);
					} catch (Exception e) {
						LOGGER.warn("Unable to remove base name {} from modification {}", baseName, modName);
					}
				}
			}
		}
		
		//We're missing some because no examples have been currently modelled
		modificationPhrases.add("sodium sulfonate");
	}

	private Set<Concept> getModifications(Concept substance) throws TermServerScriptException {
		return gl.getDescendantsCache()
				.getDescendantsOrSelf(SUBSTANCE).stream()
				.filter(child -> child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, substance, ActiveState.ACTIVE).size() > 0)
				.collect(Collectors.toSet());
	}

	private void findMissingModifications() throws TermServerScriptException {
		for (Concept substance : gl.getDescendantsCache().getDescendantsOrSelf(SUBSTANCE)) {
			String baseName = SnomedUtilsBase.deconstructFSN(substance.getFsn())[0];
			//Do I have any immediate stated or inferred children which start with my X which are not a modification of me?
			for (Concept childSubstance : getChildren(substance)) {
				String childName = SnomedUtilsBase.deconstructFSN(childSubstance.getFsn())[0];
				if (childName.startsWith(baseName) && substancesProductMap.containsKey(childSubstance)) {
					boolean exisitingPhrase = false;
					boolean hasModification = false;
					try {
						String modificationPhrase = childName.substring(baseName.length() + 1);
						exisitingPhrase = modificationPhrases.contains(modificationPhrase);
						hasModification = !childSubstance.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE).isEmpty();
					} catch (Exception e) {
						LOGGER.warn ("Unable to remove base name {} from child {}", baseName, childName);
					}
					Concept exampleProduct = substancesProductMap.get(childSubstance);
					report(childSubstance, substance, exisitingPhrase, hasModification, exampleProduct);
				}
			}
		}
	}

	private Set<Concept> getChildren(Concept substance) {
		Set<Concept> allChildren = new HashSet<>(substance.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
		allChildren.addAll(substance.getChildren(CharacteristicType.STATED_RELATIONSHIP));
		return allChildren;
	}
}
