package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.SplitRoleGroupsWithRepeatedAttributes;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;

/**
 * QI-4 Reports a number of quality measures across a set of concepts selected via 
 * an ECL constraint.
 * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GnarlyFactorCalculationECL extends TermServerReport {

	private static final Logger LOGGER = LoggerFactory.getLogger(GnarlyFactorCalculationECL.class);

	InitialAnalysis intermediatePrimitivesReport;
	InferredGroupsNotStated inferredGroupsNotStatedReport;
	SplitRoleGroupsWithRepeatedAttributes splitRoleGroupsWithRepeatedAttributes;
	int lowerLimit = 250;
	Map<Relationship, Set<Concept>> expansionMap = new HashMap<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		GnarlyFactorCalculationECL report = new GnarlyFactorCalculationECL();
		try {
			report.inputFileHasHeaderRow = false;
			report.additionalReportColumns = "FSN, Depth, Descendants Stated/Inferred, IntermediatePrimitives, FDs under IPs, Mismatched Groups, RepeatedAttributes";
			report.init(args);
			report.loadProjectSnapshot(false);  //just FSNs
			report.startTimer();
			report.identifyGroupersByAttribute(report.processFile());
			report.run();
		} catch (Exception e) {
			LOGGER.error("Failed to produce report", e);
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
	private void identifyGroupersByAttribute(List<Component> components) throws TermServerScriptException {
		for (Component component : components) {
			Relationship ecl = (Relationship) component;
			Collection<Concept> expansion = doEclExpansion(ecl);
			//Work our way up the stated parents via some attribute that we have;
			int expansionSize = expansion.size();
			Relationship optimalECL =  ecl;
			if ( expansionSize == 0) {
				LOGGER.warn (ecl + " matched no concepts.  Skipping");
				continue;
			} else if ( expansionSize > lowerLimit) {
				LOGGER.warn (ecl + " already has " + expansionSize + " members.  Adding.");
			} else {
				optimalECL = findOptimalECL(ecl, 0);
				if (optimalECL == null || optimalECL.getTarget() == null) {
					LOGGER.warn ("Failed to find optimal grouper from " + ecl); 
				} 
			}
		}
	}

	private Collection<Concept> doEclExpansion(Relationship r) throws TermServerScriptException {
		//We're looking for a Clinical Finding with the attribute given
		String ecl = " << " + CLINICAL_FINDING + " : " +
				r.getType() + " = << " +
				r.getTarget();
		return findConcepts(ecl);
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
			} else if (descCount == 0) {
				LOGGER.warn ("ECL found no concepts for " + r + ", returning");
				return null;
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
				//Do we already have this ECL mapped?  No need to request again if so.
				if (expansionMap.containsKey(modified)) {
					LOGGER.info("Have already recovered " + modified);
					return modified;
				}
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
		LOGGER.info("Calculating Gnarly Factor in " + expansionMap.size() + " ECLs");
		int x = 0;
		for (Relationship ecl : expansionMap.keySet()) {
			Set<Concept> eclConcepts = new HashSet<>(expansionMap.get(ecl));
			//Those aren't fully concepts so we'll need to recover them
			Set<Concept> concepts = eclConcepts.stream().map(c -> gl.getConceptSafely(c.getConceptId())).collect(Collectors.toSet());
			intermediatePrimitivesReport.setSubHierarchy(concepts);
			inferredGroupsNotStatedReport.setSubHierarchy(concepts);
			splitRoleGroupsWithRepeatedAttributes.setSubHierarchy(concepts);
			
			intermediatePrimitivesReport.reportConceptsAffectedByIntermediatePrimitives();
			String fDsUnderIPs = calculateTotalFDsUnderIPs(concepts, intermediatePrimitivesReport.intermediatePrimitives.keySet());
			int groupDiff = inferredGroupsNotStatedReport.runCheckForInferredGroupsNotStated();
			int repeatedAttributes = splitRoleGroupsWithRepeatedAttributes.identifyComponentsToProcess().size();
			
			report((Relationship)ecl, 
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
		LOGGER.info("");
	}
	
	private String calculateTotalFDsUnderIPs(Set<Concept> subHierarchy, Set<Concept> intermediatePrimitives) throws TermServerScriptException {
		int totalFDsUnderIPs = 0;
		int fdsInSubHierarchy = 0;
		for (Concept ip : intermediatePrimitives) {
			for (Concept c : gl.getDescendantsCache().getDescendantsOrSelf(ip)) {
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
	
	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		String item = lineItems[0].replaceAll("\\[", "").replaceAll("\\]", "");
		String[] items = item.split("\\->");
		Concept type = gl.getConcept(items[0]);
		Concept target = gl.getConcept(items[1]);
		if (type == null || target == null) {
			LOGGER.warn ("Unable to parse input file for " + item);
			return null;
		}
		Relationship r = new Relationship (null, type, target, UNGROUPED);
		return Collections.singletonList(r);
	}

}
