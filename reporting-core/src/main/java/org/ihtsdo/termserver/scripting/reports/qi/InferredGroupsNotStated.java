package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI2018
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InferredGroupsNotStated extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(InferredGroupsNotStated.class);

	Set<Concept> subHierarchy;
	List<Concept> largeHierarchies = new ArrayList<>();
	static int LARGE = 6000;
	static int consolidationGrouping = 100;
	Map<Concept, Integer> instancesPerSubHierarchy = new HashMap<>();
	public boolean includeParents = true;
	
	public static void main(String[] args) throws TermServerScriptException {
		InferredGroupsNotStated report = new InferredGroupsNotStated();
		try {
			report.additionalReportColumns = "SemTag, DefnStatus, statedAttribs, infAttribs, UnstatedGroup, ReasonableAncestors";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runCheckForInferredGroupsNotStated();
			report.listInstancesPerSubHierarchy();
		} catch (Exception e) {
			LOGGER.info("Failed to produce InferredGroupsNotStated Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		//subHierarchy = gl.getConcept("138875005"); // |SNOMED CT Concept (SNOMED RT+CTV3)|
		subHierarchy = gl.getConcept("46866001").getDescendants(NOT_SET); // |Fracture of lower limb (disorder)|
		
		//Identify large hierarchies from depth 2 to 6
		for (Concept hierarchy : ROOT_CONCEPT.getDescendants(4)) {
			int hierarchySize = hierarchy.getDescendants(NOT_SET).size();
			if (hierarchySize >= LARGE) {
				LOGGER.debug("Large hierarchy: " + hierarchy + " - " + hierarchySize);
				largeHierarchies.add(hierarchy);
			}
		}
		super.postInit();
	}
	
	public void setSubHierarchy(Concept concept) throws TermServerScriptException {
		this.subHierarchy = concept.getDescendants(NOT_SET);
	}

	public void setSubHierarchy(Set<Concept> concepts) {
		this.subHierarchy = concepts;
	}


	public int runCheckForInferredGroupsNotStated() throws TermServerScriptException {
		int inferredNotStated = 0;
		for (Concept c : this.subHierarchy) {
			inferredNotStated += checkForInferredGroupsNotStated(c);
			incrementSummaryInformation("Concepts checked");
		}
		return inferredNotStated;
	}


	private int checkForInferredGroupsNotStated(Concept c) throws TermServerScriptException {
		boolean unmatchedGroupDetected = false;
		//Work through all inferred groups and see if they're subsumed by a stated group
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP);
		
		nextGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			//Can we find a matching (or less specific but otherwise matching) stated group?
			for (RelationshipGroup statedGroup : statedGroups) {
				if (groupIsSubsumedBy(inferredGroup, statedGroup)) {
					continue nextGroup;
				}
			}
			
			//If we get to here, then an inferred group has not been matched by a stated one
			unmatchedGroupDetected = true;
			
			if (!quiet) {
				Integer statedAttributes = countAttributes(c, CharacteristicType.STATED_RELATIONSHIP);
				Integer inferredAttributes = countAttributes(c, CharacteristicType.INFERRED_RELATIONSHIP);
				incrementSummaryInformation("Relationship groups reported");
				String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				incrementSummaryInformation(semTag);
				Set<Concept> reasonableLevel = getReasonableAncestors(c);
				reasonableLevel.stream().forEach(a -> incrementInstanceCount(a, 1));
				String reasonableLevelStr = reasonableLevel.stream()
						.map(a -> a.toString())
						.collect (Collectors.joining(", "));
				report(c, semTag,
						c.getDefinitionStatus(),
						statedAttributes,
						inferredAttributes,
						inferredGroup, 
						reasonableLevelStr);
			}
		}
		return unmatchedGroupDetected ? 1 : 0;
	}

	/**
	 * Return the ancestors that are below the level of "Large" hierarchies
	 * @param c
	 * @return
	 * @throws TermServerScriptException
	 */
	private Set<Concept> getReasonableAncestors(Concept c) throws TermServerScriptException {
		Set<Concept> ancestors = new HashSet<>(c.getParents(CharacteristicType.INFERRED_RELATIONSHIP));
		boolean movementDetected = true;
		Set<Concept> addAncestors = new HashSet<>();
		Set<Concept> removeAncestors = new HashSet<>();
		do {
			movementDetected= false;
			addAncestors.clear();
			removeAncestors.clear();
 			for (Concept ancestor : ancestors) {
				//Check this ancestors parents and see if we can safely go up a level
 				for (Concept aParent : ancestor.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
 					if (!largeHierarchies.contains(aParent)) {
 						addAncestors.add(aParent);
 						removeAncestors.add(ancestor);
 						movementDetected = true;
 					}
 				}
			}
			ancestors.addAll(addAncestors);
			ancestors.removeAll(removeAncestors);
		} while (movementDetected == true);
		return ancestors;
	}

	private boolean groupIsSubsumedBy(RelationshipGroup a, RelationshipGroup b) throws TermServerScriptException {
		//Is every relationship in group a equal or more specific than one in b?
		for (Relationship ra : a.getRelationships()) {
			//First find types that match, then check for values
			Set<Relationship> subsumingTypes = getSubsumingTypes(ra.getType(), b);
			if (getSubsumingValues(ra.getTarget(), subsumingTypes).size() == 0 ) {
				return false;
			}
		}
		return true;
	}

	private Set<Relationship> getSubsumingTypes(Concept type, RelationshipGroup group) throws TermServerScriptException {
		Set<Relationship> subsumingTypes = new HashSet<>();
		Set<Concept> typeAncestorsOrSelf = type.getAncestors(NOT_SET, 
				CharacteristicType.INFERRED_RELATIONSHIP, 
				true);
		for (Relationship r : group.getRelationships()) {
			if (typeAncestorsOrSelf.contains(r.getType())) {
				subsumingTypes.add(r);
			}
		}
		return subsumingTypes;
	}
	
	private Set<Relationship> getSubsumingValues(Concept value, Set<Relationship> relationships) throws TermServerScriptException {
		Set<Relationship> subsumingValues = new HashSet<>();
		Set<Concept> typeAncestorsOrSelf = value.getAncestors(NOT_SET, 
				CharacteristicType.INFERRED_RELATIONSHIP, 
				true);
		for (Relationship r : relationships) {
			if (typeAncestorsOrSelf.contains(r.getTarget())) {
				subsumingValues.add(r);
			}
		}
		return subsumingValues;
	}
	
	public void incrementInstanceCount(Concept key, int incrementAmount) {
		if (!instancesPerSubHierarchy.containsKey(key)) {
			instancesPerSubHierarchy.put (key, 0);
		}
		int newValue = instancesPerSubHierarchy.get(key) + incrementAmount;
		instancesPerSubHierarchy.put(key, newValue);
	}
	

	private void listInstancesPerSubHierarchy() throws TermServerScriptException {
		consolidateList(consolidationGrouping);
		DecimalFormat df = new DecimalFormat("##.#%");
		for (Map.Entry<Concept, Integer> entry : instancesPerSubHierarchy.entrySet()) {
			Concept subHierarchy = entry.getKey();
			int issueCount = entry.getValue();
			int hierarchySize =  subHierarchy.getDescendants(NOT_SET).size();
			double percent = (issueCount / (double)hierarchySize);
			LOGGER.debug (df.format(percent) + "  " + subHierarchy + " : " + issueCount + " / " + hierarchySize );
		}
	}

	private void consolidateList(int minSize) {
		//Remove any items that are too small and add the amount to the parents
		LOGGER.debug("Before consolidation size = " + instancesPerSubHierarchy.size());
		boolean consolidationMade = false;
		do {
			consolidationMade = false;
			Collection<Concept> concepts = new ArrayList<>(instancesPerSubHierarchy.keySet());
			for (Concept c : concepts) {
				int instanceCount = instancesPerSubHierarchy.get(c).intValue();
				if (instanceCount < minSize) {
					for (Concept parent : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
						incrementInstanceCount (parent, instanceCount);
					}
					instancesPerSubHierarchy.remove(c);
					consolidationMade = true;
				}
			}
		} while (consolidationMade);
		LOGGER.debug("After consolidation size = " + instancesPerSubHierarchy.size());
	}

}
