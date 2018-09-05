package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.io.PrintStream;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;

/**
 * INFRA-2723 Detect various possible issues
 * 
 * https://docs.google.com/spreadsheets/d/1jrCR_VOZ6k7qBwDAhTqbt67iisbm_rThV7vt37lr_Rg/edit#gid=0
 */
public class ReleaseIssuesReport extends TermServerReport {
	
	Concept subHierarchy = ROOT_CONCEPT;
	private static final String FULL_STOP = ".";
	String[] knownAbbrevs = new String[] {	"ser","ss","subsp",
											"f","E", "var", "St"};
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		ReleaseIssuesReport report = new ReleaseIssuesReport();
		try {
			ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA
			report.additionalReportColumns = "FSN, Issue, Legacy, C/D/R Active, Detail";
			report.init(args);
			report.loadProjectSnapshot(false);  
			report.unexpectedDescriptionModules();
			report.unexpectedRelationshipModules();
			report.fullStopInSynonym();
			report.inactiveMissingFSN_PT();
		} catch (Exception e) {
			info("Failed to produce Description Report due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			report.finish();
		}
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
					}
				}
			}
		}
	}
	
	//MAINT-224 Synonyms created as TextDefinitions new content only
	private void fullStopInSynonym() throws TermServerScriptException {
		for (Concept c : ROOT_CONCEPT.getDescendents(NOT_SET)) {
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
