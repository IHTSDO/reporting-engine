package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;

/**
 * 
 */
public class InferredGroupsNotStated extends TermServerReport {
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		InferredGroupsNotStated report = new InferredGroupsNotStated();
		try {
			report.additionalReportColumns = "UnstatedGroup";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postInit();
			report.runCheckForInferredGroupsNotStated();
		} catch (Exception e) {
			println("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("46866001"); // |Fracture of lower limb (disorder)|
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
			report (c, inferredGroup.toString());
		}
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

}


