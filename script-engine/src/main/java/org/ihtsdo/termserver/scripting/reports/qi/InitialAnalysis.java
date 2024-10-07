package org.ihtsdo.termserver.scripting.reports.qi;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * Update: Adding a 2nd report to determine how many sufficiently defined concepts are affected by an IP
 * QI-222 Select concepts by ECL
 * */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InitialAnalysis extends TermServerReport implements org.ihtsdo.termserver.scripting.ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(InitialAnalysis.class);

	private static final int MAX_CONCEPTS = 10000; 
	Collection<Concept> conceptsToAnalyse;
	Set<Concept> exclusions = new HashSet<>();
	public Map<Concept, Integer> intermediatePrimitives;
	public Map<Concept, Integer> attributeUsage;
	public Map<Concept, Concept> attributeExamples;
	String[] blankColumns = new String[] {"","","",""};
	
	public InitialAnalysis() {
	}
	
	public InitialAnalysis(TermServerReport owner) {
		if (owner!=null) {
			setReportManager(owner.getReportManager());
		}
	}
	
	//QI-222  Multi-ecl report runner
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 11429006 |Consultation (procedure)|");
		TermServerScript.run(InitialAnalysis.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL)
					.withType(JobParameter.Type.ECL)
					.withMandatory()
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Initial Analysis")
				.withDescription("This report lists intermediate primitives and how often attribute types are used in the specified sub-hierarchy. " +
						"Note that the 'Issues' count here refers to the number of intermediate primitives reported in the 2nd tab.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		
		LOGGER.info("Reviewing concepts affected by intermediate primitives");
		reportConceptsAffectedByIntermediatePrimitives();
		
		LOGGER.info("Reporting IPs with analysis");
		reportTotalSDsUnderIPs();
		
		LOGGER.info("Reporting attribute usage counts");
		reportAttributeUsageCounts();
	}

	@Override
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1m7MVhMePldYrNjOvsE_WTAYcowZ4ps50");  // QI/Initial Analysis
		
		subsetECL = this.jobRun.getParamValue(ECL);
		
		String[] columnHeadings = new String[] {	"SCTID, FSN, SemTag, Proximal Primitive Parent, is Intermediate, Defn Status, Stated Attributes, Stated Role Groups, Inferred Role Groups, Stated Parents",
													"SCTID, FSN, Can Be Sufficiently Defined (1=yes 0=no), JIRA, Comments, Authoring Task, In Subhierarchy,Prim Above Here (NOS),Descendants,Total SDs affected, SD Concepts in subhierarchy, Total Primitive Concepts affected, Primitive Concepts in SubHierarchy",
													"SCTID, FSN, Concepts Using Type, Example" };
		String[] tabNames = new String[] {	"Concepts in Subhierarchy with PPPs",
											"IPs with Counts",
											"Attribute Usage"};
		
		super.postInit(tabNames, columnHeadings);
		setSubHierarchy();
	}
	
	public void setExclusions (String[] exclusionArr) throws TermServerScriptException {
		exclusions = new HashSet<>();
		for (String exclusionStr : exclusionArr) {
			Concept exclusionStart = gl.getConcept(exclusionStr);
			exclusions.addAll(exclusionStart.getDescendants(NOT_SET));
		}
	}

	@Override
	public String getReportName() {
		if (subsetECL == null) {
			return "Report name not yet known";
		} else {
			String itemOfInterest;
			if (subsetECL.contains(":")) {
				itemOfInterest = subsetECL.substring(subsetECL.indexOf(":") + 1).trim();
			} else {
				itemOfInterest = subsetECL.replaceAll("<",""); 
			}
			return itemOfInterest + " - Initial Analysis";
		}
	}
	
	public void setSubHierarchy() throws TermServerScriptException {
		conceptsToAnalyse = new ArrayList<>(findConcepts(subsetECL));
		intermediatePrimitives = new HashMap<>();
		attributeUsage = new HashMap<>();
		attributeExamples = new HashMap<>();
		
		if (conceptsToAnalyse.size() > MAX_CONCEPTS) {
			throw new TermServerScriptException("ECL selection returned " + conceptsToAnalyse.size() + " concepts.  Refine ECL to select less than 10K");
		}
	}

	public void setSubHierarchy(Set<Concept> concepts) {
		this.conceptsToAnalyse = concepts;
		intermediatePrimitives = new HashMap<>();
		attributeUsage = new HashMap<>();
		attributeExamples = new HashMap<>();
	}

	public void reportConceptsAffectedByIntermediatePrimitives() throws TermServerScriptException {
		for (Concept c : this.conceptsToAnalyse) {
			if (whiteListedConceptIds.contains(c.getId())) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			//Skip exclusions
			if (exclusions.contains(c)) {
				continue;
			}
			
			if (gl.isOrphanetConcept(c)) {
				incrementSummaryInformation("Orphanet concepts excluded");
				continue;
			}
			
			//We're only interested in fully defined concepts
			//Update:  OR leaf concepts 
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED) || 
				(c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE) && 
					c.getChildren(CharacteristicType.INFERRED_RELATIONSHIP).size() == 0)) {
				List<Concept> proxPrimParents = determineProximalPrimitiveParents(c);
				//Do those parents themselves have sufficiently defined ancestors ie making them intermediate primitives
				for (Concept thisPPP : proxPrimParents) {
					boolean isIntermediate = false;
					if (containsFdConcept(gl.getAncestorsCache().getAncestors(thisPPP))) {
						isIntermediate = true;
						if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
							incrementSummaryInformation("Intermediate Primitives reported on SD concepts");
						} else {
							incrementSummaryInformation("Intermediate Primitives reported on Primitive leaf concepts");
						}
						incrementSummaryInformation(thisPPP.toString());
						if (!intermediatePrimitives.containsKey(thisPPP)) {
							incrementSummaryInformation("Unique Intermediate Primitives Reported");
						}
						intermediatePrimitives.merge(thisPPP, 1, Integer::sum);
					} else {
						incrementSummaryInformation("Safely modelled count");
					}
					
					if (!quiet) {
						report(c, thisPPP.toString(),
								isIntermediate?"Yes":"No",
								c.getDefinitionStatus(),
								Integer.toString(countAttributes(c, CharacteristicType.STATED_RELATIONSHIP)),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP).size()),
								Integer.toString(c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP).size()),
								getParentsWithDefnStatus(c)
								);
					}
				}
				incrementSummaryInformation("FD Concepts checked");
			}
			incrementSummaryInformation("Concepts checked");
		}
		if (!quiet) {
			report((Concept)null, "Note that the list above only includes SD and Leaf Concepts, so total may be less than " + conceptsToAnalyse.size() + " expected.");
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

	private boolean containsFdConcept(Collection<Concept> concepts) {
		for (Concept c : concepts) {
			if (c.isActive() && c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				return true;
			}
		}
		return false;
	}
	
	private void reportTotalSDsUnderIPs() throws TermServerScriptException {
		intermediatePrimitives.entrySet().stream()
			.sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
			.forEach(k -> {
				try {
					reportTotalFDsUnderIP(k.getKey());
				} catch (TermServerScriptException e) {
					LOGGER.error("Exception encountered",e);
				}
			});
	}
	
	private void reportTotalFDsUnderIP(Concept intermediatePrimitive) throws TermServerScriptException {
		int totalFDsUnderIP = 0;
		int fdsInSubHierarchy = 0;
		int totalPrimitiveConceptsUnderIP = 0;
		int totalPrimitiveConceptsUnderIPInSubHierarchy = 0;
		int IPinSubHierarchy = conceptsToAnalyse.contains(intermediatePrimitive) ? 1 : 0;
		if (IPinSubHierarchy == 0) {
			//It was decided to only look at IPs within our current subhierarchy
			return;
		}
		
		for (Concept c : gl.getDescendantsCache().getDescendantsOrSelf(intermediatePrimitive)) {
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				totalFDsUnderIP++;
				if (this.conceptsToAnalyse.contains(c)) {
					fdsInSubHierarchy++;
				}
			} else {
				totalPrimitiveConceptsUnderIP++;
				if (this.conceptsToAnalyse.contains(c)) {
					totalPrimitiveConceptsUnderIPInSubHierarchy++;
				}
			}
		}
		int aboveMe = primitivesAboveHere(intermediatePrimitive);
		int descendants = gl.getDescendantsCache().getDescendants(intermediatePrimitive).size();
		report(SECONDARY_REPORT, intermediatePrimitive, blankColumns, IPinSubHierarchy, aboveMe, descendants, totalFDsUnderIP, fdsInSubHierarchy, totalPrimitiveConceptsUnderIP, totalPrimitiveConceptsUnderIPInSubHierarchy);
		countIssue(null);
	}
	
	
	private int primitivesAboveHere(Concept intermediatePrimitive) throws TermServerScriptException {
		//We're assuming that Clinical Finding is our top level.
		Set<Concept> aboveHere = gl.getAncestorsCache().getAncestors(intermediatePrimitive)
				.stream().filter(c -> c.getDefinitionStatus().equals(DefinitionStatus.PRIMITIVE))
				.collect(Collectors.toSet());
		aboveHere.remove(ROOT_CONCEPT);
		aboveHere.remove(CLINICAL_FINDING);
		aboveHere.remove(DISEASE);
		aboveHere.remove(COMPLICATION);
		//And remove all the other intermediate primitives that we know about
		aboveHere.removeAll(intermediatePrimitives.keySet());
		return aboveHere.size();
	}

	private void reportAttributeUsageCounts() throws TermServerScriptException {
		//For every concept in the subhierarchy, get the attribute types used, and an example
		for (Concept c : this.conceptsToAnalyse) {
			for (Concept type : getAttributeTypes(c, CharacteristicType.INFERRED_RELATIONSHIP)) {
				attributeExamples.put(type, c);
				attributeUsage.merge(type, 1, Integer::sum);
			}
		}
		
		attributeUsage.entrySet().stream()
			.sorted((k1, k2) -> k2.getValue().compareTo(k1.getValue()))
			.forEach(k -> reportSafely (TERTIARY_REPORT, k.getKey(), k.getValue(), attributeExamples.get(k.getKey())));
	}

	private Set<Concept> getAttributeTypes(Concept c, CharacteristicType charType) {
		return c.getRelationships(charType, ActiveState.ACTIVE)
				.stream()
				.map(r -> r.getType())
				.collect(Collectors.toSet());
	}

}
