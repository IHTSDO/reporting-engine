package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * RP-476 Report to search for particular concepts and then list where those
 * concepts are used as target values in defining relationships.
 * Optionally include descendants of the attributes found
 * Optionally just look at Stated Modeling (otherwise both)
 */
public class ConceptsUsedAsDefiningAttributes extends TermServerReport implements ReportClass {
	public static final String SELECT_CONCEPTS_ECL = "Select Concepts ECL";
	public static final String FILTER_CONCEPTS_REGEX = "Filter Concepts RegEx";	
	public static final String INCLUDE_DESCENDANTS = "Include Descendants";
	public static final String STATED_VIEW_ONLY = "Stated View Only";
	public static final String OUT_OF_SCOPE_ECL = "Out of Scope ECL";
	private boolean includeDescendants = true;
	private boolean statedViewOnly = true;
	private Pattern filterConceptsRegEx;
	private String outOfScopeECL;
	
	Collection<Concept> startingSet;  //We don't want to check our concepts in their own hierarchy
	Collection<Concept> outOfScope = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(SELECT_CONCEPTS_ECL, "<< 105590001 |Substance (substance)|");
		params.put(OUT_OF_SCOPE_ECL, "<< 373873005 |Pharmaceutical / biologic product (product)|");
		// https://regex101.com/r/Okwd83/1
		params.put(FILTER_CONCEPTS_REGEX, "(and|or|and\\/or).*?(derivative|compound)");
		params.put(INCLUDE_DESCENDANTS, "true");
		params.put(STATED_VIEW_ONLY, "false");
		TermServerReport.run(ConceptsUsedAsDefiningAttributes.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		subsetECL = run.getMandatoryParamValue(SELECT_CONCEPTS_ECL);
		outOfScopeECL = run.getMandatoryParamValue(OUT_OF_SCOPE_ECL);
		includeDescendants = run.getParameters().getMandatoryBoolean(INCLUDE_DESCENDANTS);
		statedViewOnly = run.getParameters().getMandatoryBoolean(STATED_VIEW_ONLY);
		String filterConceptsRegExStr = run.getParamValue(FILTER_CONCEPTS_REGEX);
		if (!StringUtils.isEmpty(filterConceptsRegExStr)) {
			filterConceptsRegEx = Pattern.compile(filterConceptsRegExStr,Pattern.CASE_INSENSITIVE);
		}
		
		additionalReportColumns = "FSN,SemTag,DefStatus,SCT Expression,Target Property Present";
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] { 
				"SCTID, FSN, SemTag, Type, Target",
				"SCTID, FSN, Semtag, Descendent Of"
		};
		String[] tabNames = new String[] {	
				"Concepts using Attribute Values",
				"Attributes of Interest"
		};
		super.postInit(tabNames, columnHeadings, false);
		
		if (!StringUtils.isEmpty(outOfScopeECL)) {
			outOfScope = findConcepts(outOfScopeECL);
		}
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SELECT_CONCEPTS_ECL).withType(JobParameter.Type.ECL).withMandatory()
				.add(FILTER_CONCEPTS_REGEX).withType(JobParameter.Type.STRING)
				.add(INCLUDE_DESCENDANTS).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(true)
				.add(STATED_VIEW_ONLY).withType(JobParameter.Type.BOOLEAN).withMandatory().withDefaultValue(false)
				.add(OUT_OF_SCOPE_ECL).withType(JobParameter.Type.ECL).withMandatory()
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Concepts Used As Defining Attributes")
				.withDescription("This report selects a set of concepts based on ECL and RegEx and then " +
						"lists the concepts that use these concepts (and optionally their descendants) as defining attributes")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//First find the concepts that we're working with here
		List<Concept> attributeValuesOfInterest = getAttributeValuesOfInterest();
		info("Collected " + attributeValuesOfInterest.size() + " attribute values of interest");
		Set<Concept> reported = new HashSet<>();
		List<Concept> activeConcepts = gl.getAllConcepts().stream()
				.filter(Concept::isActive)
				.sorted(Comparator.comparing(Concept::getFsn))
				.collect(Collectors.toList());
		for (Concept c : activeConcepts) {
			reported.clear();
			if (startingSet.contains(c) || outOfScope.contains(c)) {
				continue;
			}
			
			CharacteristicType charType = statedViewOnly ? CharacteristicType.STATED_RELATIONSHIP : CharacteristicType.ALL ;
			for (Relationship r : c.getRelationships(charType, ActiveState.ACTIVE)) {
				if (r.getType().equals(IS_A)) {
					continue;
				}
				Concept target = r.getTarget();
				if (!reported.contains(target) && attributeValuesOfInterest.contains(target)) {
					report (c, r.getType(), target);
					reported.add(target);
				}
			}
		}
	}

	private List<Concept> getAttributeValuesOfInterest() throws TermServerScriptException {
		List<Concept> conceptsOfInterest = new ArrayList<>();
		startingSet = findConcepts(subsetECL);
		for (Concept c : startingSet) {
			if (filterConceptsRegEx == null || filterConceptsRegEx.matcher(c.getFsn()).find()) {
				conceptsOfInterest.add(c);
			}
		}
		conceptsOfInterest.sort(Comparator.comparing(Concept::getFsn));
		
		//Lets list these in tab 2.  We'll add as we go along, so copy the initial list
		for (Concept c : new ArrayList<>(conceptsOfInterest)) {
			report (SECONDARY_REPORT, c);
			//Are we including descendants?
			if (includeDescendants) {
				List<Concept> descendants = c.getDescendents(NOT_SET).stream()
						.filter(d -> !conceptsOfInterest.contains(d))
						.sorted(Comparator.comparing(Concept::getFsn))
						.collect(Collectors.toList());
				for (Concept descendant : descendants) {
					report (SECONDARY_REPORT, descendant, c);
					conceptsOfInterest.add(descendant);
				}
			}
			
		}
		//Sort again since we added more items
		conceptsOfInterest.sort(Comparator.comparing(Concept::getFsn));
		return conceptsOfInterest;
	}

}
