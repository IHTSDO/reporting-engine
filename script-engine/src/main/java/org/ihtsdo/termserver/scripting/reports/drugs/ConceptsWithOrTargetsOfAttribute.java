package org.ihtsdo.termserver.scripting.reports.drugs;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
DRUGS-453 A report to identify the following:
	Substance concept that has both:
	1 - stated descendants
	2 - is target of Is modification of attribute
	
	Substance concept that has both:
	1 - stated descendants
	2 - has Is modification of attribute
	
SUBST-265
	1) All concepts that have "is modification" and are not leaf concepts. 
	2) All concepts that are target of is modification and have stated children. (Replacing DRUGS-453)
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConceptsWithOrTargetsOfAttribute extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptsWithOrTargetsOfAttribute.class);

	Concept subHierarchy;
	Concept attributeType;
	
	Map<Concept, List<Concept>> attributeSourceMap = new HashMap<>();
	Map<Concept, List<Concept>> attributeTargetMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		ConceptsWithOrTargetsOfAttribute report = new ConceptsWithOrTargetsOfAttribute();
		try {
			report.getReportManager().setNumberOfDistinctReports(2);
			report.additionalReportColumns = "FSN, Reason";
			report.secondaryReportColumns = "FSN, Reason, Example source";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.populateSourceTargetMaps();
			report.runModifiedNotLeaf();
			report.runModifcationTargetWithChildren();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report",e);
		} finally {
			report.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("105590001"); // |Substance (substance)|
		attributeType = gl.getConcept("738774007"); // |Is modification of (attribute)|
	}

	//Find all concepts that are a target of the attribute type we're interested in
	private void populateSourceTargetMaps() {
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getType().equals(attributeType)) {
						List<Concept> sources = attributeSourceMap.get(r.getSource());
						List<Concept> targets = attributeTargetMap.get(r.getTarget());
						
						if (targets == null) {
							targets = new ArrayList<>();
							attributeTargetMap.put(r.getTarget(), targets);
						}
						
						if (sources == null) {
							sources = new ArrayList<>();
							attributeSourceMap.put(r.getSource(), sources);
						}
						targets.add(r.getSource());
						sources.add(r.getTarget());
					}
				}
			}
		}
	}

	private void runModifiedNotLeaf() throws TermServerScriptException {
		Collection<Concept> subHierarchyConcepts = subHierarchy.getDescendants(NOT_SET);
		for (Concept c : subHierarchyConcepts) {
			//We're only interested in active concepts with descendants - ie not leaf concepts
			if (c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size() > 0) {
				//That have a modification of attribute
				if (c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE).size() > 0) {
					incrementSummaryInformation("Non-Leaf modifications reported");
					report(c, "Modified non-leaf concept");
				}
			}
		}
	}
	
	private void runModifcationTargetWithChildren() throws TermServerScriptException {
		Collection<Concept> subHierarchyConcepts = subHierarchy.getDescendants(NOT_SET);
		for (Concept c : subHierarchyConcepts) {
			//We're only interested in active concepts with stated descendants - ie not leaf concepts
			if (!c.getChildren(CharacteristicType.STATED_RELATIONSHIP).isEmpty()) {
				//That are the target of a modification of attribute
				if (attributeTargetMap.containsKey(c)) {
					incrementSummaryInformation("Non-Leaf modification targets reported");
					report(SECONDARY_REPORT, c, "Target of Modification, with children", attributeTargetMap.get(c).get(0));
				}
			}
		}
	}

}
