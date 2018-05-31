package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.SplitRoleGroupsWithRepeatedAttributes;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-4 Reports a number of quality measures across a set of concepts selected via 
 * an ECL constraint.
 * */
public class GnarlyFactorCalculationECL extends TermServerReport {
	
	GenerateInitialAnalysis intermediatePrimitivesReport;
	InferredGroupsNotStated inferredGroupsNotStatedReport;
	SplitRoleGroupsWithRepeatedAttributes splitRoleGroupsWithRepeatedAttributes;
	int lowerLimit = 250;
	int upperLimit = 1000;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		GnarlyFactorCalculationECL report = new GnarlyFactorCalculationECL();
		try {
			report.additionalReportColumns = "FSN, Depth, Descendants Stated/Inferred, IntermediatePrimitives, FDs under IPs, Mismatched Groups, RepeatedAttributes";
			report.init(args);
			report.loadProjectSnapshot(false);  //just FSNs
			List<Component> ECLs = report.identifyGroupersByAttribute(report.processFile());
			report.run(ECLs);
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	


	/**
	 * Rather than working up the list randomly, try to find the first parent which has some 
	 * attribute we can use as an organising principle - eg morphology or causative agent.
	 * @param components
	 * @return
	 * @throws TermServerScriptException
	 */
	private List<Component> identifyGroupersByAttribute(List<Component> components) throws TermServerScriptException {
		List<Component> ECLs = new ArrayList<>();
		for (Component component : components) {
			Relationship ecl = (Relationship) component;
			List<Concept> expansion = doEclExpansion(ecl);
			//Work our way up the stated parents via some attribute that we have;
			int expansionSize = expansion.size();
			Relationship optimalECL =  ecl;
			if ( expansionSize > lowerLimit) {
				warn (ecl + " already has " + expansionSize + " members.  Adding.");
				ECLs.add(optimalECL);
			} else {
				optimalECL = findOptimalECL(ecl, 0);
				if (optimalECL == null) {
					debug ("Failed to find optimal grouper from " + ecl); 
				} else {
					ECLs.add(optimalECL);
				}
			}
		}
		return ECLs;
	}

	private List<Concept> doEclExpansion(Relationship r) throws TermServerScriptException {
		//We're looking for a Clinical Finding with the attribute given
		String ecl = " << " + CLINICAL_FINDING + " : " +
				r.getType() + " = << " +
				r.getTarget();
		
		return findConcepts(project.getBranchPath(), ecl);
	}

	private Relationship findOptimalECL(Relationship r, int hopCount) throws TermServerScriptException {
		//Which ancestor gives us the biggest expansion
		Concept bestParent = null;
		int bestParentCount = lowerLimit;
		for (Concept parent : r.getTarget().getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			Relationship modified = r.clone(null);
			modified.setTarget(parent);
			int descCount = doEclExpansion(modified).size();
			if ( descCount > lowerLimit && (bestParent == null || descCount < bestParentCount)) {
				bestParent = parent;
				bestParentCount = descCount;
			}
		}
		
		//Did we find a solution at this level?
		//TODO include hop count calculation
		Concept bestAncestor = null;
		if (bestParent != null) {
			Relationship modified = r.clone(null);
			modified.setTarget(bestParent);
			return modified;
		} else {
			int bestAncestorCount = lowerLimit;
			for (Concept parent : r.getTarget().getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				Relationship modified = r.clone(null);
				modified.setTarget(parent);
				Relationship thisAncestorRel = findOptimalECL(modified, hopCount + 1);
				int descCount = doEclExpansion(modified).size();
				if ( descCount > lowerLimit && (bestAncestor == null || descCount < bestAncestorCount)) {
					bestAncestor = thisAncestorRel.getTarget();
					bestAncestorCount = descCount;
				}
			}
		}
		
		Relationship modified = r.clone(null);
		modified.setTarget(bestAncestor);
		return modified;
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

	private void run(List<Component> ECLs) throws TermServerScriptException {
		info ("Calculating Gnarly Factor in " + ECLs.size() + " ECLs");
		startTimer();
		int x = 0;
		for (Component ecl : ECLs) {
			Set<Concept> concepts = getECL((Relationship)ecl);
			intermediatePrimitivesReport.setSubHierarchy(concepts);
			inferredGroupsNotStatedReport.setSubHierarchy(concepts);
			splitRoleGroupsWithRepeatedAttributes.setSubHierarchy(concepts);
			
			intermediatePrimitivesReport.reportConceptsAffectedByIntermediatePrimitives();
			String fDsUnderIPs = calculateTotalFDsUnderIPs(concepts, intermediatePrimitivesReport.intermediatePrimitives.keySet());
			int groupDiff = inferredGroupsNotStatedReport.runCheckForInferredGroupsNotStated();
			int repeatedAttributes = splitRoleGroupsWithRepeatedAttributes.identifyComponentsToProcess().size();
			
			report ((Relationship)ecl, 
					"N/A",
					concepts.size(), 
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

	private Set<Concept> getECL(Relationship ecl) {
		// TODO Auto-generated method stub
		return null;
	}
	
	private String calculateTotalFDsUnderIPs(Set<Concept> subHierarchy, Set<Concept> intermediatePrimitives) throws TermServerScriptException {
		int totalFDsUnderIPs = 0;
		int fdsInSubHierarchy = 0;
		for (Concept ip : intermediatePrimitives) {
			for (Concept c : descendantsCache.getDescendentsOrSelf(ip)) {
				if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
					totalFDsUnderIPs++;
					if (subHierarchy.contains(c)) {
						fdsInSubHierarchy++;
					}
				}
			}
		}
		return fdsInSubHierarchy + " (" + totalFDsUnderIPs + ")";
	}

}
