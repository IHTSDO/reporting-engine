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
	
	String[] textsToMatch;
	boolean reportConceptOnceOnly = true;
	public static final String STARTS_WITH = "Starts With";
	public static final String WORDS = "Words";
	public static final String ATTRIBUTE_TYPE = "Attribute Type";
	Concept attributeDetail;
	boolean startsWith = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException {
		Map<String, String> params = new HashMap<>();
		params.put(STARTS_WITH, "Y");
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(WORDS, "[");
		params.put(ATTRIBUTE_TYPE, null);
		TermServerReport.run(TermContainsXReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, Def Status, TermMatched, MatchedIn, Case, AttributeDetail, SubHierarchy, SubSubHierarchy";
		super.init(run);
		getArchiveManager().populateHierarchyDepth = true;
		textsToMatch = run.getMandatoryParamValue(WORDS).split(COMMA);
		
		String attribStr = run.getParamValue(ATTRIBUTE_TYPE);
		if (attribStr != null && !attribStr.isEmpty()) {
			attributeDetail = gl.getConcept(attribStr);
		}
		
		String startsWithStr = run.getMandatoryParamValue(STARTS_WITH);
		if (startsWithStr != null && startsWithStr.toUpperCase().equals("Y")) {
			startsWith = true;
		}
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withDefaultValue(ROOT_CONCEPT)
				.add(STARTS_WITH).withType(JobParameter.Type.BOOLEAN).withMandatory()
				.add(WORDS).withType(JobParameter.Type.STRING).withMandatory()
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withDescription("Optional. Will show the attribute values per concept for the specified attribute type.  For example in Substances, show me all concepts that are used as a target for 738774007 |Is modification of (attribute)| by specifying that attribute type in this field.")
				.build();
		return new Job( new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES),
						"Term contains X",
						"This report lists all concepts containing the specified words, with optional attribute details. ",
						params);
	}
	
	public void runJob() throws TermServerScriptException {
		nextConcept:
		for (Concept c : subHierarchy.getDescendents(NOT_SET)) {
			if (c.isActive()) {
				if (whiteListedConcepts.contains(c)) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					boolean reported = false;
					for (String matchText : textsToMatch) {
						if ( (!startsWith && d.getTerm().toLowerCase().contains(matchText.toLowerCase())) ||
								(startsWith && d.getTerm().toLowerCase().startsWith(matchText.toLowerCase()))) {
							String[] hiearchies = getHierarchies(c);
							String cs = SnomedUtils.translateCaseSignificanceFromEnum(c.getFSNDescription().getCaseSignificance());
							String ds = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
							report(c, ds, matchText, d, cs, getAttributeDetail(c), hiearchies[1], hiearchies[2]);
							reported = true;
							incrementSummaryInformation("Matched " + matchText);
							incrementSummaryInformation(ISSUE_COUNT);
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
