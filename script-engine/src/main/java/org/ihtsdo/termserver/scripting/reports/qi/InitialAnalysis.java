package org.ihtsdo.termserver.scripting.reports.qi;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
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

	private static Logger LOGGER = LoggerFactory.getLogger(InitialAnalysis.class);

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
	public static void main(String[] args) throws TermServerScriptException, IOException {
		//TermServerScript.runHeadless(3);
		/*String[] morphologies = new String[] {
				"11889001|Abiotrophy (morphologic abnormality)|",
				"13331008|Atrophy (morphologic abnormality)|",
				"33359002|Degeneration (morphologic abnormality)|",
				"32693004|Demyelination (morphologic abnormality)|",
				"69251000|Depletion (morphologic abnormality)|",
				"46595003|Deposition (morphologic abnormality)|",
				"4720007|Dystrophy (morphologic abnormality)|",
				"2218006|Endothelial degeneration (morphologic abnormality)|",
				"47939006|Etat criblé (morphologic abnormality)|",
				"66984008|Etat lacunaire (morphologic abnormality)|",
				"16190006|Herring's bodies (morphologic abnormality)|",
				"18695008|Hyaline body (morphologic abnormality)|",
				"708529002|Lesion of degenerative abnormality (morphologic abnormality)|",
				"107670002|Lysis AND/OR resorbed tissue (morphologic abnormality)|",
				"35828005|Malacia (morphologic abnormality)|",
				"15524008|Obliteration (morphologic abnormality)|",
				"107671003|Vascular sclerosis (morphologic abnormality)|"};
		for (String morphology : morphologies) {
			String ecl = "<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = << " + morphology;
			Map<String, String> params = new HashMap<>();
			params.put(ECL, ecl);
			TermServerReport.run(InitialAnalysis.class, args, params);
		} */
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 11429006 |Consultation (procedure)|");
		TermServerReport.run(InitialAnalysis.class, args, params);
	}
	
/*	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(ECL, "<< 46866001");	//       |Fracture of lower limb (disorder)|
		//params.put(ECL, "<< 125605004");	// QI-2  |Fracture of bone (disorder)|
		//params.put(ECL, "<< 128294001");	// QI-8  |Chronic inflammatory disorder (disorder)|
		//params.put(ECL, "<< 126537000");	// QI-11 |Neoplasm of bone (disorder)|
		//params.put(ECL, "<< 34014006");	// QI-12 |Viral disease
		//params.put(ECL, "<< 87628006");	// QI-13 |Bacterial infectious disease (disorder)|
		//params.put(ECL, "<< 95896000");	// QI-18 |Protozoan infection (disorder)|
		//params.put(ECL, "<< 52515009");	// QI-22 |Hernia of abdominal cavity|
		//params.put(ECL, "<< 125666000");	// QI-22 |Burn (disorder)|
		//params.put(ECL, "<< 74627003");	// QI-38 |Diabetic complication (disorder)|
		//params.put(ECL, "<< 283682007");	// QI-35 |Bite - wound (disorder)|
		//params.put(ECL, "<< 8098009");	// QI-40 |Sexually transmitted infectious disease (disorder)|
		//params.put(ECL, "<< 3723001");	// QI-42 |Arthritis|
		//params.put(ECL, "<< 276654001");	// QI-43 |Congenital malformation (disorder)| );
		//params.put(ECL, "<< 3218000");	//QI-46 |Mycosis (disorder)|
		//params.put(ECL, "<< 17322007");	//QI-49 |Disease caused by parasite|
		//params.put(ECL, "<< 416462003");  //QI-50 |Wound (disorder)
		//params.put(ECL, "<< 125643001");  //QI-51 |Open wound|
		//params.put(ECL, "<< 416886008");  //QI-52 |Closed wound|
		//params.put(ECL, "<< 432119003");  //QI- |Aneurysm (disorder)|
		//params.put(ECL, "<< 399963005 |Abrasion|"); //QI-96
		//params.put(ECL, "<< setSubHierarchy("233776003 |Tracheobronchial disorder|"); //QI-152
		//params.put(ECL, "<< 40733004|Infectious disease|"); //QI-142
		/* setExclusions(new String[] {"87628006 |Bacterial infectious disease (disorder)|","34014006 |Viral disease (disorder)|",
				"3218000 |Mycosis (disorder)|","8098009 |Sexually transmitted infectious disease (disorder)|", 
				"17322007 |Disease caused by parasite (disorder)|", "91302008 |Sepsis (disorder)|"});
		
		params.put(ECL, "<< 404684003 |Clinical finding (finding)| : 116676008 |Associated morphology (attribute)| = 72704001 |Fracture (morphologic abnormality)|");
		TermServerReport.run(InitialAnalysis.class, args, params);
	}*/
	
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

	
	public void runJob() throws TermServerScriptException {
		
		LOGGER.info("Reviewing concepts affected by intermediate primitives");
		reportConceptsAffectedByIntermediatePrimitives();
		
		LOGGER.info("Reporting IPs with analysis");
		reportTotalSDsUnderIPs();
		
		LOGGER.info("Reporting attribute usage counts");
		reportAttributeUsageCounts();
	}
	
	public void postInit() throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1m7MVhMePldYrNjOvsE_WTAYcowZ4ps50";  // QI/Initial Analysis
		
		subsetECL = this.jobRun.getParamValue(ECL);
		
		String[] columnHeadings = new String[] {	"SCTID, FSN, SemTag, Proximal Primitive Parent, is Intermediate, Defn Status, Stated Attributes, Stated Role Groups, Inferred Role Groups, Stated Parents",
													"SCTID, FSN, Can Be Sufficiently Defined (1=yes 0=no), JIRA, Comments, Authoring Task, In Subhierarchy,Prim Above Here (NOS),Descendants,Total SDs affected, SD Concepts in subhierarchy, Total Primitive Concepts affected, Primitive Concepts in SubHierarchy",
													"SCTID, FSN, Concepts Using Type, Example" };
		String[] tabNames = new String[] {	"Concepts in Subhierarchy with PPPs",
											"IPs with Counts",
											"Attribute Usage"};
		
		super.postInit(tabNames, columnHeadings, false);
		setSubHierarchy();
	}
	
	public void setExclusions (String[] exclusionArr) throws TermServerScriptException {
		exclusions = new HashSet<>();
		for (String exclusionStr : exclusionArr) {
			Concept exclusionStart = gl.getConcept(exclusionStr);
			exclusions.addAll(exclusionStart.getDescendents(NOT_SET));
		}
	}
	
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
						report (c, thisPPP.toString(), 
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
			report ((Concept)null, "Note that the list above only includes SD and Leaf Concepts, so total may be less than " + conceptsToAnalyse.size() + " expected.");
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
		int IPinSubHierarchy = conceptsToAnalyse.contains(intermediatePrimitive) ? 1 : 0;
		if (IPinSubHierarchy == 0) {
			//It was decided to only look at IPs within our current subhierarchy
			return;
		}
		
		for (Concept c : gl.getDescendantsCache().getDescendentsOrSelf(intermediatePrimitive)) {
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
		int descendants = gl.getDescendantsCache().getDescendents(intermediatePrimitive).size();
		report (SECONDARY_REPORT, intermediatePrimitive, blankColumns, IPinSubHierarchy, aboveMe, descendants, totalFDsUnderIP, fdsInSubHierarchy, totalPrimitiveConceptsUnderIP, totalPrimitiveConceptsUnderIPInSubHierarchy);
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
