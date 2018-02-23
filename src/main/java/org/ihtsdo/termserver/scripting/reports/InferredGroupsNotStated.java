package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI2018
 */
public class InferredGroupsNotStated extends TermServerReport {
	
	Concept subHierarchy;
	List<Concept> largeHierarchies = new ArrayList<>();
	static int LARGE = 6000;
	Map<Concept, Integer> instancesPerSubHierarchy = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		InferredGroupsNotStated report = new InferredGroupsNotStated();
		try {
			report.additionalReportColumns = "SemTag, UnstatedGroup, nth level ancestor";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runCheckForInferredGroupsNotStated();
			report.listInstancesPerSubHierarchy();
		} catch (Exception e) {
			println("Failed to produce InferredGroupsNotStated Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("138875005"); // |SNOMED CT Concept (SNOMED RT+CTV3)|
		//subHierarchy = gl.getConcept("46866001"); // |Fracture of lower limb (disorder)|
		
		//Identify large hierarchies from depth 2 to 6
		for (Concept hierarchy : ROOT_CONCEPT.getDescendents(4)) {
			int hierarchySize = hierarchy.getDescendents(NOT_SET).size();
			if (hierarchySize >= LARGE) {
				debug ("Large hierarchy: " + hierarchy + " - " + hierarchySize);
				largeHierarchies.add(hierarchy);
			}
		}
	}


	private void runCheckForInferredGroupsNotStated() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			checkForInferredGroupsNotStated(c);
			incrementSummaryInformation("Concepts checked");
		}
	}


	private void checkForInferredGroupsNotStated(Concept c) throws TermServerScriptException {
		//Work through all inferred groups and see if they're subsumed by a stated group
		Collection<RelationshipGroup> inferredGroups = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false);
		Collection<RelationshipGroup> statedGroups = c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false);
		
		nextGroup:
		for (RelationshipGroup inferredGroup : inferredGroups) {
			//Can we find a matching (or less specific but otherwise matching) stated group?
			for (RelationshipGroup statedGroup : statedGroups) {
				if (groupIsSubsumedBy(inferredGroup, statedGroup)) {
					continue nextGroup;
				}
			}
			incrementSummaryInformation("Relationship groups reported");
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			incrementSummaryInformation(semTag);
			List<Concept> ancestorsAtDepth = getAncestorAtDepth(c,2);
			ancestorsAtDepth.stream().forEach(a -> incrementInstanceCount(a, 1));
			String thirdLevel = ancestorsAtDepth.stream()
					.map(a -> a.toString())
					.collect (Collectors.joining(", "));
			report (c, semTag, inferredGroup.toString(), thirdLevel);
		}
	}

	private List<Concept> getAncestorAtDepth(Concept c, int depth) throws TermServerScriptException {
		List<Concept> ancestorsAtDepth = new ArrayList<>();
		if (c.getDepth() >= depth) {
			for (Concept ancestor : c.getAncestors(NOT_SET)) {
				if (ancestor.getDepth() == depth) {
					//If this ancestor is from a large hierarchy, then return the next level down
					if (largeHierarchies.contains(ancestor)) {
						return getAncestorAtDepth(c, depth+1);
					} else {
						ancestorsAtDepth.add(ancestor);
					}
				}
			}
		}
		return ancestorsAtDepth;
	}

	private boolean groupIsSubsumedBy(RelationshipGroup a, RelationshipGroup b) throws TermServerScriptException {
		//Is every relationship in group a equal or more specific than one in b?
		for (Relationship ra : a.getRelationships()) {
			//First find types that match, then check for values
			List<Relationship> subsumingTypes = getSubsumingTypes(ra.getType(), b);
			if (getSubsumingValues(ra.getTarget(), subsumingTypes).size() == 0 ) {
				return false;
			}
		}
		return true;
	}

	private List<Relationship> getSubsumingTypes(Concept type, RelationshipGroup group) throws TermServerScriptException {
		List<Relationship> subsumingTypes = new ArrayList<>();
		Set<Concept> typeAncestorsOrSelf = type.getAncestors(NOT_SET, 
				CharacteristicType.INFERRED_RELATIONSHIP, 
				ActiveState.ACTIVE, 
				true);
		for (Relationship r : group.getRelationships()) {
			if (typeAncestorsOrSelf.contains(r.getType())) {
				subsumingTypes.add(r);
			}
		}
		return subsumingTypes;
	}
	
	private List<Relationship> getSubsumingValues(Concept value, List<Relationship> relationships) throws TermServerScriptException {
		List<Relationship> subsumingValues = new ArrayList<>();
		Set<Concept> typeAncestorsOrSelf = value.getAncestors(NOT_SET, 
				CharacteristicType.INFERRED_RELATIONSHIP, 
				ActiveState.ACTIVE, 
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
			instancesPerSubHierarchy.put (key, new Integer(0));
		}
		int newValue = ((Integer)instancesPerSubHierarchy.get(key)).intValue() + incrementAmount;
		instancesPerSubHierarchy.put(key, newValue);
	}
	

	private void listInstancesPerSubHierarchy() throws TermServerScriptException {
		consolidateList(50);
		DecimalFormat df = new DecimalFormat("##.#%");
		for (Map.Entry<Concept, Integer> entry : instancesPerSubHierarchy.entrySet()) {
			Concept subHierarchy = entry.getKey();
			int issueCount = entry.getValue();
			int hierarchySize =  subHierarchy.getDescendents(NOT_SET).size();
			double percent = (issueCount / (double)hierarchySize);
			debug (df.format(percent) + "  " + subHierarchy + " : " + issueCount + " / " + hierarchySize );
		}
	}

	private void consolidateList(int minSize) {
		//Remove any items that are too small and add the amount to the parents
		debug ("Before consolidation size = " + instancesPerSubHierarchy.size());
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
		debug ("After consolidation size = " + instancesPerSubHierarchy.size());
	}

}


