package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.util.StringUtils;

/**
 * INFRA-2723 Detect various possible issues
 * 
 * https://docs.google.com/spreadsheets/d/1jrCR_VOZ6k7qBwDAhTqbt67iisbm_rThV7vt37lr_Rg/edit#gid=0
 
 For cut-n-paste list of issues checked:
 ISRS-391 Descriptions whose module id does not match that of the component
 ISRS-392 Stated Relationships whose module id does not match that of the component
 MAINT-224 Synonyms created as TextDefinitions new content only
 INFRA-2580, MAINT-342 Inactivated concepts without active PT or synonym – new instances only
 ATF-1550 Check that concept has only one semantic tag – new and released content
 ISRS-414 Descriptions which contain a non-breaking space
 ISRS-286 Ensure Parents in same module
 RP-128 Ensure concepts referenced in axioms are active
 Active concept parents should not belong to more than one top-level hierarchy – please check NEW and LEGACY content for issues
 */
public class ReleaseIssuesReport extends TermServerReport implements ReportClass {
	
	Concept subHierarchy = ROOT_CONCEPT;
	private static final String FULL_STOP = ".";
	String[] knownAbbrevs = new String[] {	"ser","ss","subsp",
											"f","E", "var", "St"};
	char NBSP = 255;
	String NBSPSTR = "\u00A0";
	boolean includeLegacyIssues = false;
	private static final int MIN_TEXT_DEFN_LENGTH = 12;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "Y");
		TermServerReport.run(ReleaseIssuesReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		additionalReportColumns = "FSN, Semtag, Issue, Legacy, C/D/R Active, Detail";
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue("N")
				.build();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION),
						"Release Issues Report",
						"This report lists a range of potential issues identified in INFRA-2723. " + 
						"For example 1. Descriptions where the module id does not match the concept module id and, 2. Inactive concepts without an active Preferred Term. "  +
						"Note that the 'Issues' count here refers to components added/modified in the current authoring cycle.",
						params);
	}

	public void runJob() throws TermServerScriptException {
		info("Checking...");
		
		info("...modules are appropriate (~10 seconds)");
		parentsInSameModule();
		unexpectedDescriptionModules();
		unexpectedRelationshipModules();
		
		info("...description rules");
		fullStopInSynonym();
		inactiveMissingFSN_PT();
		nonBreakingSpace();
		
		info("...duplicate Semantic Tags");
		duplicateSemanticTags();
		
		info("...parent hierarchies (~20 seconds)");
		parentsInSameTopLevelHierarchy();
		
		info("...axiom integrity");
		axiomIntegrity();
		
		info("Checks complete");
	}
	//ISRS-286 Ensure Parents in same module.
	//TODO To avoid issues with LOINC and ManagedService, only check core and model module
	//concepts
	private void parentsInSameModule() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (c.getModuleId() == null) {
				warn ("Encountered concept with no module defined: " + c);
				continue;
			}
			if (!c.getModuleId().equals(SCTID_CORE_MODULE) && !c.getModuleId().equals(SCTID_MODEL_MODULE)) {
				continue;
			}
			
			//Also skip the top of the metadata hierarchy - it has a core parent
			//900000000000441003 |SNOMED CT Model Component (metadata)|
			if (!c.isActive() || c.getConceptId().equals("900000000000441003")) {
				continue;
			}
			
			for (Concept p : c.getParents(CharacteristicType.STATED_RELATIONSHIP)) {
				if (!p.getModuleId().equals(c.getModuleId())) {
					report(c, "Mismatching parent moduleId",isLegacy(c), isActive(c,null), p);
					if (isLegacy(c).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
						incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
					}
				}
			}
		}
	}
	

	//ISRS-391 Descriptions whose module id does not match that of the component
	private void unexpectedDescriptionModules() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				if (!d.getModuleId().equals(c.getModuleId())) {
					String msg = "Concept module " + c.getModuleId() + " vs Desc module " + d.getModuleId();
					report(c, "Unexpected Description Module",isLegacy(d), isActive(c,d), msg, d);
					if (isLegacy(d).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
						incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
					}
				}
			}
		}
	}
	
	//ISRS-392 Part II Stated Relationships whose module id does not match that of the component
	private void unexpectedRelationshipModules() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
				if (!r.getModuleId().equals(c.getModuleId())) {
					String msg = "Concept module " + c.getModuleId() + " vs Rel module " + r.getModuleId();
					report(c, "Unexpected Stated Rel Module",isLegacy(r), isActive(c,r), msg, r);
					if (isLegacy(r).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
						incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
					}
				}
			}
		}
	}
	
	//MAINT-224 Synonyms created as TextDefinitions new content only
	private void fullStopInSynonym() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (whiteListedConcepts.contains(c)) {
				continue;
			}
			//Only look at concepts that have been in some way edited in this release cycle
			//Unless we're interested in legacy issues
			if (c.isActive() && (includeLegacyIssues || SnomedUtils.hasNewChanges(c))) {
				for (Description d : c.getDescriptions(Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
					if (d.getTerm().endsWith(FULL_STOP) && d.getTerm().length() > MIN_TEXT_DEFN_LENGTH) {
						report(c, "Possible TextDefn as Synonym",isLegacy(d), isActive(c,d), d);
						incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
						if (isLegacy(d).equals("Y")) {
							incrementSummaryInformation("Legacy Issues Reported");
						}	else {
							incrementSummaryInformation("Fresh Issues Reported");
						}
					}
				}
				
				//Check we've only got max 1 Text Defn for each dialect
				if (c.getDescriptions(US_ENG_LANG_REFSET, Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE).size() > 1 ||
					c.getDescriptions(GB_ENG_LANG_REFSET, Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE).size() > 1 ) {
					report(c, ">1 Text Definition per Dialect","N", "Y");
					incrementSummaryInformation("Fresh Issues Reported");
					incrementSummaryInformation(ISSUE_COUNT);
				}
			}
		}
	}
	
	//INFRA-2580, MAINT-342 Inactivated concepts without active PT or synonym – new instances only
	private void inactiveMissingFSN_PT() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive()) {
				boolean reported = false;
				if (c.getFSNDescription() == null || !c.getFSNDescription().isActive()) {
					report(c, "Inactive concept without active FSN",isLegacy(c), isActive(c,null));
					reported = true;
				}
				
				Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
				if (usPT == null || !usPT.isActive()) {
					report(c, "Inactive concept without active US PT",isLegacy(c), isActive(c,null));
					reported = true;
				}
				
				Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
				if (gbPT == null || !gbPT.isActive()) {
					report(c, "Inactive concept without active GB PT",isLegacy(c), isActive(c,null));
					reported = true;
				}
				
				if (reported) {
					if (isLegacy(c).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
						incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
					}
				}
			}
		}
	}
	
	//ATF-1550 Check that concept has only one semantic tag – new and released content
	private void duplicateSemanticTags() throws TermServerScriptException {
		Map<String, Concept> knownSemanticTags = new HashMap<>();
		Set<String> whiteList = new HashSet<>();
		whiteList.add("368847001");
		whiteList.add("368812009");
		whiteList.add("385238005");
		whiteList.add("368808003");
		
		//First pass through all active concepts to find semantic tags
		for (Concept c : gl.getAllConcepts()) {
			if (c.getFSNDescription() == null) {
				warn("No FSN Description found for concept " + c.getConceptId());
				continue;
			}
			if (c.isActive()) {
				String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
				if (StringUtils.isEmpty(semTag)) {
					String legacy = isLegacy(c.getFSNDescription());
					report(c,"FSN missing semantic tag" ,legacy, isActive(c,c.getFSNDescription()), c.getFsn());
					incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
				} else {
					knownSemanticTags.put(semTag, c);
				}
			}
		}
		
		info ("Collected " + knownSemanticTags.size() + " distinct semantic tags");
		
		//Second pass to see if we have any of these remaining once
		//the real semantic tag (last set of brackets) has been removed
		for (Concept c : gl.getAllConcepts()) {
			if (whiteList.contains(c.getId())) {
				continue;
			}
			if (whiteListedConcepts.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			if (c.getFSNDescription() == null) {
				warn("No FSN Description found (2nd pass) for concept " + c.getConceptId());
				continue;
			}
			String legacy = isLegacy(c.getFSNDescription());
			
			//Don't log lack of semantic tag for inactive concepts
			String termWithoutTag = SnomedUtils.deconstructFSN(c.getFsn(), !c.isActive())[0];
			
			//We can short cut this if we don't have any brackets here.  
			if (!termWithoutTag.contains("(")) {
				continue;
			}
			for (Map.Entry<String, Concept> entry : knownSemanticTags.entrySet()) {
				if (termWithoutTag.contains(entry.getKey())) {
					report(c, "Multiple semantic tags",legacy, isActive(c,c.getFSNDescription()), c.getFsn(), "Contains semtag: " + entry.getKey() + " identified by " + entry.getValue());
					if (legacy.equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
						incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
					}
				}
			}
		}
	}
	
	//ISRS-414 Descriptions which contain a non-breaking space
	private void nonBreakingSpace () throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().indexOf(NBSPSTR) != NOT_SET) {
					String legacy = isLegacy(d);
					String msg = "At position: " + d.getTerm().indexOf(NBSPSTR);
					report(c, "Non-breaking space",legacy, isActive(c,d),msg, d);
					if (legacy.equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
						incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
					}
				}
			}
		}
	}
	
	//Active concept parents should not belong to more than one top-level hierarchy – please check NEW and LEGACY content for issues
	private void parentsInSameTopLevelHierarchy() throws TermServerScriptException {
		Set<Concept> whiteList = new HashSet<>();
		whiteList.add(gl.getConcept("411115002 |Drug-device combination product (product)|")); 
				
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			if (whiteListedConcepts.contains(c)) {
				incrementSummaryInformation(WHITE_LISTED_COUNT);
				continue;
			}
			if (c.isActive()) {
				String legacy = isLegacy(c);
				
				//Skip root concept - has no highest ancestor
				if (c.equals(ROOT_CONCEPT)) {
					continue;
				}
				
				//If this concept - or any of its ancestors - are whitelisted, then skip
				for (Concept a : gl.getAncestorsCache().getAncestorsOrSelf(c)){
					if (whiteList.contains(a)) {
						continue nextConcept;
					}
				}
				
				Concept lastTopLevel = null;
				for (Concept p : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
					//If we are a top level, skip also
					if (p.equals(ROOT_CONCEPT)) {
						continue nextConcept;
					}
					//What top level hierarchy is this parent in?
					Set<Concept> topLevels = SnomedUtils.getHighestAncestorsBefore(p, ROOT_CONCEPT);
					
					if (topLevels.size() > 1) {
						String topLevelStr = topLevels.stream().map(cp -> cp.toString()).collect(Collectors.joining(",\n"));
						report(c, "Parent has multiple top level ancestors", legacy, isActive(c,null), topLevelStr);
						continue nextConcept;
					}
					
					Concept thisTopLevel = topLevels.iterator().next();
					if (lastTopLevel == null) {
						lastTopLevel = thisTopLevel;
					} else if ( !lastTopLevel.equals(thisTopLevel)) {
						report(c, "Mixed TopLevel Parents", legacy, isActive(c,null), thisTopLevel, lastTopLevel);
						if (legacy.equals("Y")) {
							incrementSummaryInformation("Legacy Issues Reported");
						}	else {
							incrementSummaryInformation("Fresh Issues Reported");
							incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
						}
					}
				}
			}
		}
	}
	
	//RP-128
	private void axiomIntegrity() throws TermServerScriptException {
		//Check all concepts referenced in relationships are valid
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				for (Relationship r : c.getRelationships()) {
					if (r.isActive()) {
						String legacy = isLegacy(r);
						if (!r.getType().isActive()) {
							report(c, "Axiom contains inactive type", legacy, isActive(c,r), r);
							incrementSummaryInformation(ISSUE_COUNT); 
						}
						if (!r.getTarget().isActive()) {
							report(c, "Axiom contains inactive target", legacy, isActive(c,r), r);
							incrementSummaryInformation(ISSUE_COUNT); 
						}
					}
				}
			}
		}
	}
	
	private Object isActive(Component c1, Component c2) {
		return (c1.isActive() ? "Y":"N") + "/" + (c2 == null?"" : (c2.isActive() ? "Y":"N"));
	}

	private String isLegacy(Component c) {
		return c.getEffectiveTime() == null ? "N" : "Y";
	}

}
