package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;

import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * SUBST-254 using term pattern X Y where X is the base and Y is some modification words:
 * Compiles a list of all modification words, and then reports on stated IS-A
 * relationships where the base name matches and the child has a modification word, but 
 * does not have a modification attribute
 */
public class MissingModifications extends TermServerReport {
	
	Set<String> modificationPhrases = new HashSet<>();
	Map<Concept, Concept> substancesProductMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		MissingModifications report = new MissingModifications();
		try {
			report.additionalReportColumns = "Base, HasPhrasePrecedent, HasExistingModification, Example Product";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.findSubstancesUsedInProducts();
			report.findModificationWords();
			report.findMissingModifications();
		} catch (Exception e) {
			info("Failed to produce MissingAttributeReport due to " + e.getClass().getSimpleName() + ": " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void findSubstancesUsedInProducts() throws TermServerScriptException {
		for (Concept product : PHARM_BIO_PRODUCT.getDescendents(NOT_SET)) {
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
				substancesProductMap.put(r.getTarget(), product);
			}
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE)) {
				substancesProductMap.put(r.getTarget(), product);
			}
		}
	}

	private void findModificationWords() throws TermServerScriptException {
		debug ("Finding modification words");
		for (Concept substance : gl.getDescendantsCache().getDescendentsOrSelf(SUBSTANCE)) {
			//What is my X?
			String baseName = SnomedUtils.deconstructFSN(substance.getFsn())[0];
			for (Concept modification : getModifications(substance)) {
				String modName = SnomedUtils.deconstructFSN(modification.getFsn())[0];
				if (modName.startsWith(baseName)) {
					//What is the modification's Y - remove X
					try {
						String modificationPhrase = modName.substring(baseName.length() + 1);
						incrementSummaryInformation("Modification words: " + modificationPhrase);
						modificationPhrases.add(modificationPhrase);
					} catch (Exception e) {
						warn ("Unable to remove base name " + baseName + " from modification " + modName);
					}
				}
			}
		}
		
		//We're missing some because no examples have been currently modelled
		modificationPhrases.add("sodium sulfonate");
	}

	private Set<Concept> getModifications(Concept substance) throws TermServerScriptException {
		return gl.getDescendantsCache()
				.getDescendentsOrSelf(SUBSTANCE).stream()
				.filter(child -> child.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, substance, ActiveState.ACTIVE).size() > 0)
				.collect(Collectors.toSet());
	}

	private void findMissingModifications() throws TermServerScriptException {
		for (Concept substance : gl.getDescendantsCache().getDescendentsOrSelf(SUBSTANCE)) {
			if (substance.getConceptId().equals("396061008")) {
				debug("Checkpoint");
			}
			String baseName = SnomedUtils.deconstructFSN(substance.getFsn())[0];
			//Do I have any immediate stated or inferred children which start with my X which are not a modification of me?
			for (Concept childSubstance : getChildren(substance)) {
				String childName = SnomedUtils.deconstructFSN(childSubstance.getFsn())[0];
				if (childName.startsWith(baseName) && substancesProductMap.containsKey(childSubstance)) {
					boolean exisitingPhrase = false;
					boolean hasModification = false;
					try {
						String modificationPhrase = childName.substring(baseName.length() + 1);
						exisitingPhrase = modificationPhrases.contains(modificationPhrase);
						hasModification = childSubstance.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE).size() > 0;
					} catch (Exception e) {
						warn ("Unable to remove base name " + baseName + " from child " + childName);
					}
					Concept exampleProduct = substancesProductMap.get(childSubstance);
					report (childSubstance, substance, exisitingPhrase, hasModification, exampleProduct);
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
