package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.io.PrintStream;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

/**
 * Reports concepts that are intermediate primitives from point of view of some subhierarchy
 * Update: Adding a 2nd report to determine how many sufficiently defined concepts are affected by an IP
 * */
public class InitialAnalysis extends TermServerReport implements ReportClass {
	
	Concept subHierarchyStart;
	Set<Concept> subHierarchy;
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
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		InitialAnalysis report = new InitialAnalysis(null);
		try {
			ReportSheetManager.targetFolderId = "1m7MVhMePldYrNjOvsE_WTAYcowZ4ps50";  // QI/Initial Analysis
			report.init(args);
			report.loadProjectSnapshot(true);  //just FSNs
			report.postInit();
			info("Generating Intermediate Primitive Report for " + report.subHierarchyStart);
			report.runJob();
		} catch (Exception e) {
			info("Failed to produce Initial Analysis Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
	}
	

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY)
					.withType(JobParameter.Type.CONCEPT)
					.withMandatory()
				.build();
		
		return new Job(	new JobCategory(JobType.REPORT, JobCategory.QI),
						"Initial Analysis",
						"This report lists intermediate primitives and how often attribute types are used in the specified sub-hierarchy. " +
						"Note that the 'Issues' count here refers to the number of intermediate primitives reported in the 2nd tab.",
						params);
	}

	
	public void runJob() throws TermServerScriptException {
		
		info("Reviewing concepts affected by intermediate primitives");
		reportConceptsAffectedByIntermediatePrimitives();
		
		info("Reporting IPs with analysis");
		reportTotalSDsUnderIPs();
		
		info("Reporting attribute usage counts");
		reportAttributeUsageCounts();
	}
	
	public void postInit() throws TermServerScriptException {
		subHierarchyStr = this.jobRun == null ? null : this.jobRun.getMandatoryParamValue(SUB_HIERARCHY);
		try {
			if (subHierarchyStr == null) {
				//setSubHierarchy("46866001");	//       |Fracture of lower limb (disorder)|
				//setSubHierarchy("125605004");	// QI-2  |Fracture of bone (disorder)|
				//setSubHierarchy("128294001");	// QI-8  |Chronic inflammatory disorder (disorder)|
				//setSubHierarchy("126537000");	// QI-11 |Neoplasm of bone (disorder)|
				//setSubHierarchy("34014006");	// QI-12 |Viral disease
				//setSubHierarchy("87628006");	// QI-13 |Bacterial infectious disease (disorder)|
				//setSubHierarchy("95896000");	// QI-18 |Protozoan infection (disorder)|
				//setSubHierarchy("52515009");	// QI-22 |Hernia of abdominal cavity|
				//setSubHierarchy("125666000");	// QI-22 |Burn (disorder)|
				//setSubHierarchy("74627003");	// QI-38 |Diabetic complication (disorder)|
				//setSubHierarchy("283682007");	// QI-35 |Bite - wound (disorder)|
				//setSubHierarchy("8098009");	// QI-40 |Sexually transmitted infectious disease (disorder)|
				//setSubHierarchy("3723001");	// QI-42 |Arthritis|
				//setSubHierarchy("276654001");	// QI-43 |Congenital malformation (disorder)| );
				//setSubHierarchy("3218000");	//QI-46 |Mycosis (disorder)|
				//setSubHierarchy("17322007");	//QI-49 |Disease caused by parasite|
				//setSubHierarchy("416462003");  //QI-50 |Wound (disorder)
				//setSubHierarchy("125643001");  //QI-51 |Open wound|
				//setSubHierarchy("416886008");  //QI-52 |Closed wound|
				//setSubHierarchy("432119003");  //QI- |Aneurysm (disorder)|
				//setSubHierarchy("399963005 |Abrasion|"); //QI-96
				setSubHierarchy("233776003 |Tracheobronchial disorder|"); //QI-152
				/* setSubHierarchy("40733004|Infectious disease|"); //QI-142
				setExclusions(new String[] {"87628006 |Bacterial infectious disease (disorder)|","34014006 |Viral disease (disorder)|",
						"3218000 |Mycosis (disorder)|","8098009 |Sexually transmitted infectious disease (disorder)|", 
						"17322007 |Disease caused by parasite (disorder)|", "91302008 |Sepsis (disorder)|"});
				*/
			} else {
				setSubHierarchy(subHierarchyStr);
			}

			String[] columnHeadings = new String[] {	"SCTID, FSN, SemTag, Proximal Primitive Parent, is Intermediate, Defn Status, Stated Attributes, Stated Role Groups, Inferred Role Groups, Stated Parents",
														"SCTID, FSN, Can Be Sufficiently Defined (1=yes 0=no), JIRA, Comments, Authoring Task, In Subhierarchy,Prim Above Here (NOS),Descendants,Total SDs affected, SD Concepts in subhierarchy, Total Primitive Concepts affected, Primitive Concepts in SubHierarchy",
														"SCTID, FSN, Concepts Using Type, Example" };
			String[] tabNames = new String[] {	"Concepts in Subhierarchy with PPPs",
												"IPs with Counts",
												"Attribute Usage"};
			
			super.postInit(tabNames, columnHeadings, false);
		} catch (Exception e) {
			throw new TermServerScriptException ("Unable to initialise " + this.getClass().getSimpleName(), e);
		}
	}
	
	public void setExclusions (String[] exclusionArr) throws TermServerScriptException {
		exclusions = new HashSet<>();
		for (String exclusionStr : exclusionArr) {
			Concept exclusionStart = gl.getConcept(exclusionStr);
			exclusions.addAll(exclusionStart.getDescendents(NOT_SET));
		}
	}
	
	public String getReportName() {
		if (subHierarchyStart == null) {
			return "Report name not yet known";
		} else {
			return SnomedUtils.deconstructFSN(subHierarchyStart.getFsn())[0] + " - Intermediate Primitives";
		}
	}
	
	public void setSubHierarchy(String subHierarchyStr) throws TermServerScriptException {
		subHierarchyStart = gl.getConcept(subHierarchyStr);
		this.subHierarchy = gl.getDescendantsCache().getDescendentsOrSelf(subHierarchyStart);
		intermediatePrimitives = new HashMap<>();
		attributeUsage = new HashMap<>();
		attributeExamples = new HashMap<>();
	}
	

	public void setSubHierarchy(Set<Concept> concepts) {
		this.subHierarchy = concepts;
		intermediatePrimitives = new HashMap<>();
		attributeUsage = new HashMap<>();
		attributeExamples = new HashMap<>();
	}

	public void reportConceptsAffectedByIntermediatePrimitives() throws TermServerScriptException {
		for (Concept c : this.subHierarchy) {
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
						report (c, thisPPP.toString(), 
								isIntermediate?"Yes":"No",
								c.getDefinitionStatus(),
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
					e.printStackTrace();
				}
			});
	}
	
	private void reportTotalFDsUnderIP(Concept intermediatePrimitive) throws TermServerScriptException {
		int totalFDsUnderIP = 0;
		int fdsInSubHierarchy = 0;
		int totalPrimitiveConceptsUnderIP = 0;
		int totalPrimitiveConceptsUnderIPInSubHierarchy = 0;
		int IPinSubHierarchy = gl.getDescendantsCache().getDescendentsOrSelf(this.subHierarchyStart).contains(intermediatePrimitive) ? 1 : 0;
		if (IPinSubHierarchy == 0) {
			//It was decided to only look at IPs within our current subhierarchy
			return;
		}
		
		for (Concept c : gl.getDescendantsCache().getDescendentsOrSelf(intermediatePrimitive)) {
			if (c.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
				totalFDsUnderIP++;
				if (this.subHierarchy.contains(c)) {
					fdsInSubHierarchy++;
				}
			} else {
				totalPrimitiveConceptsUnderIP++;
				if (this.subHierarchy.contains(c)) {
					totalPrimitiveConceptsUnderIPInSubHierarchy++;
				}
			}
		}
		int aboveMe = primitivesAboveHere(intermediatePrimitive);
		int descendants = gl.getDescendantsCache().getDescendents(intermediatePrimitive).size();
		report (SECONDARY_REPORT, intermediatePrimitive, blankColumns, IPinSubHierarchy, aboveMe, descendants, totalFDsUnderIP, fdsInSubHierarchy, totalPrimitiveConceptsUnderIP, totalPrimitiveConceptsUnderIPInSubHierarchy);
		incrementSummaryInformation(ISSUE_COUNT);
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
		for (Concept c : this.subHierarchy) {
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
