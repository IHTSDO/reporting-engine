package org.ihtsdo.termserver.scripting.reports;

import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

/**
 * Reports all concepts in a hierarchy
 * - or from a list
 * - optionally using a specific attribute
 * that are used in the definition of other concepts.
 * DRUGS-445
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HierarchyConceptsUsedInDefinitionsReport extends TermServerScript{

	private static final Logger LOGGER = LoggerFactory.getLogger(HierarchyConceptsUsedInDefinitionsReport.class);

	String hierarchy = "49062001"; // |Device (physical object)|
	Concept attributeType = null; // Not currently needed because concepts coming from file
	Set<Concept> ignoredHierarchies;
	Set<String> alreadyReported = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		HierarchyConceptsUsedInDefinitionsReport report = new HierarchyConceptsUsedInDefinitionsReport();
		try {
			report.additionalReportColumns="UsedToDefine, InAttribute, Defn_Status";
			report.init(args);
			report.loadProjectSnapshot(true);  //Load FSNs only
			report.reportConceptsUsedInDefinition();
		} catch (Exception e) {
			LOGGER.info("Failed to validate laterality due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	private void reportConceptsUsedInDefinition() throws TermServerScriptException {

		//Concept sourceHierarchy = gl.getConcept(hierarchy);
		//Set<Concept> sourceConcepts = filterActive(sourceHierarchy.getDescendants(NOT_SET));
		List<Component> sourceConcepts = processFile();
		
		LOGGER.info("Active source concepts number " + sourceConcepts.size());
		Multiset<String> tags = HashMultiset.create();
		for (Concept thisConcept : filterActive(gl.getAllConcepts())) {
			//What hierarchy is this concept in?
			Concept thisHierarchy = SnomedUtils.getTopLevel(thisConcept);
			if (thisHierarchy == null) {
				LOGGER.debug("Unable to determine top level hierarchy for: "  + thisConcept);
			}
			//Skip ignored hierarchies
			if (ignoredHierarchies.contains(thisHierarchy)) {
				continue;
			}
			//Ignore concepts checking themselves
			if (sourceConcepts.contains(thisConcept) || !thisConcept.isActive()) {
				continue;
			}
			for (Relationship thisRelationship : thisConcept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)){
				//Does this relationship use one of our source concepts as a target?
				if (sourceConcepts.contains(thisRelationship.getTarget())) {
					//Only report each source / hierarchy / attribute combination once
					String source_hierarchy_attribute = thisRelationship.getTarget().getConceptId() + "_" +  thisHierarchy.getConceptId() + "_" + thisRelationship.getType().getConceptId();
					if (true /*!alreadyReported.contains(source_hierarchy_attribute)*/) {
						report(thisRelationship.getTarget(), thisConcept, thisRelationship.getType());
						tags.add(SnomedUtils.deconstructFSN(thisConcept.getFsn())[1]);
						alreadyReported.add(source_hierarchy_attribute);
						break;
					}
				}
			}
		}
		
		for (String tag : tags.elementSet()) {
			LOGGER.info("\t" + tag + ": " + tags.count(tag));
		}
	}

	private Set<Concept> filterActive(Collection<Concept> collection) {
		Set <Concept> activeConcepts = new HashSet<Concept>();
		for (Concept thisConcept : collection ) {
			if (thisConcept.isActive()) {
				activeConcepts.add(thisConcept);
			}
		}
		return activeConcepts;
	}

	protected void report(Concept c, Concept usedIn, Concept via) throws TermServerScriptException {
		String line = 	c.getConceptId() + COMMA_QUOTE + 
						c.getFsn().replace(",", "") + QUOTE_COMMA_QUOTE +
						usedIn + QUOTE_COMMA_QUOTE +
						via + QUOTE_COMMA +
						usedIn.getDefinitionStatus();
		
		writeToReportFile(line);
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		ignoredHierarchies = new HashSet<>();
		ignoredHierarchies.add (gl.getConcept("105590001")); // |Substance (substance)|
		ignoredHierarchies.add (gl.getConcept("373873005")); // |Pharmaceutical / biologic product (product)|
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}
}
