package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.AssociationEntry;
import org.ihtsdo.termserver.scripting.fixes.SplitRoleGroupsWithRepeatedAttributes;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI-4 Reports a number of quality measures across the 870 sub-hierarchies listed.
 * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GnarlyFactorCalculation extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(GnarlyFactorCalculation.class);

	List<Concept> subHierarchies;
	InitialAnalysis intermediatePrimitivesReport;
	InferredGroupsNotStated inferredGroupsNotStatedReport;
	SplitRoleGroupsWithRepeatedAttributes splitRoleGroupsWithRepeatedAttributes;
	int lowerLimit = 250;
	int upperLimit = 1000;
	List<Concept> organisingPrinciples;
	Set<String> crossCutting = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		GnarlyFactorCalculation report = new GnarlyFactorCalculation();
		try {
			report.additionalReportColumns = "FSN, Depth, Descendants Stated/Inferred, IntermediatePrimitives, FDs under IPs, Mismatched Groups, RepeatedAttributes";
			report.init(args);
			report.loadProjectSnapshot(false);  //just FSNs
			report.postLoadInit();
			report.subHierarchies = report.identifyGroupersByAttribute(report.processFile());
			report.run();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
		} finally {
			report.finish();
			for (String crossCutting : report.crossCutting) {
				LOGGER.info("Possible cross cutting: " + crossCutting);
			}
		}
	}
	
	private void postLoadInit() throws TermServerScriptException {
		organisingPrinciples = new ArrayList<>();
		organisingPrinciples.add(gl.getConcept("116676008")); // |Associated morphology (attribute)|)
		organisingPrinciples.add(gl.getConcept("246075003")); // |Causative agent (attribute)|
		organisingPrinciples.add(gl.getConcept("42752001"));  // |Due to (attribute)|
	}

	//Work up the hierarchy until we have a group of concepts about 250 in size
	//but no bigger than 1000.   Consolidate the list around these groupers
	/*private List<Concept> identifyGroupers(List<Component> components) throws TermServerScriptException {
		List<Concept> groupers = new ArrayList<>();
		for (Concept c : asConcepts(components)) {
			//Only interested in findings and disorders
			if (c.getFsn() == null) {
				LOGGER.warn (c + " appears to be no longer active");
				continue;
			} else if (!c.getFsn().contains("(finding)") && !c.getFsn().contains("(disorder)")) {
				LOGGER.debug("Skipping: " + c);
				incrementSummaryInformation("Skipped non Clinicial Finding / disorder");
				continue;
			}
			//Work our way up the stated parents to find which one arrives at our target range in 
			//the fewest (or greatest?) number of hops
			int descCount = gl.getDescendantsCache().getDescendantsOrSelf(c).size();
			if ( descCount > lowerLimit) {
				LOGGER.warn (c + " already has " + descCount + " descendants.  Adding.");
			} else {
				Concept optimal = findOptimalGrouper(c, 0);
				if (optimal == null) {
					LOGGER.warn ("Failed to find optimal grouper for " + c);
				} else {
					//Is the concept already included in our list of groupers?
					if (groupers.contains(optimal) || isDescendantOf(optimal, groupers)) {
						LOGGER.debug (c + " suggested " + optimal + " but this is already captured");
					} else {
						LOGGER.debug (c + " -> " + optimal);
						groupers.add(optimal);
					}
				}
			}
		}
		return groupers;
	}*/
	
	/**
	 * Rather than working up the list randomly, try to find the first parent which has some 
	 * attribute we can use as an organising principle - eg morphology or causative agent.
	 * @param components
	 * @return
	 * @throws TermServerScriptException
	 */
	private List<Concept> identifyGroupersByAttribute(List<Component> components) throws TermServerScriptException {
		List<Concept> groupers = new ArrayList<>();
		for (Concept c : asConcepts(components)) {
			//Only interested in findings and disorders
			if (!c.isActive() || c.getFsn() == null) {
				c = getAlternative(c);
			}
				
			if (c == null) {
				continue;
			} else if (!c.getFsn().contains("(finding)") && !c.getFsn().contains("(disorder)")) {
				LOGGER.debug("Skipping: " + c);
				incrementSummaryInformation("Skipped non Clinicial Finding / disorder");
				continue;
			}
			//Work our way up the stated parents via some attribute that we have;
			int descCount = gl.getDescendantsCache().getDescendantsOrSelf(c).size();
			Concept optimal =  null;
			if ( descCount > lowerLimit) {
				LOGGER.warn (c + " already has " + descCount + " descendants.  Adding.");
			} else {
				Concept bestOrganisingPrinciple = getBestOrganisingPrinciple(c);
				if (bestOrganisingPrinciple != null) {
					optimal = findOptimalGrouper(c, 0, bestOrganisingPrinciple);
				} else {
					optimal = findOptimalGrouper(c, 0);
				}
				if (bestOrganisingPrinciple != null && optimal == null) {
					LOGGER.debug("Failed to find optimal grouper for " + c + " using organising principle " + bestOrganisingPrinciple);
					crossCutting.add(bestOrganisingPrinciple + " -> " + SnomedUtils.getTargets(c, new Concept[] {bestOrganisingPrinciple}, CharacteristicType.INFERRED_RELATIONSHIP));
					optimal = findOptimalGrouper(c, 0);
				} 
				
				if (optimal == null) {
					LOGGER.debug("Failed to find optimal grouper for " + c);
				} else {
					//Is the concept already included in our list of groupers?
					if (groupers.contains(optimal) || isDescendantOf(optimal, groupers)) {
						LOGGER.debug (c + " suggested " + optimal + " but this is already captured");
					} else {
						LOGGER.debug (c + " -> " + optimal);
						groupers.add(optimal);
					}
				}
			}
		}
		return groupers;
	}

	private Concept getBestOrganisingPrinciple(Concept c) {
		//Do we even have any attributes?
		if (countAttributes(c, CharacteristicType.STATED_RELATIONSHIP) == 0) {
			return null;
		}
		//Otherwise, work through all relationships checking for good organising principles
		for (Concept organisingPrinciple : organisingPrinciples) {
			for (org.ihtsdo.termserver.scripting.domain.Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)) {
				if (r.getType().equals(organisingPrinciple)) {
					return organisingPrinciple;
				}
			}
		}
		LOGGER.warn (c + " failed to deliver organising principle from: " + c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE)
							.stream()
							.map(r->r.getType().toString())
							.collect(Collectors.joining(", ")));
		return null;
	}

	private boolean isDescendantOf(Concept optimal, List<Concept> groupers) throws TermServerScriptException {
		for (Concept grouper : groupers) {
			if (gl.getDescendantsCache().getDescendantsOrSelf(grouper).contains(optimal)) {
				return true;
			}
		}
		return false;
	}

	private Concept findOptimalGrouper(Concept c, int hopCount) throws TermServerScriptException {
		//Which ancestor gives us the best descendant count with the fewest hops?
		Concept bestParent = null;
		int bestParentCount = lowerLimit;
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			int descCount = gl.getDescendantsCache().getDescendantsOrSelf(parent).size();
			if ( descCount > lowerLimit && (bestParent == null || descCount < bestParentCount)) {
				bestParent = parent;
				bestParentCount = descCount;
			}
		}
		
		//Did we find a solution at this level?
		//TODO include hop count calculation
		Concept bestAncestor = null;
		if (bestParent != null) {
			return bestParent;
		} else {
			int bestAncestorCount = lowerLimit;
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				Concept thisAncestor = findOptimalGrouper(parent, hopCount + 1);
				int descCount = gl.getDescendantsCache().getDescendantsOrSelf(thisAncestor).size();
				if ( descCount > lowerLimit && (bestAncestor == null || descCount < bestAncestorCount)) {
					bestAncestor = thisAncestor;
					bestAncestorCount = descCount;
				}
			}
		}
		return bestAncestor;
	}
	
	private Concept findOptimalGrouper(Concept c, int hopCount, Concept organisingPrinciple) throws TermServerScriptException {
		//Which ancestor - that has our organising principle -
		// gives us the best descendant count with the fewest hops?
		Concept bestParent = null;
		int bestParentCount = lowerLimit;
		for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			int descCount = gl.getDescendantsCache().getDescendantsOrSelf(parent).size();
			if (SnomedUtils.hasType(CharacteristicType.INFERRED_RELATIONSHIP, parent, organisingPrinciple) && descCount > lowerLimit && (bestParent == null || descCount < bestParentCount)) {
				bestParent = parent;
				bestParentCount = descCount;
			}
		}
		
		//Did we find a solution at this level?
		//TODO include hop count calculation
		Concept bestAncestor = null;
		if (bestParent != null) {
			return bestParent;
		} else {
			int bestAncestorCount = lowerLimit;
			for (Concept parent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				Concept thisAncestor = findOptimalGrouper(parent, hopCount + 1, organisingPrinciple);
				if (thisAncestor == null) {
					//Failed to find an optimal grouper.
					//TODO - We could fall back to a straight up the line ancestor here
					return null;
				}
				int descCount = gl.getDescendantsCache().getDescendantsOrSelf(thisAncestor).size();
				if (SnomedUtils.hasType(CharacteristicType.INFERRED_RELATIONSHIP, parent, organisingPrinciple) && descCount > lowerLimit && (bestAncestor == null || descCount < bestAncestorCount)) {
					bestAncestor = thisAncestor;
					bestAncestorCount = descCount;
				}
			}
		}
		return bestAncestor;
	}

	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		
		intermediatePrimitivesReport = new InitialAnalysis(this);
		intermediatePrimitivesReport.setQuiet(true);
		
		inferredGroupsNotStatedReport = new InferredGroupsNotStated();
		inferredGroupsNotStatedReport.setQuiet(true);
		
		splitRoleGroupsWithRepeatedAttributes = new SplitRoleGroupsWithRepeatedAttributes(null);
		splitRoleGroupsWithRepeatedAttributes.postLoadInit();
	}

	private void run() throws TermServerScriptException {
		LOGGER.info("Calculating Gnarly Factor in " + subHierarchies.size() + " sub-hierarchies");
		startTimer();
		int x = 0;
		for (Concept subHierarchy : subHierarchies) {
			String size = getSize(subHierarchy);
			throw new IllegalStateException("Modify this code to set ECL rather than subHierarchy");
			/*intermediatePrimitivesReport.setSubHierarchy(subHierarchy.getConceptId());
			inferredGroupsNotStatedReport.setSubHierarchy(subHierarchy);
			splitRoleGroupsWithRepeatedAttributes.setSubHierarchy(subHierarchy);
			
			intermediatePrimitivesReport.reportConceptsAffectedByIntermediatePrimitives();
			String fDsUnderIPs = calculateTotalFDsUnderIPs(subHierarchy, intermediatePrimitivesReport.intermediatePrimitives.keySet());
			int groupDiff = inferredGroupsNotStatedReport.runCheckForInferredGroupsNotStated();
			int repeatedAttributes = splitRoleGroupsWithRepeatedAttributes.identifyComponentsToProcess().size();
			
			report(subHierarchy, 
					Integer.toString(subHierarchy.getDepth()),
					size, 
					Integer.toString(intermediatePrimitivesReport.intermediatePrimitives.size()),
					fDsUnderIPs,
					Integer.toString(groupDiff),
					Integer.toString(repeatedAttributes));
			
			if (++x%10==0) {
				print(".");
			}*/
		}
		LOGGER.info("");
	}

	private String getSize(Concept c) throws TermServerScriptException {
		int statedDescendants = c.getDescendants(NOT_SET, CharacteristicType.STATED_RELATIONSHIP).size();
		int inferredDescendants = gl.getDescendantsCache().getDescendantsOrSelf(c).size();
		return statedDescendants + " / " + inferredDescendants;
	}
	
	private String calculateTotalFDsUnderIPs(Concept subHierarchy, Set<Concept> intermediatePrimitives) throws TermServerScriptException {
		int totalFDsUnderIPs = 0;
		int fdsInSubHierarchy = 0;
		for (Concept ip : intermediatePrimitives) {
			for (Concept c : gl.getDescendantsCache().getDescendantsOrSelf(ip)) {
				if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					totalFDsUnderIPs++;
					if (gl.getDescendantsCache().getDescendantsOrSelf(subHierarchy).contains(c)) {
						fdsInSubHierarchy++;
					}
				}
			}
		}
		return fdsInSubHierarchy + " (" + totalFDsUnderIPs + ")";
	}
	
	private Concept getAlternative(Concept c) throws TermServerScriptException {
		//Work through the active historical associations and find an active alternative
		List<AssociationEntry> assocs = c.getAssociationEntries(ActiveState.ACTIVE);
		if (assocs.size() > 1 || assocs.size() == 0) {
			LOGGER.warn ( c + " is inactive with " + assocs.size() + " historical associations.  Cannot determine alternative concept." );
			return null;
		}
		Concept refset =  gl.getConcept(assocs.get(0).getRefsetId());
		Concept alternative = gl.getConcept(assocs.get(0).getTargetComponentId());
		alternative.setConceptType(c.getConceptType());
		LOGGER.warn("Working on " + alternative + " instead of inactive original " + c + " due to " + refset);
		return alternative;
	}

}
