package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.termserver.job.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;

/**
 * FD19459 Reports all terms that contain the specified text
 * Optionally only report the first description matched for each concept
 * 
 * CTR-19 Match organism taxon
 * MAINT-224 Check for full stop in descriptions other than text definitions
 * SUBST-314 Converting to ReportClass and also list arbitrary attribute
 */
public class TermContainsXReport extends TermServerReport implements ReportClass {
	
	//String[] textsToMatch = new String[] {"remission", "diabet" };
	String[] textsToMatch;
	boolean reportConceptOnceOnly = true;
	public static final String WORDS = "Words";
	public static final String ATTRIBUTE_DETAIL = "AttributeDetail";
	Concept attributeDetail;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		Map<String, String> params = new HashMap<>();
		params.put(SUB_HIERARCHY, SUBSTANCE.toString());
		params.put("Words", "hydrochloride,mesilate,mesylate,maleate,anhydrous");
		params.put("AttributeDetail", "738774007 |Is modification of (attribute)|");
		TermServerReport.run(TermContainsXReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, TermMatched, MatchedIn, Case, AttributeDetail, SubHierarchy, SubSubHierarchy";
		super.init(run);
		getArchiveManager().populateHierarchyDepth = true;
		textsToMatch = run.getParameter(WORDS).split(COMMA);
		String attribStr = run.getParameter(ATTRIBUTE_DETAIL);
		if (attribStr != null && !attribStr.isEmpty()) {
			attributeDetail = gl.getConcept(attribStr);
		}
	}
	
	@Override
	public Job getJob() {
		String[] parameterNames = new String[] { "SubHierarchy", "Words", "AttributeDetail" };
		return new Job( new JobCategory(JobCategory.ADHOC_QUERIES),
						"Term contains X",
						"List all concept containing specified words, with optional attribute detail",
						parameterNames);
	}
	
	public void runJob() throws TermServerScriptException {
		nextConcept:
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			if (c.isActive()) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					boolean reported = false;
					for (String matchText : textsToMatch) {
						if (d.getTerm().toLowerCase().contains(matchText.toLowerCase())) {
							String[] hiearchies = getHierarchies(c);
							String cs = SnomedUtils.translateCaseSignificanceFromEnum(c.getFSNDescription().getCaseSignificance());
							report(c, matchText, d, cs, getAttributeDetail(c), hiearchies[1], hiearchies[2]);
							reported = true;
							incrementSummaryInformation("Matched " + matchText);
						}
					}
					if (reported && reportConceptOnceOnly) {
						continue nextConcept;
					}
				}
			}
		}
	}
	
	private String getAttributeDetail(Concept c) throws TermServerScriptException {
		if (attributeDetail != null) {
			return SnomedUtils.getTargets(c, new Concept[] {attributeDetail}, CharacteristicType.INFERRED_RELATIONSHIP)
					.stream()
					.map(Concept::toString)
					.collect(Collectors.joining(",\n"));
		}
		return "";
	}

	//Return hierarchy depths 1, 2, 3
	private String[] getHierarchies(Concept c) throws TermServerScriptException {
		String[] hierarchies = new String[3];
		Set<Concept> ancestors = c.getAncestors(NOT_SET);
		for (Concept ancestor : ancestors) {
			int depth = ancestor.getDepth();
			if (depth > 0 && depth < 4) {
				hierarchies[depth -1] = ancestor.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			}
		}
		return hierarchies;
	}

}
