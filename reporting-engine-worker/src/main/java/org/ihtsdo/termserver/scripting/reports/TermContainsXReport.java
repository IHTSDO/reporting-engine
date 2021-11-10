package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

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
	String[] textsToAvoid = null;
	boolean reportConceptOnceOnly = true;
	public static final String STARTS_WITH = "Starts With";
	public static final String WHOLE_WORD = "Whole Word Only";
	public static final String WORDS = "Words";
	public static final String ATTRIBUTE_TYPE = "Attribute Type";
	public static final String WITHOUT = "Without";
	Concept attributeDetail;
	boolean startsWith = false;
	boolean wholeWord = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(STARTS_WITH, "N");
		params.put(SUB_HIERARCHY, ROOT_CONCEPT.toString());
		params.put(WORDS, "angiography, angiogram, arteriography, arteriogram");
		params.put(WITHOUT, "fluoroscopic, fluoroscopy, computed tomography, CT, magnetic resonance, MR, MRA, MRI");
		params.put(WHOLE_WORD, "false");
		params.put(ATTRIBUTE_TYPE, null);
		TermServerReport.run(TermContainsXReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, Def Status, TermMatched, MatchedIn, Case, AttributeDetail, SubHierarchy, SubSubHierarchy";
		runStandAlone = false; //We need a proper path lookup for MS projects
		super.init(run);
		getArchiveManager().setPopulateHierarchyDepth(true);
		textsToMatch = run.getMandatoryParamValue(WORDS).split(COMMA);
		for (int i=0; i < textsToMatch.length; i++) {
			textsToMatch[i] = textsToMatch[i].toLowerCase().trim();
		}
		
		if (!StringUtils.isEmpty(run.getParamValue(WITHOUT))) {
			textsToAvoid = run.getMandatoryParamValue(WITHOUT).toLowerCase().split(COMMA);
			for (int i=0; i < textsToAvoid.length; i++) {
				textsToAvoid[i] = textsToAvoid[i].toLowerCase().trim();
			}
		}
		
		String attribStr = run.getParamValue(ATTRIBUTE_TYPE);
		if (attribStr != null && !attribStr.isEmpty()) {
			attributeDetail = gl.getConcept(attribStr);
		}
		
		startsWith = run.getParameters().getMandatoryBoolean(STARTS_WITH);
		wholeWord = run.getParameters().getMandatoryBoolean(WHOLE_WORD);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SUB_HIERARCHY).withType(JobParameter.Type.CONCEPT).withDefaultValue(ROOT_CONCEPT)
				.add(STARTS_WITH).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(WHOLE_WORD).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(WORDS).withType(JobParameter.Type.STRING).withMandatory().withDescription("Use a comma to separate multiple words in an 'or' search")
				.add(WITHOUT).withType(JobParameter.Type.STRING).withDescription("Use a comma to separate multiple words in an 'or' search")
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withDescription("Optional. Will show the attribute values per concept for the specified attribute type.  For example in Substances, show me all concepts that are used as a target for 738774007 |Is modification of (attribute)| by specifying that attribute type in this field.")
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Term contains X")
				.withDescription("This report lists all concepts containing the specified words, with optional attribute details.  Search for multiple words (in an either/or fashion) using a comma to separate.  'Without' words must not be present, again comma separated")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest = gl.getAllConcepts();
		if (subHierarchy != null) {
			conceptsOfInterest = subHierarchy.getDescendents(NOT_SET);
		}
		
		nextConcept:
		for (Concept c : conceptsOfInterest) {
			if (c.isActive()) {
				if (whiteListedConcepts.contains(c)) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				
				nextDescription:
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					boolean reported = false;
					String term = d.getTerm().toLowerCase();
					String altTerm = term;
					if (wholeWord) {
						term = " " + term + " ";
						altTerm = term.replaceAll("[^A-Za-z0-9]", " ");
					}
					for (String matchText : textsToMatch) {
						if (textsToAvoid != null) {
							for (String avoidText : textsToAvoid) {
								if (term.contains(avoidText)) {
									continue nextDescription;
								}
							}
						}
						if (wholeWord) {
							matchText = " " + matchText + " ";
						}
						if ( (!startsWith && (term.contains(matchText) || altTerm.contains(matchText))) ||
								(startsWith && (term.startsWith(matchText) || altTerm.startsWith(matchText)))) {
							String[] hiearchies = getHierarchies(c);
							String cs = SnomedUtils.translateCaseSignificanceFromEnum(c.getFSNDescription().getCaseSignificance());
							String ds = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
							report(c, ds, matchText, d, cs, getAttributeDetail(c), hiearchies[1], hiearchies[2]);
							reported = true;
							incrementSummaryInformation("Matched '" + matchText.trim() + "'");
							countIssue(c);
						}
					}
					if (reported && reportConceptOnceOnly) {
						continue nextConcept;
					}
				}
			}
		}
	
		if (Arrays.asList(textsToMatch).contains("TEST_WATCHER")) {
			info("Testing watcher functionality.  Pausing for 15 minutes");
			try {
				Thread.sleep(15 * 60 * 1000);
			} catch(Exception e) {
				info("Watcher Testing interrupted prematurely");
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
