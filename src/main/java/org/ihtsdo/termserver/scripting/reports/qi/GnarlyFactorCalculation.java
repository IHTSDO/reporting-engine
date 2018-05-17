package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.fixes.SplitRoleGroupsWithRepeatedAttributes;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-4 Reports a number of quality measures across the 870 sub-hierarchies listed.
 * */
public class GnarlyFactorCalculation extends TermServerReport {
	
	List<Concept> subHierarchies;
	GenerateInitialAnalysis intermediatePrimitivesReport;
	InferredGroupsNotStated inferredGroupsNotStatedReport;
	SplitRoleGroupsWithRepeatedAttributes splitRoleGroupsWithRepeatedAttributes;
	int lowerLimit = 250;
	int upperLimit = 1000;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		GnarlyFactorCalculation report = new GnarlyFactorCalculation();
		try {
			report.additionalReportColumns = "FSN, Depth, Descendants Stated/Inferred, IntermediatePrimitives, FDs under IPs, Mismatched Groups, RepeatedAttributes";
			report.init(args);
			report.loadProjectSnapshot(false);  //just FSNs
			report.subHierarchies = report.identifyGroupers(report.processFile());
			report.run();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	//Work up the hierarchy until we have a group of concepts about 250 in size
	//but no bigger than 1000.   Consolidate the list around these groupers
	private List<Concept> identifyGroupers(List<Component> components) throws TermServerScriptException {
		List<Concept> groupers = new ArrayList<>();
		for (Concept c : asConcepts(components)) {
			//Only interested in findings and disorders
			if (c.getFsn() == null) {
				warn (c + " appears to be no longer active");
				continue;
			} else if (!c.getFsn().contains("(finding)") && !c.getFsn().contains("(disorder)")) {
				debug ("Skipping: " + c);
				incrementSummaryInformation("Skipped non Clinicial Finding / disorder");
				continue;
			}
			//Work our way up the stated parents to find which one arrives at our target range in 
			//the fewest (or greatest?) number of hops
			int descCount = descendantsCache.getDescendentsOrSelf(c).size();
			if ( descCount > lowerLimit) {
				warn (c + " already has " + descCount + " descendants.  Adding.");
			} else {
				Concept optimal = findOptimalGrouper(c, 0);
				if (optimal == null) {
					warn ("Failed to find optimal grouper for " + c);
				} else {
					//Is the concept already included in our list of groupers?
					if (groupers.contains(optimal) || isDescendantOf(optimal, groupers)) {
						debug (c + " suggested " + optimal + " but this is already captured");
					} else {
						debug (c + " -> " + optimal);
						groupers.add(optimal);
					}
				}
			}
		}
		return groupers;
	}

	private boolean isDescendantOf(Concept optimal, List<Concept> groupers) throws TermServerScriptException {
		for (Concept grouper : groupers) {
			if (descendantsCache.getDescendentsOrSelf(grouper).contains(optimal)) {
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
			int descCount = descendantsCache.getDescendentsOrSelf(parent).size();
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
				int descCount = descendantsCache.getDescendentsOrSelf(thisAncestor).size();
				if ( descCount > lowerLimit && (bestAncestor == null || descCount < bestAncestorCount)) {
					bestAncestor = thisAncestor;
					bestAncestorCount = descCount;
				}
			}
		}
		return bestAncestor;
	}

	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		intermediatePrimitivesReport = new GenerateInitialAnalysis();
		intermediatePrimitivesReport.setQuiet(true);
		
		inferredGroupsNotStatedReport = new InferredGroupsNotStated();
		inferredGroupsNotStatedReport.setQuiet(true);
		
		splitRoleGroupsWithRepeatedAttributes = new SplitRoleGroupsWithRepeatedAttributes(null);
		splitRoleGroupsWithRepeatedAttributes.postLoadInit();
	}

	private void run() throws TermServerScriptException {
		info ("Calculating Gnarly Factor in " + subHierarchies.size() + " sub-hierarchies");
		startTimer();
		int x = 0;
		for (Concept subHierarchy : subHierarchies) {
			String size = getSize(subHierarchy);
			intermediatePrimitivesReport.setSubHierarchy(subHierarchy);
			inferredGroupsNotStatedReport.subHierarchy = subHierarchy;
			splitRoleGroupsWithRepeatedAttributes.subHierarchy = subHierarchy;
			
			intermediatePrimitivesReport.reportConceptsAffectedByIntermediatePrimitives();
			String fDsUnderIPs = calculateTotalFDsUnderIPs(subHierarchy, intermediatePrimitivesReport.intermediatePrimitives.keySet());
			int groupDiff = inferredGroupsNotStatedReport.runCheckForInferredGroupsNotStated();
			int repeatedAttributes = splitRoleGroupsWithRepeatedAttributes.identifyComponentsToProcess().size();
			
			report (subHierarchy, 
					Integer.toString(subHierarchy.getDepth()),
					size, 
					Integer.toString(intermediatePrimitivesReport.intermediatePrimitives.size()),
					fDsUnderIPs,
					Integer.toString(groupDiff),
					Integer.toString(repeatedAttributes));
			
			if (++x%10==0) {
				print(".");
			}
		}
		info("");
	}

	private String getSize(Concept c) throws TermServerScriptException {
		int statedDescendants = c.getDescendents(NOT_SET, CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE).size();
		int inferredDescendants = descendantsCache.getDescendentsOrSelf(c).size();
		return statedDescendants + " / " + inferredDescendants;
	}
	
	private String calculateTotalFDsUnderIPs(Concept subHierarchy, Set<Concept> intermediatePrimitives) throws TermServerScriptException {
		int totalFDsUnderIPs = 0;
		int fdsInSubHierarchy = 0;
		for (Concept ip : intermediatePrimitives) {
			for (Concept c : descendantsCache.getDescendentsOrSelf(ip)) {
				if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					totalFDsUnderIPs++;
					if (descendantsCache.getDescendentsOrSelf(subHierarchy).contains(c)) {
						fdsInSubHierarchy++;
					}
				}
			}
		}
		return fdsInSubHierarchy + " (" + totalFDsUnderIPs + ")";
	}

}
