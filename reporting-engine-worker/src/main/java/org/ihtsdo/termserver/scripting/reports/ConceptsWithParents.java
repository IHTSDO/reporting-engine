package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class ConceptsWithParents extends TermServerReport implements ReportClass {
    private static final String TERMS_FILTER = "Filter for terms";
    private static final String EXAMPLE_MAIN_PARAM_ECL = "<< 1144725004";
    private static final String EXAMPLE_MAIN_PARAM_TERMS_FILTER = "capsule";
    private static final Pattern CONTAINS_TERM_PATTERN = Pattern.compile("\\{\\{\\s*term", Pattern.MULTILINE);
    public static final String COMMA_NEWLINE_DELIMITER = ",\n";

    public static void main(String[] args) throws TermServerScriptException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put(ECL, EXAMPLE_MAIN_PARAM_ECL);
        params.put(TERMS_FILTER, EXAMPLE_MAIN_PARAM_TERMS_FILTER);
        TermServerReport.run(ConceptsWithParents.class, args, params);
    }

    public void init(JobRun run) throws TermServerScriptException {
        ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
        additionalReportColumns = "FSN, SemTag, DEF_STATUS, Immediate Stated Parent, Immediate Inferred Parents ,Inferred Grand Parents";
        super.init(run);
    }

    @Override
    public Job getJob() {
        JobParameters params = new JobParameters()
                .add(ECL).withDescription("foo").withType(JobParameter.Type.ECL).withMandatory().withDescription("Specify")
                .add(TERMS_FILTER).withDescription("Optional.  Use a comma to separate multiple terms.  This will be ignored if the ECL contains a term filter.").withType(JobParameter.Type.STRING)
                .build();

        return new Job()
                .withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
                .withName("Concepts with Parents")
                .withDescription("This report lists all parents and grandparents of concepts in the specified hierarchies.")
                .withProductionStatus(ProductionStatus.PROD_READY)
                .withParameters(params)
                .withTag(INT)
                .build();
    }

    public void runJob() throws TermServerScriptException {
        String eclQuery = appendTermsFilter(jobRun.getMandatoryParamValue(ECL), jobRun.getParamValue(TERMS_FILTER));
        Collection<Concept> conceptsOfInterest = findConcepts(eclQuery);
        List<Concept> sortedListOfConceptsOfInterest = SnomedUtils.sort(conceptsOfInterest);

        for (Concept concept : sortedListOfConceptsOfInterest) {
            if (whiteListedConceptIds.contains(concept.getId())) {
                incrementSummaryInformation(WHITE_LISTED_COUNT);
                continue;
            }

            Set<Concept> statedParents = concept.getParents(CharacteristicType.STATED_RELATIONSHIP);
            String statedParentsStr = statedParents.stream()
                    .map(Concept::toString)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(COMMA_NEWLINE_DELIMITER));
            Set<Concept> inferredParents = concept.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
            String inferredParentsStr = inferredParents.stream()
                    .map(Concept::toString)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(COMMA_NEWLINE_DELIMITER));
            String grandParentsStr = inferredParents.stream()
                    .flatMap(parent -> parent.getParents(CharacteristicType.INFERRED_RELATIONSHIP).stream())
                    .map(Concept::toString)
                    .distinct()
                    .sorted()
                    .collect(Collectors.joining(COMMA_NEWLINE_DELIMITER));
            String definition = SnomedUtils.translateDefnStatus(concept.getDefinitionStatus());

            report(concept, definition, statedParentsStr, inferredParentsStr, grandParentsStr);
            countIssue(concept);
            incrementSummaryInformation("Concepts reported");
        }
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
