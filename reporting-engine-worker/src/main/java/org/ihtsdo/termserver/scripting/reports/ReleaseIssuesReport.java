package org.ihtsdo.termserver.scripting.reports;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.AxiomUtils;
import org.ihtsdo.termserver.scripting.DescendentsCache;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.owltoolkit.conversion.ConversionException;
import org.snomed.otf.owltoolkit.domain.AxiomRepresentation;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.util.StringUtils;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

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
 RP-127 Disease specific rules
 RP-165 Text definition dialect rules
 RP-179 concepts using surgical approach must be surgical procedures
 RP-181 Combined body sites cannot be the target for finding/procedure sites
 RP-181 No new combined body sites
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
	private Map<String, Integer> issueSummaryMap = new HashMap<>();
	DescendentsCache cache;
	
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
		cache = gl.getDescendantsCache();
		getArchiveManager().populateReleasedFlag = true;
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { "SCTID, FSN, Semtag, Issue, Legacy, C/D/R Active, Detail",
				"Issue, Count"};
		String[] tabNames = new String[] {	"Issues",
				"Summary"};
		
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
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
		
		info("...duplicate semantic tags");
		duplicateSemanticTags();
		
		info("...parent hierarchies (~20 seconds)");
		parentsInSameTopLevelHierarchy();
		
		info("...axiom integrity");
		axiomIntegrity();
		
		info("...Disease semantic tag rule");
		diseaseIntegrity();
		
		info("...Text definition dialect checks");
		textDefinitionDialectChecks();
		
		info("...Nested brackets check");
		nestedBracketCheck();
		
		info("...Modelling rules check");
		validateAttributeDomainModellingRules();
		validateAttributeTypeValueModellingRules();
		validateDeprecatedHierarchies();
		
		info("Checks complete, creating summary tag");
		populateSummaryTab();
		
		info("Summary tab complete, all done.");
	}

	private void populateSummaryTab() throws TermServerScriptException {
		issueSummaryMap.entrySet().stream()
				.sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
				.forEach(e -> reportSafely (SECONDARY_REPORT, (Component)null, e.getKey(), e.getValue()));
	}

	//ISRS-286 Ensure Parents in same module.
	//TODO To avoid issues with LOINC and ManagedService, only check core and model module
	//concepts
	private void parentsInSameModule() throws TermServerScriptException {
		String issueStr = "Mismatching parent moduleId";
		initialiseSummary(issueStr);
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
					report(c, issueStr,isLegacy(c), isActive(c,null), p);
					if (isLegacy(c).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
				}
			}
		}
	}
	

	//ISRS-391 Descriptions whose module id does not match that of the component
	private void unexpectedDescriptionModules() throws TermServerScriptException {
		String issueStr ="Unexpected Description Module";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions()) {
				if (!d.getModuleId().equals(c.getModuleId())) {
					String msg = "Concept module " + c.getModuleId() + " vs Desc module " + d.getModuleId();
					report(c, issueStr, isLegacy(d), isActive(c,d), msg, d);
					if (isLegacy(d).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
				}
			}
		}
	}
	
	//ISRS-392 Part II Stated Relationships whose module id does not match that of the component
	private void unexpectedRelationshipModules() throws TermServerScriptException {
		String issueStr = "Unexpected Stated Rel Module";
		initialiseSummary(issueStr);
		for (Concept c : gl.getAllConcepts()) {
			for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.BOTH)) {
				if (!r.getModuleId().equals(c.getModuleId())) {
					String msg = "Concept module " + c.getModuleId() + " vs Rel module " + r.getModuleId();
					report(c, issueStr, isLegacy(r), isActive(c,r), msg, r);
					if (isLegacy(r).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
				}
			}
		}
	}
	
	//MAINT-224 Synonyms created as TextDefinitions new content only
	private void fullStopInSynonym() throws TermServerScriptException {
		String issueStr = "Possible TextDefn as Synonym";
		String issue2Str = ">1 Text Definition per Dialect";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		for (Concept c : gl.getAllConcepts()) {
			if (whiteListedConcepts.contains(c)) {
				continue;
			}
			//Only look at concepts that have been in some way edited in this release cycle
			//Unless we're interested in legacy issues
			if (c.isActive() && (includeLegacyIssues || SnomedUtils.hasNewChanges(c))) {
				for (Description d : c.getDescriptions(Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE)) {
					if (d.getTerm().endsWith(FULL_STOP) && d.getTerm().length() > MIN_TEXT_DEFN_LENGTH) {
						report(c, issueStr, isLegacy(d), isActive(c,d), d);
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
					report(c, issue2Str,"N", "Y");
					incrementSummaryInformation("Fresh Issues Reported");
				}
			}
		}
	}
	
	//INFRA-2580, MAINT-342 Inactivated concepts without active PT or synonym – new instances only
	private void inactiveMissingFSN_PT() throws TermServerScriptException {
		String issueStr = "Inactive concept without active FSN";
		String issue2Str = "Inactive concept without active US PT";
		String issue3Str = "Inactive concept without active GB PT";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		initialiseSummary(issue3Str);
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive()) {
				boolean reported = false;
				if (c.getFSNDescription() == null || !c.getFSNDescription().isActive()) {
					report(c, issueStr, isLegacy(c), isActive(c,null));
					reported = true;
				}
				
				Description usPT = c.getPreferredSynonym(US_ENG_LANG_REFSET);
				if (usPT == null || !usPT.isActive()) {
					report(c, issue2Str, isLegacy(c), isActive(c,null));
					reported = true;
				}
				
				Description gbPT = c.getPreferredSynonym(GB_ENG_LANG_REFSET);
				if (gbPT == null || !gbPT.isActive()) {
					report(c, issue3Str,isLegacy(c), isActive(c,null));
					reported = true;
				}
				
				if (reported) {
					if (isLegacy(c).equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
				}
			}
		}
	}
	
	//ATF-1550 Check that concept has only one semantic tag – new and released content
	private void duplicateSemanticTags() throws TermServerScriptException {
		String issueStr = "FSN missing semantic tag";
		String issue2Str = "Multiple semantic tags";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
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
					report(c, issueStr, legacy, isActive(c,c.getFSNDescription()), c.getFsn());
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
					report(c, issue2Str, legacy, isActive(c,c.getFSNDescription()), c.getFsn(), "Contains semtag: " + entry.getKey() + " identified by " + entry.getValue());
					if (legacy.equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
				}
			}
		}
	}
	
	//ISRS-414 Descriptions which contain a non-breaking space
	private void nonBreakingSpace () throws TermServerScriptException {
		String issueStr = "Non-breaking space";
		initialiseSummary(issueStr);
		
		for (Concept c : gl.getAllConcepts()) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().indexOf(NBSPSTR) != NOT_SET) {
					String legacy = isLegacy(d);
					String msg = "At position: " + d.getTerm().indexOf(NBSPSTR);
					report(c, issueStr, legacy, isActive(c,d),msg, d);
					if (legacy.equals("Y")) {
						incrementSummaryInformation("Legacy Issues Reported");
					}	else {
						incrementSummaryInformation("Fresh Issues Reported");
					}
				}
			}
		}
	}
	
	//Active concept parents should not belong to more than one top-level hierarchy – please check NEW and LEGACY content for issues
	private void parentsInSameTopLevelHierarchy() throws TermServerScriptException {
		String issueStr = "Parent has multiple top level ancestors";
		String issue2Str = "Mixed TopLevel Parents";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
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
						report(c, issueStr, legacy, isActive(c,null), topLevelStr);
						continue nextConcept;
					} else if (topLevels.size() == 0) {
						report(c, "Failed to find top level of parent ", legacy, isActive(c,null), p);
						continue nextConcept;
					}
					
					Concept thisTopLevel = topLevels.iterator().next();
					if (lastTopLevel == null) {
						lastTopLevel = thisTopLevel;
					} else if ( !lastTopLevel.equals(thisTopLevel)) {
						report(c, issue2Str, legacy, isActive(c,null), thisTopLevel, lastTopLevel);
						if (legacy.equals("Y")) {
							incrementSummaryInformation("Legacy Issues Reported");
						}	else {
							incrementSummaryInformation("Fresh Issues Reported");
						}
					}
				}
			}
		}
	}
	
	//RP-128
	private void axiomIntegrity() throws TermServerScriptException {
		String issueStr = "Axiom contains inactive type";
		String issue2Str = "Axiom contains inactive target";
		String issue3Str = "GCI Axiom contains inactive type";
		String issue4Str = "GCI Axiom contains inactive type";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		initialiseSummary(issue3Str);
		initialiseSummary(issue4Str);
		
		//Check all concepts referenced in relationships are valid
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				//Check all RHS relationships are active
				for (Relationship r : c.getRelationships()) {
					if (r.isActive()) {
						String legacy = isLegacy(r);
						if (!r.getType().isActive()) {
							report(c, issueStr, legacy, isActive(c,r), r);
						}
						if (!r.getTarget().isActive()) {
							report(c, issue2Str, legacy, isActive(c,r), r);
						}
					}
				}
				
				//Check all LHS relationships are active
				for (AxiomEntry a : c.getAxiomEntries()) {
					try {
						String legacy = isLegacy(a);
						AxiomRepresentation axiom = gl.getAxiomService().convertAxiomToRelationships(Long.parseLong(c.getConceptId()), a.getOwlExpression());
						//Things like property chains give us a null axiom
						if (axiom == null) {
							continue;
						}
						
						for (Relationship r : AxiomUtils.getLHSRelationships(c, axiom)) {
							if (!r.getType().isActive()) {
								report(c, issue3Str, legacy, isActive(c,r), r);
							}
							if (!r.getTarget().isActive()) {
								report(c, issue4Str, legacy, isActive(c,r), r);
							}
						}
					} catch (ConversionException e) {
						error ("Failed to convert: " + a, e);
					}
				}
			}
		}
	}
	
	//RP-127
	private void diseaseIntegrity() throws TermServerScriptException {
		String issueStr = "Clinical finding has disorder as ancestor ";
		String issue2Str = "Disorder is not descendant of 64572001|Disease (disorder)| ";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		//Rule 1 (clinical finding) concepts cannot have a (disorder) concept as a parent
		//Rule 2 All (disorder) concepts must be a descendant of 64572001|Disease (disorder)| 
		Set<Concept> diseases = DISEASE.getDescendents(NOT_SET);
		for (Concept c : CLINICAL_FINDING.getDescendents(NOT_SET)) {
			/*if (c.getConceptId().equals("300097006")) {
				debug("debug here");
			}*/
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			if (semTag.equals("(finding)")) {
				checkForAncestorSemTag(c, "(disorder)", issueStr);
			} else if (semTag.equals("(disorder)") && !diseases.contains(c)) {
				String legacy = isLegacy(c);
				report(c, issue2Str, legacy, isActive(c,null));
			}
		}
	}
	
	private void checkForAncestorSemTag(Concept c, String string, String issueStr) throws TermServerScriptException {
		Set<Concept> ancestors = c.getAncestors(NOT_SET);
		for (Concept ancestor : ancestors) {
			String semTag = SnomedUtils.deconstructFSN(ancestor.getFsn())[1];
			if (semTag.equals("(disorder)")) {
				String legacy = isLegacy(c);
				report(c, issueStr, legacy, isActive(c,null), ancestor);
				return;
			}
		}
	}
	
	//RP-165
	private void textDefinitionDialectChecks() throws TermServerScriptException {
		String issueStr = "Text Definition exists in one dialect and not the other";
		initialiseSummary(issueStr);
		
		List<Description> bothDialectTextDefns = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				List<Description> textDefns = c.getDescriptions(Acceptability.BOTH, DescriptionType.TEXT_DEFINITION, ActiveState.ACTIVE);
				if (textDefns.size() > 2) {
					warn (c + " has " + textDefns.size() + " active text definitions - check for compatibility");
				}
				boolean hasUS = false;
				boolean hasGB = false;
				for (Description textDefn : textDefns) {
					boolean isUS = false;
					boolean isGB = false;
					if (textDefn.isPreferred(US_ENG_LANG_REFSET)) {
						isUS = true;
						hasUS = true;
					}
					if (textDefn.isPreferred(GB_ENG_LANG_REFSET)) {
						isGB = true;
						hasGB = true;
					}
					if (isUS && isGB) {
						bothDialectTextDefns.add(textDefn);
					}
					if (!isUS && !isGB) {
						warn ("Text definition is not preferred in either dialect: " + textDefn);
					}
				}
				if ((hasUS && !hasGB) || (hasGB && !hasUS)) {
					String legacy = isLegacy(c);
					report(c, issueStr, legacy, isActive(c,null));
				}
			}
		}
		checkForUsGbSpecificSpelling(bothDialectTextDefns);
	}

	private void checkForUsGbSpecificSpelling(List<Description> bothDialectTextDefns) throws TermServerScriptException {
		String issueStr = "Text Definition acceptable in both dialects contains US specific spelling";
		String issue2Str = "Text Definition acceptable in both dialects contains GB specific spelling";
		initialiseSummary(issueStr);
		initialiseSummary(issue2Str);
		
		
		List<String> lines;
		debug ("Loading us/gb terms");
		try {
			lines = Files.readLines(new File("resources/us-to-gb-terms-map.txt"), Charsets.UTF_8);
		} catch (IOException e) {
			throw new TermServerScriptException("Unable to read resources/us-to-gb-terms-map.txt", e);
		}
		List<DialectPair> dialectPairs = lines.stream()
				.map(l -> new DialectPair(l))
				.collect(Collectors.toList());
		
		debug ("Checking " + bothDialectTextDefns.size() + " both-dialect text definitions against " + dialectPairs.size() + " dialect pairs");
		
		nextDescription:
		for (Description textDefn : bothDialectTextDefns) {
			String term = " " + textDefn.getTerm().toLowerCase().replaceAll("[^A-Za-z0-9]", " ");
			Concept c = gl.getConcept(textDefn.getConceptId());
			String legacy = isLegacy(c);
			for (DialectPair dialectPair : dialectPairs) {
				if (term.contains(dialectPair.usTerm)) {
					report(c, issueStr, legacy, isActive(c,null), dialectPair.usTerm, textDefn);
					continue nextDescription;
				}
				if (term.contains(dialectPair.gbTerm)) {
					report(c, issue2Str, legacy, isActive(c,null), dialectPair.gbTerm, textDefn);
					continue nextDescription;
				}
			}
		}
	}
	

	private void nestedBracketCheck() throws TermServerScriptException {
		String issueStr = "Active description on inactive concept contains nested brackets";
		initialiseSummary(issueStr);
		Character[][] bracketPairs = new Character[][] {{'(', ')'},
			{'[',']'}};
			
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			if (!c.isActive()) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					for (Character[] bracketPair : bracketPairs) {
						if (containsNestedBracket(c, d, bracketPair)) {
							report (c, issueStr, d);
							continue nextConcept;
						}
					}
				}
			}
		}
	}
	
	private boolean containsNestedBracket(Concept c, Description d, Character[] bracketPair) throws TermServerScriptException {
		Stack<Character> brackets = new Stack<>();
		for (Character ch: d.getTerm().toCharArray()) {
			if (ch.equals(bracketPair[0])) {  //Opening bracket
				brackets.push(ch);
				if (brackets.size() > 1) {
					return true;
				}
			} else if (ch.equals(bracketPair[1])) {  //Closing bracket
				if (brackets.size() == 0) {
					report (c,"Closing bracket found without matching opening",d);
				} else {
					brackets.pop();
				}
			}
		}
		return false;
	}
	

	private void validateAttributeDomainModellingRules() throws TermServerScriptException {
		String issueStr = "Concepts using |Surgical approach| must be subtypes of |surgical procedure|";
		initialiseSummary(issueStr);
		Concept type = gl.getConcept("424876005 |Surgical approach (attribute)|");
		Concept subHierarchy = gl.getConcept("387713003 |Surgical procedure (procedure)|");
		Set<Concept> subHierarchyList = cache.getDescendentsOrSelf(subHierarchy);
		
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				validateTypeUsedInDomain(c, type, subHierarchyList, issueStr);
			}
		}
	}

	/**
	 * Where a concept uses the specified attribute type in its modelling, 
	 * ensure that it is a descendant of the specified subhierarchy
	 * @throws TermServerScriptException 
	 */
	private void validateTypeUsedInDomain(Concept c, Concept type, Set<Concept> subHierarchyList, String issueStr) throws TermServerScriptException {
		if (SnomedUtils.hasType(CharacteristicType.INFERRED_RELATIONSHIP, c, type)) {
			if (!subHierarchyList.contains(c)) {
				report (c, issueStr);
			}
		}
	}

	private void validateAttributeTypeValueModellingRules() throws TermServerScriptException {
		String issueStr = "Finding/Procedure site cannot take a combined site value";
		initialiseSummary(issueStr);
		
		//RP-181 No finding or procedure site attribute should take a combined bodysite as the value
		List<Concept> typesOfInterest = new ArrayList<>();
		typesOfInterest.add(FINDING_SITE);
		Set<Concept> procSiteTypes = cache.getDescendentsOrSelf(gl.getConcept("363704007 |Procedure site (attribute)|"));
		typesOfInterest.addAll(procSiteTypes);
		Set<Concept> invalidValues = cache.getDescendentsOrSelf(gl.getConcept("116007004 |Combined site (body structure)|"));
		
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActive()) {
				for (Concept type : typesOfInterest) {
					validateTypeValueCombo(c, type, invalidValues, issueStr, false);
				}
			}
		}
	}

	/**
	 * If the given concept uses the particular type, checks if that type is in (or must not be in)
	 * the list of specified values
	 * @throws TermServerScriptException 
	 */
	private void validateTypeValueCombo(Concept c, Concept type, Set<Concept> values, String issueStr,
			boolean mustBeIn) throws TermServerScriptException {
		List<Relationship> relsWithType = c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, type, ActiveState.ACTIVE);
		for (Relationship relWithType : relsWithType) {
			//Must the value be in, or must the value be NOT in our list of values?
			boolean isIn = values.contains(relWithType.getTarget());
			if (!isIn == mustBeIn) {
				report (c, issueStr, relWithType);
			}
		}
	}
	

	private void validateDeprecatedHierarchies() throws TermServerScriptException {
		List<Concept> deprecatedHierarchies = new ArrayList<>();
		deprecatedHierarchies.add(gl.getConcept("116007004|Combined site (body structure)|"));
		for (Concept deprecatedHierarchy : deprecatedHierarchies) {
			String issueStr = "No new descendants allowed in " + deprecatedHierarchy;
			initialiseSummary(issueStr);
			for (Concept c : deprecatedHierarchy.getDescendents(NOT_SET)) {
				//No child can have a new parent relationship in this hierarchy
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE)) {
					if (r.getEffectiveTime() == null || r.getEffectiveTime().isEmpty() || !r.isReleased()) {
						report (c, issueStr, r);
					}
				}
			}
		}
	}

	protected void initialiseSummary(String issue) {
		issueSummaryMap.merge(issue, 0, Integer::sum);
	}
	
	protected void report (Concept c, Object...details) throws TermServerScriptException {
		//First detail is the issue
		issueSummaryMap.merge(details[0].toString(), 1, Integer::sum);
		countIssue(c);
		super.report (PRIMARY_REPORT, c, details);
	}

	private Object isActive(Component c1, Component c2) {
		return (c1.isActive() ? "Y":"N") + "/" + (c2 == null?"" : (c2.isActive() ? "Y":"N"));
	}

	private String isLegacy(Component c) {
		return c.getEffectiveTime() == null ? "N" : "Y";
	}
	
	class DialectPair {
		String usTerm;
		String gbTerm;
		DialectPair (String line) {
			String[] pair = line.split(TAB);
			//Wrap in spaces to ensure whole word matching
			usTerm = " " + pair[0] + " ";
			gbTerm = " " + pair[1] + " ";
		}
	}

}
