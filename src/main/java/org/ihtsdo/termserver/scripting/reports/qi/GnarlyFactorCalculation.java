package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.fixes.SplitRoleGroupsWithRepeatedAttributes;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-4 Reports a number of quality measures across the 870 sub-hierarchies listed.
 * */
public class GnarlyFactorCalculation extends TermServerReport {
	
	List<Concept> subHierarchies;
	IntermediatePrimitivesFromSubHierarchy intermediatePrimitivesReport;
	InferredGroupsNotStated inferredGroupsNotStatedReport;
	SplitRoleGroupsWithRepeatedAttributes splitRoleGroupsWithRepeatedAttributes;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		GnarlyFactorCalculation report = new GnarlyFactorCalculation();
		try {
			report.additionalReportColumns = "Depth, Descendants Stated/Inferred, IntermediatePrimitives, FDs under IPs, Mismatched Groups, RepeatedAttributes";
			report.init(args);
			report.loadProjectSnapshot(false);  //just FSNs
			report.subHierarchies = asConcepts(report.processFile());
			report.run();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, SnowOwlClientException {
		super.init(args);
		
		intermediatePrimitivesReport = new IntermediatePrimitivesFromSubHierarchy();
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
