package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class ConceptsWithParents extends TermServerReport implements ReportClass {

	private static final String TERMS_FILTER = "Filter for terms";
	private static final String INDENT = "Indent";
	private static final String EXAMPLE_MAIN_PARAM_ECL = "<< 1296758008";
	private static final String EXAMPLE_MAIN_PARAM_TERMS_FILTER = null;
	private static final Pattern CONTAINS_TERM_PATTERN = Pattern.compile("\\{\\{\\s*term", Pattern.MULTILINE);
	public static final String COMMA_NEWLINE_DELIMITER = ",\n";

	private boolean indent = false;
	private Collection<Concept> conceptsOfInterest;
	private Set<Concept> conceptsReported = new HashSet<>();
	int deepestLevel = NOT_SET;
	int highestLevel = NOT_SET;

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, EXAMPLE_MAIN_PARAM_ECL);
		params.put(TERMS_FILTER, EXAMPLE_MAIN_PARAM_TERMS_FILTER);
		params.put(INDENT, "true");
		TermServerScript.run(ConceptsWithParents.class, args, params);
	}

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		additionalReportColumns = "";
		super.init(run);
		indent = run.getMandatoryParamBoolean(INDENT);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		determineConceptsOfInterest();
		String columnStr = "";
		if (indent) {
			//If we're indenting, we just need a lot of columns but we don't know what they're going to contain
			for (int i = highestLevel ; i < deepestLevel ; i++ ) {
				if (i == highestLevel) {
					columnStr += ", , , , , , ";
				}
				columnStr += ", ";
			}
		} else {
			columnStr += "SCTID,FSN, SemTag, DEF_STATUS, Immediate Stated Parent, Immediate Inferred Parents, Inferred Grand Parents";
		}

		String[] tabNames = new String[]{
				"Concepts with Parents"};
		String[] columnHeadings = new String[]{
				columnStr
				};
		super.postInit(tabNames, columnHeadings);
	}

	protected void determineConceptsOfInterest() throws TermServerScriptException {
		String eclQuery = appendTermsFilter(jobRun.getMandatoryParamValue(ECL), jobRun.getParamValue(TERMS_FILTER));
		conceptsOfInterest = SnomedUtils.sort(findConcepts(eclQuery));
		deepestLevel = SnomedUtils.findDeepestConcept(conceptsOfInterest, false).getDepth();
		highestLevel = SnomedUtils.findShallowestConcept(conceptsOfInterest, false).getDepth();
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL)
					.withType(JobParameter.Type.ECL)
					.withMandatory()
					.withDescription("Specify")
				.add(TERMS_FILTER)
					.withType(JobParameter.Type.STRING)
					.withDescription("Optional.  Use a comma to separate multiple terms.  This will be ignored if the ECL contains a term filter.")
				.add(INDENT)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
					.withDescription("Indent the output as a representation of the hierarchy.")
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Concepts with Parents")
				.withDescription("This report lists all parents and grandparents of concepts in the specified hierarchies.  Grandparents not available when indenting.  Note that indenting may cause the same concept to appear multiple times if it has multiple parents within the selection.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		List<Concept> topLevelConcepts = new ArrayList<>();
		if (indent) {
			//Report top level concepts first
			topLevelConcepts = conceptsOfInterest.stream()
					.filter(c -> c.getDepth() == highestLevel)
					.toList();

			for (Concept concept : topLevelConcepts) {
				if (indent) {
					reportIndented(concept, 0,true);
				} else {
					report(concept);
				}
			}
		}

		for (Concept concept : conceptsOfInterest) {
			if (topLevelConcepts.contains(concept)) {
				continue;
			}
			if (indent) {
				int indentBy = concept.getDepth() - highestLevel;
				reportIndented(concept, indentBy,true);
			} else {
				report(concept, null, true);
			}
		}
	}

	private void reportIndented(Concept concept, int indentBy, boolean isTopLevel) throws TermServerScriptException {
		//If we're being run directly off the initial selection, we don't want to report concepts more than once
		//But other than that, if a concept is a child more than once (ie multiple parents) then report it each time.
		if ((isTopLevel && conceptsReported.contains(concept)) || !conceptsOfInterest.contains(concept)) {
			return;
		}
		if (concept.getDepth() == highestLevel) {
			report(concept, null, true);
		} else {
			String[] indentArr = new String[indentBy];
			report(concept, indentArr, false);
		}
		for (Concept child : SnomedUtils.sort(concept.getChildren(CharacteristicType.INFERRED_RELATIONSHIP))) {
			reportIndented(child, indentBy + 1,false);
		}

	}

	private void report(Concept c, String[] indent, boolean includeGrandParents) throws TermServerScriptException {
		if (whiteListedConceptIds.contains(c.getId())) {
			incrementSummaryInformation(WHITE_LISTED_COUNT);
			return;
		}

		Set<Concept> statedParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
		String statedParentsStr = statedParents.stream()
				.map(Concept::toString)
				.distinct()
				.sorted()
				.collect(Collectors.joining(COMMA_NEWLINE_DELIMITER));
		Set<Concept> inferredParents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		String inferredParentsStr = inferredParents.stream()
				.map(Concept::toString)
				.distinct()
				.sorted()
				.collect(Collectors.joining(COMMA_NEWLINE_DELIMITER));
		String definition = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		if (includeGrandParents) {
			String grandParentsStr = inferredParents.stream()
					.flatMap(parent -> parent.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream())
					.map(Concept::toString)
					.distinct()
					.sorted()
					.collect(Collectors.joining(COMMA_NEWLINE_DELIMITER));
			report(c, definition, statedParentsStr, inferredParentsStr, grandParentsStr);
		} else {
			report(PRIMARY_REPORT, indent, c, statedParentsStr, inferredParentsStr);
		}
		conceptsReported.add(c);
		countIssue(c);
		incrementSummaryInformation("Concepts reported");
	}

	/**
	 * Append a terms filter to the ECL query. Although if the ECL already contains a term filter, it will be ignored.
	 *
	 * @param ecl ECL to add terms to.
	 * @param terms String containing comma separated terms.
	 * @return new ECL with terms filter.
	 */
	private String appendTermsFilter(String ecl, String terms) {
		StringBuilder resultEcl = new StringBuilder();

		if (ecl == null || ecl.isEmpty()) {
			return "";
		}

		resultEcl.append(ecl.trim());

		if (terms == null || terms.isEmpty() || (CONTAINS_TERM_PATTERN.matcher(ecl).find())) {
			return resultEcl.toString().replaceAll("\\s+", " ");
		}

		String[] termList = terms.split(",");

		if (termList.length == 1) {
			resultEcl.append(" {{term = \"").append(termList[0]).append("\"}}");
			return resultEcl.toString().replaceAll("\\s+", " ");
		}

		resultEcl.append(" {{term = (");
		boolean addSpace = false;

		for (String term : termList) {
			if (addSpace) {
				resultEcl.append(' ');
			}

			addSpace = true;
			resultEcl.append('"').append(term.trim()).append('"');
		}

		resultEcl.append(")}}");
		return resultEcl.toString().replaceAll("\\s+", " ");
	}
}
