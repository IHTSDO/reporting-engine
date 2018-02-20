package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;

/**
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * */
public class IntermediatePrimitivesFromSubHierarchy extends TermServerReport{
	
	Concept subHierarchy;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		IntermediatePrimitivesFromSubHierarchy report = new IntermediatePrimitivesFromSubHierarchy();
		try {
			report.additionalReportColumns = "ProximalPrimitiveParent, isIntermediate, firstStatedParent";
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.postInit();
			report.reportIntermediatePrimitives();
		} catch (Exception e) {
			println("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	private void postInit() throws TermServerScriptException {
		subHierarchy = gl.getConcept("46866001"); // |Fracture of lower limb (disorder)|
	}

	private void reportIntermediatePrimitives() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			//We're only interested in fully defined concepts
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				List<Concept> proxPrimParents = determineProximalPrimitiveParents(c);
				//Do those parents themselves have sufficiently defined ancestors ie making them intermediate primitives
				for (Concept thisPPP : proxPrimParents) {
					if (containsFdConcept(thisPPP.getAncestors(NOT_SET))) {
						report (c, thisPPP.toString(), "Yes", c.getParents(CharacteristicType.STATED_RELATIONSHIP).get(0).toString());
						incrementSummaryInformation("Intermediate Primitives reported");
						incrementSummaryInformation(thisPPP.toString());
					} else {
						incrementSummaryInformation("Safely modelled count");
						report (c, thisPPP.toString(), "No", c.getParents(CharacteristicType.STATED_RELATIONSHIP).get(0).toString());
					}
				}
				incrementSummaryInformation("FD Concepts checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	}

	private List<Concept> determineProximalPrimitiveParents(Concept c) throws TermServerScriptException {
		//Filter for only the primitive ancestors
		//Sort to work with the lowest level concepts first for efficiency
		List<Concept> primitiveAncestors = c.getAncestors(NOT_SET).stream()
											.filter(ancestor -> ancestor.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE))
											.sorted((c1, c2) -> Integer.compare(c2.getDepth(), c1.getDepth()))
											.collect(Collectors.toList());
		
		//Now which of these primitive concepts do not subsume others?
		Set<Concept> subsumers = new HashSet<>();
		for (Concept thisAncestor : primitiveAncestors) {
			//Skip any that have already been identified as subsumers
			if (!subsumers.contains(thisAncestor)) {
				//Does thisAncestor's ancestors contain any of the other candidates?
				Set<Concept> subsumesThisAncestor = thisAncestor.getAncestors(NOT_SET);
				subsumesThisAncestor.retainAll(primitiveAncestors);
				subsumers.addAll(subsumesThisAncestor);
			}
		}
		//Now remove all subsumers from our list, to leave the most specific concepts
		primitiveAncestors.removeAll(subsumers);
		return primitiveAncestors;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}
	
}
