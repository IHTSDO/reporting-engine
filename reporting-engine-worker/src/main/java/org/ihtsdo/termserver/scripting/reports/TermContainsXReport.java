package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TermContainsXReport extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(TermContainsXReport.class);

	private static final String TRUE = "true";
	private static final String FALSE = "false";

	private String[] textsToMatch = null;
	private String[] textsToAvoid = null;
	private boolean reportConceptOnceOnly = true;
	public static final String EXT_ONLY = "Extension Descriptions Only";
	public static final String STARTS_WITH = "Starts With";
	public static final String WHOLE_WORD = "Whole Word Only";
	public static final String WORDS = "Words";
	public static final String ATTRIBUTE_TYPE = "Attribute Type";
	public static final String WITHOUT = "Without";
	public static final String TERM_TYPES = "Term Type";
	public static final String WITHOUT_MODE = "Without Mode";
	public static final String ALL_WITHOUT = "All terms 'without'";
	public static final String ANY_WITHOUT = "Any terms 'without'";
	private Concept attributeDetail;
	private boolean startsWith = false;
	private boolean wholeWord = false;
	private boolean extensionDescriptionsOnly = false;
	private enum WithoutMode {ALL_WITHOUT, ANY_WITHOUT}
	private WithoutMode withoutMode;
	
	private List<String> targetTypes;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, Object> params = new HashMap<>();
		params.put(STARTS_WITH, "N");
		params.put(ECL, "<< 363346000 |Malignant neoplastic disease (disorder)| MINUS (<< 372087000 |Primary malignant neoplasm (disorder)| OR << 269475001 |Malignant tumor of lymphoid, hemopoietic AND/OR related tissue (disorder)|)"); 
		params.put(WORDS, "primary");
		params.put(WITHOUT_MODE, ANY_WITHOUT);
		params.put(WHOLE_WORD, FALSE);
		params.put(ATTRIBUTE_TYPE, null);
		List<String> descTypes = new ArrayList<>();
		descTypes.add("FSN");
		descTypes.add("PT");
		descTypes.add("SYN");
		params.put(TERM_TYPES, descTypes);
		params.put(EXT_ONLY, FALSE);
		TermServerScript.run(TermContainsXReport.class, params, args);
	}
	
	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId(GFOLDER_ADHOC_REPORTS);
		additionalReportColumns = "FSN, SemTag, Def Status, TermMatched, MatchedIn, Case, AttributeDetail, SubHierarchy, SubSubHierarchy";
		runStandAlone = false; //We need a proper path lookup for MS projects
		super.init(run);
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		
		if (!StringUtils.isEmpty(run.getParamValue(WORDS))) {
			textsToMatch = run.getParamValue(WORDS).split(COMMA);
			for (int i=0; i < textsToMatch.length; i++) {
				textsToMatch[i] = textsToMatch[i].toLowerCase().trim();
			}
			
			if (run.getParamValue(WORDS).contains("FAIL_LARGE")) {
				throw new TermServerScriptException("Deliberate fail with long message: " + StringUtils.repeat('A', 65100));
			}
		}
		
		if (!StringUtils.isEmpty(run.getParamValue(WITHOUT))) {
			textsToAvoid = run.getParamValue(WITHOUT).toLowerCase().split(COMMA);
			for (int i=0; i < textsToAvoid.length; i++) {
				textsToAvoid[i] = textsToAvoid[i].toLowerCase().trim();
			}
		}
		
		if (!StringUtils.isEmpty(run.getParamValue(WITHOUT_MODE))) {
			String withoutModeStr = run.getParamValue(WITHOUT_MODE);
			switch (withoutModeStr) {
				case ALL_WITHOUT : withoutMode = WithoutMode.ALL_WITHOUT;
						break;
				case ANY_WITHOUT : withoutMode = WithoutMode.ANY_WITHOUT;
						break;
				default: throw new IllegalArgumentException("Did not recognise 'Without Mode': " + withoutModeStr);
			}
		}
		
		String attribStr = run.getParamValue(ATTRIBUTE_TYPE);
		if (attribStr != null && !attribStr.isEmpty()) {
			attributeDetail = gl.getConcept(attribStr);
		}
		
		startsWith = run.getParameters().getMandatoryBoolean(STARTS_WITH);
		wholeWord = run.getParameters().getMandatoryBoolean(WHOLE_WORD);
		targetTypes = run.getParameters().getValues(TERM_TYPES);
		extensionDescriptionsOnly = run.getParameters().getMandatoryBoolean(EXT_ONLY);

		//Makes no sense to run extension descriptions only in a core project
		if (extensionDescriptionsOnly && !project.getBranchPath().contains("SNOMEDCT-")) {
			throw new IllegalArgumentException("Extension Descriptions Only flag is not compatible with core projects");
		}
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(EXT_ONLY).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(STARTS_WITH).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(WHOLE_WORD).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(WORDS).withType(JobParameter.Type.STRING).withDescription("Use a comma to separate multiple words in an 'or' search")
				.add(WITHOUT).withType(JobParameter.Type.STRING).withDescription("Use a comma to separate multiple words in an 'or' search")
				.add(WITHOUT_MODE).withType(JobParameter.Type.DROPDOWN).withOptions(ALL_WITHOUT, ANY_WITHOUT).withDefaultValue(ALL_WITHOUT)
				.add(TERM_TYPES).withType(JobParameter.Type.CHECKBOXES).withOptions("FSN", "PT", "SYN", "DEFN").withDefaultValues(TRUE, TRUE,FALSE,FALSE)
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withDescription("Optional. Will show the attribute values per concept for the specified attribute type.  For example in Substances, show me all concepts that are used as a target for 738774007 |Is modification of (attribute)| by specifying that attribute type in this field.")
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Term contains X")
				.withDescription("This report lists all concepts containing the specified words, with optional attribute details.  Search for multiple words (in an either/or fashion) using a comma to separate.  'Without' words must not be present, again comma separated.  Note that PT and SYN are considered distinct, so SYN - in this context - means synonyms other than the Preferred Term.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	@Override
	public void runJob() throws TermServerScriptException {
		List<Concept> conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = new ArrayList<>(findConcepts(subsetECL));
		}
		conceptsOfInterest.sort(Comparator.comparing(Concept::getFsnSafely));
		
		nextConcept:
		for (Concept c : conceptsOfInterest) {
			if (c.isActiveSafely()) {
				if (whiteListedConceptIds.contains(c.getId())) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				
				nextDescription:
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (!SnomedUtils.isTargetDescriptionType(targetTypes, d)) {
						continue;
					}
					if (extensionDescriptionsOnly && !inScope(d)) {
						continue;
					}
					boolean reported = false;
					String term = d.getTerm().toLowerCase();
					String altTerm = term;
					if (wholeWord) {
						term = " " + term + " ";
						altTerm = term.replaceAll("[^A-Za-z0-9]", " ");
					}
					
					if (textsToMatch!= null && textsToMatch.length > 0) {
						for (String matchText : textsToMatch) {
							if (textsToAvoid != null) {
								for (String avoidText : textsToAvoid) {
									if (term.contains(avoidText)) {
										if (withoutMode == WithoutMode.ALL_WITHOUT) {
											continue nextConcept;
										} else {
											continue nextDescription;
										}
									}
								}
							}
							if (wholeWord) {
								matchText = " " + matchText + " ";
							}
							if ( (!startsWith && (term.contains(matchText) || altTerm.contains(matchText))) ||
									(startsWith && (term.startsWith(matchText) || altTerm.startsWith(matchText)))) {
								report(c,matchText, d);
								reported = true;
								incrementSummaryInformation("Matched '" + matchText.trim() + "'");
							}
						}
						if (reported && reportConceptOnceOnly) {
							continue nextConcept;
						}
					} else {
						// Are we just looking for terms that don't contain some string
						if (textsToAvoid != null) {
							for (String avoidText : textsToAvoid) {
								if (term.contains(avoidText)) {
									if (withoutMode == WithoutMode.ALL_WITHOUT) {
										continue nextConcept;
									} else {
										continue nextDescription;
									}
								}
							}
						}
						
						if (getJobRun().getParamValue(WITHOUT) != null && withoutMode == WithoutMode.ANY_WITHOUT) {
							report(c, "Did not contain: " + getJobRun().getParamValue(WITHOUT), d);
						}
					}
				}
				
				if (textsToMatch == null && getJobRun().getParamValue(WITHOUT) != null && withoutMode == WithoutMode.ALL_WITHOUT) {
					String allTerms = SnomedUtils.getDescriptionsOfType(c, targetTypes);
					report(c, "No terms contained: " + getJobRun().getParamValue(WITHOUT), allTerms);
				}
			}
		}
	
		if (textsToMatch != null && Arrays.asList(textsToMatch).contains("TEST_WATCHER")) {
			LOGGER.info("Testing watcher functionality.  Pausing for 15 minutes");
			try {
				Thread.sleep(15 * 60 * 1000L);
			} catch(Exception e) {
				Thread.currentThread().interrupt();
				LOGGER.info("Watcher Testing interrupted prematurely");
			}
		}
		
		if (textsToMatch != null && Arrays.asList(textsToMatch).contains("KILL_REPORT")) {
			throw new TermServerScriptException("'KILL_REPORT' instruction received.");
		}
	
	}
	
	@Override
	protected boolean report(Concept c, Object...details) throws TermServerScriptException {
		String[] hiearchies = getHierarchies(c);
		String cs = SnomedUtils.translateCaseSignificanceFromEnum(c.getFSNDescription().getCaseSignificance());
		String ds = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		countIssue(c);
		return super.report(c, ds, details, cs, getAttributeDetail(c), hiearchies[1], hiearchies[2]);
	}
	
	private String getAttributeDetail(Concept c) {
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
