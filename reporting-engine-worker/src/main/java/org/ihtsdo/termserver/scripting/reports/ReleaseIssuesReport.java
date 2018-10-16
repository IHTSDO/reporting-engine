package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.util.StringUtils;

/**
 * INFRA-2723 Detect various possible issues
 * 
 * https://docs.google.com/spreadsheets/d/1jrCR_VOZ6k7qBwDAhTqbt67iisbm_rThV7vt37lr_Rg/edit#gid=0
 */
public class ReleaseIssuesReport extends TermServerReport implements ReportClass {
	
	Concept subHierarchy = ROOT_CONCEPT;
	private static final String FULL_STOP = ".";
	String[] knownAbbrevs = new String[] {	"ser","ss","subsp",
											"f","E", "var", "St"};
	char NBSP = 255;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		TermServerReport.run(ReleaseIssuesReport.class, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
		super.init(run);
		additionalReportColumns = "FSN, Semtag, Issue, Legacy, C/D/R Active, Detail";
	}

	@Override
	public Job getJob() {
		String[] parameterNames = new String[] { };
		return new Job( new JobCategory(JobCategory.RELEASE_VALIDATION),
						"Release Issues",
						"Lists a range of issues identified as per INFRA-2723",
						parameterNames);
	}

	public void runJob() throws TermServerScriptException {
		unexpectedDescriptionModules();
		unexpectedRelationshipModules();
		fullStopInSynonym();
		inactiveMissingFSN_PT();
		duplicateSemanticTags();
		nonBreakingSpace();
	}

	// ISRS-391 Descriptions whose module id does not match that of the component
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
	
	// ISRS-392 Stated Relationships whose module id does not match that of the component
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
			if (c.isActive()) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					//We're only working on descriptions modified in the current release
					if (d.getEffectiveTime() != null) {
						continue;
					}
					if (d.getTerm().contains(FULL_STOP) && !allowableFullStop(d.getTerm())) {
						report(c, "Possible TextDefn as Synonym",isLegacy(d), isActive(c,d), d);
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
		
		//First pass through all concepts to find semantic tags
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
		
		//Second pass to see if we have any of these remaining once
		//the real semantic tag (last set of brackets) has been removed
		for (Concept c : gl.getAllConcepts()) {
			if (c.getFSNDescription() == null) {
				warn("No FSN Description found (2nd pass) for concept " + c.getConceptId());
				incrementSummaryInformation(ISSUE_COUNT);  //We'll only flag up fresh issues
				continue;
			}
			String legacy = isLegacy(c.getFSNDescription());
			String termWithoutTag = SnomedUtils.deconstructFSN(c.getFsn())[0];
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
				if (d.getTerm().indexOf(NBSP) != NOT_SET) {
					String msg = "Multiple semantic tags";
					String legacy = isLegacy(d);
					report(c, "Non-breaking space",legacy, isActive(c,d), msg, d);
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
	
	private Object isActive(Component c1, Component c2) {
		return (c1.isActive() ? "Y":"N") + "/" + (c2 == null?"" : (c2.isActive() ? "Y":"N"));
	}

	private String isLegacy(Component c) {
		return c.getEffectiveTime() == null ? "N" : "Y";
	}

	private boolean allowableFullStop(String term) {
		//Work through all full stops in the term
		int index = term.indexOf(FULL_STOP);
		while (index >= 0) {
			boolean thisStopOK = false;
			//If the character after the full stop is a number, that's fine
			if (term.length() > index + 1 && Character.isDigit(term.charAt(index+1))) {
				thisStopOK = true;
			} else {
				for (String thisAbbrev : knownAbbrevs) {
					if ((index - thisAbbrev.length()) >= 0 && term.substring(index - thisAbbrev.length(), index).equals(thisAbbrev)) {
						thisStopOK = true;
						break;
					}
				}
			}
			
			if (thisStopOK) {
				index = term.indexOf(FULL_STOP, index + 1);
				continue;
			} else {
				return false;
			}
		}
		return true;
	}
}
