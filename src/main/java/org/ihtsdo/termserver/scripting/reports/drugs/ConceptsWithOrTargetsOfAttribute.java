package org.ihtsdo.termserver.scripting.reports.drugs;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * DRUGS-454 A report to identify the following:
	Substance concept that has both:
	1 - stated descendants
	2 - is target of Is modification of attribute
	
	Substance concept that has both:
	1 - stated descendants
	2 - has Is modification of attribute
 */
public class ConceptsWithOrTargetsOfAttribute extends TermServerReport {
	
	Concept subHierarchy;
	Concept attributeType;
	
	Map<Concept, List<Concept>> attributeSourceMap = new HashMap<>();
	Map<Concept, List<Concept>> attributeTargetMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ConceptsWithOrTargetsOfAttribute report = new ConceptsWithOrTargetsOfAttribute();
		try {
			report.additionalReportColumns = "Reason, References";
			report.init(args);
			report.loadProjectSnapshot(false);  //Load all descriptions
			report.postLoadInit();
			report.populateSourceTargetMaps();
			report.runRepeatedAttributeValueReport();
		} catch (Exception e) {
			info("Failed to produce ConceptsWithOrTargetsOfAttribute Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
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
							targets = new ArrayList<Concept>();
							attributeTargetMap.put(r.getTarget(), targets);
						}
						
						if (sources == null) {
							sources = new ArrayList<Concept>();
							attributeSourceMap.put(r.getSource(), sources);
						}
						
						targets.add(r.getSource());
						sources.add(r.getTarget());
					}
				}
			}
		}
	}

	private void runRepeatedAttributeValueReport() throws TermServerScriptException {
		Collection<Concept> subHierarchyConcepts = subHierarchy.getDescendents(NOT_SET);
		for (Concept c : subHierarchyConcepts) {
			//We're only interested in active concepts with descendants
			if (c.isActive() && c.getChildren(CharacteristicType.STATED_RELATIONSHIP).size() > 0) {
				//Now either include if we're a target of the specified attribute type
				//or have that attribute type ourselves 
				if (attributeTargetMap.get(c) != null) {
					for (Concept source : attributeTargetMap.get(c)) {
						report (c, "Is Source", source.toString());
						incrementSummaryInformation("Sources reported");
					}
				}
				
				if (attributeSourceMap.get(c) != null) {
					for (Concept target : attributeSourceMap.get(c)) {
						report (c, "Is Target", target.toString());
						incrementSummaryInformation("Targets reported");
					}
				}
			}
		}
		addSummaryInformation("Concepts checked", subHierarchyConcepts.size());
	}

}


