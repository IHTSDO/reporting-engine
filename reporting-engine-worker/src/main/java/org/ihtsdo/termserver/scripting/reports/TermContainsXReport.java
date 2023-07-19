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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TermContainsXReport extends TermServerReport implements ReportClass {

	private static Logger LOGGER = LoggerFactory.getLogger(TermContainsXReport.class);

	String[] textsToMatch = null;
	String[] textsToAvoid = null;
	boolean reportConceptOnceOnly = true;
	public static final String STARTS_WITH = "Starts With";
	public static final String WHOLE_WORD = "Whole Word Only";
	public static final String WORDS = "Words";
	public static final String ATTRIBUTE_TYPE = "Attribute Type";
	public static final String WITHOUT = "Without";
	public static final String TERM_TYPES = "Term Type";
	public static final String WITHOUT_MODE = "Without Mode";
	public static final String ALL_WITHOUT = "All terms 'without'";
	public static final String ANY_WITHOUT = "Any terms 'without'";
	Concept attributeDetail;
	boolean startsWith = false;
	boolean wholeWord = false;
	private enum WithoutMode {ALL_WITHOUT, ANY_WITHOUT};
	private WithoutMode withoutMode;
	
	private List<String> targetTypes;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, Object> params = new HashMap<>();
		params.put(STARTS_WITH, "N");
		//params.put(ECL, "<< 372087000 |Primary malignant neoplasm (disorder)| ");
		params.put(ECL, "<< 363346000 |Malignant neoplastic disease (disorder)| MINUS (<< 372087000 |Primary malignant neoplasm (disorder)| OR << 269475001 |Malignant tumor of lymphoid, hemopoietic AND/OR related tissue (disorder)|)"); 
		//params.put(WORDS, "angiography, angiogram, arteriography, arteriogram");
		//params.put(WITHOUT, "fluoroscopic, fluoroscopy, computed tomography, CT, magnetic resonance, MR, MRA, MRI");
		//params.put(WITHOUT, "primary");
		params.put(WORDS, "primary");
		params.put(WITHOUT_MODE, ANY_WITHOUT);
		params.put(WHOLE_WORD, "false");
		params.put(ATTRIBUTE_TYPE, null);
		List<String> descTypes = new ArrayList<>();
		descTypes.add("FSN");
		descTypes.add("PT");
		descTypes.add("SYN");
		params.put(TERM_TYPES, descTypes);
		TermServerReport.run(TermContainsXReport.class, params, args);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns = "FSN, SemTag, Def Status, TermMatched, MatchedIn, Case, AttributeDetail, SubHierarchy, SubSubHierarchy";
		runStandAlone = false; //We need a proper path lookup for MS projects
		super.init(run);
		getArchiveManager().setPopulateHierarchyDepth(true);
		
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
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(STARTS_WITH).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(WHOLE_WORD).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(WORDS).withType(JobParameter.Type.STRING).withDescription("Use a comma to separate multiple words in an 'or' search")
				.add(WITHOUT).withType(JobParameter.Type.STRING).withDescription("Use a comma to separate multiple words in an 'or' search")
				.add(WITHOUT_MODE).withType(JobParameter.Type.DROPDOWN).withOptions(ALL_WITHOUT, ANY_WITHOUT).withDefaultValue(ALL_WITHOUT)
				.add(TERM_TYPES).withType(JobParameter.Type.CHECKBOXES).withOptions("FSN", "PT", "SYN", "DEFN").withDefaultValues("FSN","PT")
				.add(ATTRIBUTE_TYPE).withType(JobParameter.Type.CONCEPT).withDescription("Optional. Will show the attribute values per concept for the specified attribute type.  For example in Substances, show me all concepts that are used as a target for 738774007 |Is modification of (attribute)| by specifying that attribute type in this field.")
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Term contains X")
				.withDescription("This report lists all concepts containing the specified words, with optional attribute details.  Search for multiple words (in an either/or fashion) using a comma to separate.  'Without' words must not be present, again comma separated.  Note that PT and SYN are considered distinct, so SYN - in this context - means synonyms other than the Preferred Term.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		List<Concept> conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = new ArrayList<>(findConcepts(subsetECL));
		}
		conceptsOfInterest.sort(Comparator.comparing(Concept::getFsnSafely));
		
		nextConcept:
		for (Concept c : conceptsOfInterest) {
			boolean atLeastOneTermMatched = false;
			/*if (c.getId().equals("307651005")) {
				LOGGER.debug("Here");
			}*/
			if (c.isActive()) {
				if (whiteListedConceptIds.contains(c.getId())) {
					incrementSummaryInformation(WHITE_LISTED_COUNT);
					continue;
				}
				
				nextDescription:
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (!isTargetDescriptionType(d)) {
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
								atLeastOneTermMatched = true;
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
							reported = true;
						}
					}
				}
				
				if (textsToMatch == null && getJobRun().getParamValue(WITHOUT) != null && withoutMode == WithoutMode.ALL_WITHOUT) {
					String allTerms = c.getDescriptions(ActiveState.ACTIVE).stream()
							.filter(d -> isTargetDescriptionType(d))
							.sorted(SnomedUtils.decriptionPrioritiser)
							.map(d -> d.getTerm())
							.collect(Collectors.joining(",\n"));
					report(c, "No terms contained: " + getJobRun().getParamValue(WITHOUT), allTerms);
				}
			}
		}
	
		if (textsToMatch != null && Arrays.asList(textsToMatch).contains("TEST_WATCHER")) {
			LOGGER.info("Testing watcher functionality.  Pausing for 15 minutes");
			try {
				Thread.sleep(15 * 60 * 1000);
			} catch(Exception e) {
				LOGGER.info("Watcher Testing interrupted prematurely");
			}
		}
		
		if (textsToMatch != null && Arrays.asList(textsToMatch).contains("KILL_REPORT")) {
			throw new TermServerScriptException("'KILL_REPORT' instruction received.");
		}
	
	}
	
	protected boolean report (Concept c, Object...details) throws TermServerScriptException {
		String[] hiearchies = getHierarchies(c);
		String cs = SnomedUtils.translateCaseSignificanceFromEnum(c.getFSNDescription().getCaseSignificance());
		String ds = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		countIssue(c);
		return super.report(c, ds, details, cs, getAttributeDetail(c), hiearchies[1], hiearchies[2]);
	}
	
	private boolean isTargetDescriptionType(Description d) {
		if (d.getType().equals(DescriptionType.FSN) && targetTypes.contains("FSN")) {
			return true;
		}
		
		if (d.getType().equals(DescriptionType.SYNONYM) && d.isPreferred() && targetTypes.contains("PT")) {
			return true;
		}
		
		if (d.getType().equals(DescriptionType.SYNONYM) && !d.isPreferred() && targetTypes.contains("SYN")) {
			return true;
		}
		
		if (d.getType().equals(DescriptionType.TEXT_DEFINITION) && targetTypes.contains("DEFN")) {
			return true;
		}
		
		return false;
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
