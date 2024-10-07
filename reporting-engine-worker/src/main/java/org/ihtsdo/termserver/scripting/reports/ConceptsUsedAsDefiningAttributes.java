package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RP-476 Report to search for particular concepts and then list where those
 * concepts are used as target values in defining relationships.
 * Optionally just look at Stated Modeling (otherwise both)
 */
public class ConceptsUsedAsDefiningAttributes extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConceptsUsedAsDefiningAttributes.class);

	private static final int MAX_ATTRIBUTE_VALUES = 80000;
	public static final String SELECT_CONCEPTS_ECL = "Select Concepts ECL";
	public static final String FILTER_CONCEPTS_REGEX = "Filter Concepts RegEx";	
	public static final String STATED_VIEW_ONLY = "Stated View Only";
	public static final String OUT_OF_SCOPE_ECL = "Out of Scope ECL";
	private boolean statedViewOnly = true;
	private Pattern filterConceptsRegEx;
	private String outOfScopeECL;
	
	Collection<Concept> startingSet;  //We don't want to check our concepts in their own hierarchy
	Collection<Concept> outOfScope = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(SELECT_CONCEPTS_ECL, "<< 105590001 |Substance (substance)|");
		params.put(OUT_OF_SCOPE_ECL, "<< 373873005 |Pharmaceutical / biologic product (product)|");
		// https://regex101.com/r/Okwd83/1
		params.put(FILTER_CONCEPTS_REGEX, "(and|or|and\\/or).*?(derivative|compound)");
		params.put(STATED_VIEW_ONLY, "false");
		TermServerScript.run(ConceptsUsedAsDefiningAttributes.class, args, params);
	}
	
	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		subsetECL = run.getMandatoryParamValue(SELECT_CONCEPTS_ECL);
		outOfScopeECL = run.getParamValue(OUT_OF_SCOPE_ECL);
		statedViewOnly = run.getParameters().getMandatoryBoolean(STATED_VIEW_ONLY);
		String filterConceptsRegExStr = run.getParamValue(FILTER_CONCEPTS_REGEX);
		if (!StringUtils.isEmpty(filterConceptsRegExStr)) {
			filterConceptsRegEx = Pattern.compile(filterConceptsRegExStr,Pattern.CASE_INSENSITIVE);
		}
		
		additionalReportColumns = "FSN,SemTag,DefStatus,SCT Expression,Target Property Present";
		super.init(run);
	}
	
	@Override
	public void postInit() throws TermServerScriptException {
		String tab1Heading = "SCTID, FSN, SemTag, Type, Target";
		if (!statedViewOnly) {
			tab1Heading = "SCTID, FSN, SemTag, Inferred Only, Type, Target";
		}
		String[] columnHeadings = new String[] { 
				tab1Heading,
		};
		String[] tabNames = new String[] {	
				"Concepts using Attribute Values",
		};
		super.postInit(tabNames, columnHeadings);
		
		if (!StringUtils.isEmpty(outOfScopeECL)) {
			outOfScope = findConcepts(outOfScopeECL);
		}
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SELECT_CONCEPTS_ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(FILTER_CONCEPTS_REGEX).withType(JobParameter.Type.STRING)
				.add(STATED_VIEW_ONLY).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(OUT_OF_SCOPE_ECL).withType(JobParameter.Type.ECL)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Concepts Used As Defining Attributes")
				.withDescription("This report selects a set of concepts based on ECL and RegEx and then " +
						"lists the concepts that use these concepts as defining attributes")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	@Override
	public void runJob() throws TermServerScriptException {
		//First find the concepts that we're working with here
		List<Concept> attributeValuesOfInterest = getAttributeValuesOfInterest();
		LOGGER.info("Collected {} attribute values of interest", attributeValuesOfInterest.size());
		List<Concept> activeConcepts = SnomedUtils.sort(gl.getAllConcepts());
		for (Concept c : activeConcepts) {
			if (startingSet.contains(c) || outOfScope.contains(c)) {
				continue;
			}
			checkConceptForAttributeValueOfInterest(c, attributeValuesOfInterest);
		}
	}

	private void checkConceptForAttributeValueOfInterest(Concept c, List<Concept> attributeValuesOfInterest) throws TermServerScriptException {
		Set<Concept> reported = new HashSet<>();
		CharacteristicType charType = statedViewOnly ? CharacteristicType.STATED_RELATIONSHIP : CharacteristicType.ALL ;
		for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
			if (r.getType().equals(IS_A)) {
				continue;
			}
			Concept target = r.getTarget();
			if (!reported.contains(target) && attributeValuesOfInterest.contains(target)) {
				if (statedViewOnly) {
					report(c, r.getType(), target);
					countIssue(c);
				} else {
					boolean inferredOnly = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, r.getType(), r.getTarget(), ActiveState.ACTIVE).isEmpty();
					report(c, inferredOnly?"Y":"N", r.getType(), target);
					countIssue(c);
				}
				reported.add(target);
			}
		}
	}

	private List<Concept> getAttributeValuesOfInterest() throws TermServerScriptException {
		List<Concept> conceptsOfInterest = new ArrayList<>();
		for (Concept c : findConcepts(subsetECL)) {
			if (filterConceptsRegEx == null || filterConceptsRegEx.matcher(c.getFsn()).find()) {
				conceptsOfInterest.add(c);
			}
		}
		
		if (conceptsOfInterest.size() > MAX_ATTRIBUTE_VALUES) {
			throw new TermServerScriptException("Too many concepts of interest (" + conceptsOfInterest.size()
					+ ") found, max is 80k.  Please refine your search.");
		}
		
		conceptsOfInterest.sort(Comparator.comparing(Concept::getFsn));
		return conceptsOfInterest;
	}

}
