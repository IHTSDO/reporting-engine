package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/**
 * QI-2, QI-18
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * Update: Adding a 2nd report to determine how many sufficiently defined concepts are affected by an IP
 * */
public class GenerateInitialAnalysis extends TermServerReport {
	
	Concept subHierarchy;
	public Map<Concept, Integer> intermediatePrimitives;
	public Map<Concept, Integer> attributeUsage;
	public Map<Concept, Concept> attributeExamples;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		GenerateInitialAnalysis report = new GenerateInitialAnalysis();
		try {
			report.additionalReportColumns = "FSN, ProximalPrimitiveParent, isIntermediate, StatedAttributes, StatedRoleGroups, InferredRoleGroups, StatedParents";
			report.secondaryReportColumns = "IP, Total SDs affected, Concepts in subhierarchy";
			report.tertiaryReportColumns = "FSN, Concepts Using Type, Example";
			report.numberOfDistinctReports = 3;
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.postInit();
			report.reportConceptsAffectedByIntermediatePrimitives();
			report.reportTotalFDsUnderIPs();
			report.reportAttributeUsageCounts();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}

	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}
	
	private void postInit() throws TermServerScriptException {
		//setSubHierarchy(gl.getConcept("46866001"));  //       |Fracture of lower limb (disorder)|
		//setSubHierarchy(gl.getConcept("125605004")); // QI-2  |Fracture of bone (disorder)|
		//setSubHierarchy(gl.getConcept("128294001")); // QI-7  |Chronic inflammatory disorder (disorder)|
		//setSubHierarchy(gl.getConcept("126537000")); // QI-11 |Neoplasm of bone (disorder)|
		//setSubHierarchy(gl.getConcept("34014006"));  // QI-12 |Viral disease
		//setSubHierarchy(gl.getConcept("87628006"));  // QI-13 |Bacterial infectious disease (disorder)|
		//setSubHierarchy(gl.getConcept("95896000"));  // QI-18 |Protozoan infection (disorder)|
		setSubHierarchy(gl.getConcept("52515009"));   // QI-22 |Hernia of abdominal cavity|
		//setSubHierarchy(gl.getConcept("125666000"));   // QI-22 |Burn (disorder)|
	}
	
	public void setSubHierarchy(Concept subHierarchy) {
		this.subHierarchy = subHierarchy;
		intermediatePrimitives = new HashMap<>();
		attributeUsage = new HashMap<>();
		attributeExamples = new HashMap<>();
	}

	public void reportConceptsAffectedByIntermediatePrimitives() throws TermServerScriptException {
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			//We're only interested in fully defined concepts
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				List<Concept> proxPrimParents = determineProximalPrimitiveParents(c);
				//Do those parents themselves have sufficiently defined ancestors ie making them intermediate primitives
				for (Concept thisPPP : proxPrimParents) {
					boolean isIntermediate = false;
					if (containsFdConcept(ancestorsCache.getAncestors(thisPPP))) {
						isIntermediate = true;
						incrementSummaryInformation("Intermediate Primitives reported");
						incrementSummaryInformation(thisPPP.toString());
						intermediatePrimitives.merge(thisPPP, 1, Integer::sum);
					} else {
						incrementSummaryInformation("Safely modelled count");
					}
					
					if (!quiet) {
						report (c, thisPPP.toString(), 
								isIntermediate?"Yes":"No", 
								Integer.toString(countAttributes(c, CharacteristicType.STATED_RELATIONSHIP)),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE, false).size()),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE, false).size()),
								getParentsWithDefnStatus(c)
								);
					}
				}
				incrementSummaryInformation("FD Concepts checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
	}

	private String getParentsWithDefnStatus(Concept c) {
		StringBuffer sb = new StringBuffer();
		boolean isFirst = true;
		for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
			if (!isFirst) {
				sb.append(", ");
			} else { 
				isFirst = false;
			}
			sb.append("[")
			.append(SnomedUtils.translateDefnStatus(p.getDefinitionStatus()))
			.append("] ")
			.append(p.toString());
		}
		return sb.toString();
	}

	private int countAttributes(Concept c, CharacteristicType charType) {
		int attributes = 0;
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			if (!r.getType().equals(IS_A)) {
				attributes++;
			}
		}
		return attributes;
	}

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}
	
	private void reportTotalFDsUnderIPs() throws TermServerScriptException {
		intermediatePrimitives.entrySet().stream()
			.sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
			.forEach(k -> {
				try {
					reportTotalFDsUnderIP(k.getKey());
				} catch (TermServerScriptException e) {
					e.printStackTrace();
				}
			});
	}
	
	private void reportTotalFDsUnderIP(Concept intermediatePrimitive) throws TermServerScriptException {
		int totalFDsUnderIP = 0;
		int fdsInSubHierarchy = 0;
		for (Concept c : descendantsCache.getDescendentsOrSelf(intermediatePrimitive)) {
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				totalFDsUnderIP++;
				if (descendantsCache.getDescendentsOrSelf(subHierarchy).contains(c)) {
					fdsInSubHierarchy++;
				}
			}
		}
		report (SECONDARY_REPORT, intermediatePrimitive, totalFDsUnderIP, fdsInSubHierarchy);
	}
	
	
	private void reportAttributeUsageCounts() throws TermServerScriptException {
		//For every concept in the subhierarchy, get the attribute types used, and an example
		for (Concept c : descendantsCache.getDescendentsOrSelf(subHierarchy)) {
			for (Concept type : getAttributeTypes(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
				attributeExamples.put(type, c);
				attributeUsage.merge(type, 1, Integer::sum);
			}
		}
		
		attributeUsage.entrySet().stream()
			.sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
			.forEach(k -> report (TERTIARY_REPORT, k.getKey(), k.getValue(), attributeExamples.get(k.getKey())));
	}

	private Set<Concept> getAttributeTypes(Concept c, CharacteristicType charType) {
		return c.getRelationships(charType, ActiveState.ACTIVE)
				.stream()
				.map(r -> r.getType())
				.collect(Collectors.toSet());
	}
}
