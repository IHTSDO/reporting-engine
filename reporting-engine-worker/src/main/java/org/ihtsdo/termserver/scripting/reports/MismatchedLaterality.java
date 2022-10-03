package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;

/**
 * RP-585 Report lateralisable Concepts that have missing lateralised counterparts. For example,
 * report if there is a Left arm concept but there is no Right arm concept.
 */
public class MismatchedLaterality extends TermServerReport implements ReportClass {
    private final Map<String, Concept> fsnMap = new HashMap<>();

    public static void main(String[] args) throws TermServerScriptException, IOException {
        Map<String, String> params = new HashMap<>();
        params.put("legacy", "false"); // Toggle whether to process all Concepts or only those that are new/modified.
        params.put("notYetMembers", "true"); // Toggle whether to process Concepts not yet in the lateralisable reference set.
        params.put("alreadyMembers", "true"); // Toggle whether to process Concepts already in the lateralisable reference set.
        TermServerReport.run(MismatchedLaterality.class, args, params);
    }

    public void init(JobRun run) throws TermServerScriptException {
        getArchiveManager().setPopulateReleasedFlag(true);
        ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
        super.init(run);
    }

    @Override
    public Job getJob() {
        return new Job()
                .withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
                .withName("Missing lateralised counterparts")
                .withDescription("This report lists lateralised concepts that have an odd number of lateralised children. Having an odd number of lateralised children suggests there may be missing content.")
                .withProductionStatus(ProductionStatus.PROD_READY)
                .withParameters(
                        new JobParameters()
                                .add("legacy").withDefaultValue(false)
                                .add("notYetMembers").withDefaultValue(true)
                                .add("alreadyMembers").withDefaultValue(true)
                                .build()
                )
                .withTag(INT)
                .build();
    }

    public void postInit() throws TermServerScriptException {
        String[] tabNames = new String[]{
                "Result",
        };
        String[] columnHeadings = new String[]{
                "Identifier, FSN, SemTag, Member, Action, Comment"
        };
        super.postInit(tabNames, columnHeadings, false);

        info("Populating FSN map for all concepts");
        for (Concept c : gl.getAllConcepts()) {
            if (c.isActive()) {
                fsnMap.put(c.getFsn().toLowerCase(), c);
            }
        }
    }

    public void runJob() throws TermServerScriptException {
        if (jobRun.getParamBoolean("notYetMembers")) {
            // Process lateralisable Concepts not yet added to 723264001 |Lateralisable body structure reference set|.
            String byLaterality = "( (<< 91723000 |Anatomical structure (body structure)| : 272741003 | Laterality (attribute) | = 182353008 |Side (qualifier value)|) MINUS ( * : 272741003 | Laterality (attribute) | = (7771000 |Left (qualifier value)| OR 24028007 |Right (qualifier value)| OR 51440002 |Right and left (qualifier value)|) ) )  MINUS (^ 723264001)";
            String byHierarchy = "(( << 91723000 |Anatomical structure (body structure)| MINUS (* : 272741003 | Laterality (attribute) | = (7771000 |Left (qualifier value)| OR 24028007 |Right (qualifier value)| OR 51440002 |Right and left (qualifier value)|)))  AND (<  (^ 723264001)))   MINUS (^ 723264001)";
            Set<Concept> notYetMembers = getConceptsByECL(byLaterality, byHierarchy);

            reportOddNumberOfLateralisedChildren(notYetMembers, false);
        }

        if (jobRun.getParamBoolean("alreadyMembers")) {
            // Process lateralisable Concepts already added to 723264001 |Lateralisable body structure reference set|.
            String byMembership = "^ 723264001";
            Set<Concept> alreadyMembers = getConceptsByECL(byMembership);

            reportOddNumberOfLateralisedChildren(alreadyMembers, true);
        }
    }

    private Set<Concept> getConceptsByECL(String... eclStatements) throws TermServerScriptException {
        Set<Concept> concepts = new HashSet<>();
        for (String eclStatement : eclStatements) {
            if (jobRun.getParamBoolean("legacy")) {
                concepts.addAll(findConcepts(eclStatement));
            } else {
                concepts.addAll(findConceptsWithoutEffectiveTime(eclStatement));
            }
        }

        return concepts;
    }

    private void reportOddNumberOfLateralisedChildren(Set<Concept> lateralisableConcepts, boolean memberOfLateralisableReferenceSet) throws TermServerScriptException {
        int counter = 0;
        int size = lateralisableConcepts.size();
        String isMember = memberOfLateralisableReferenceSet ? "Y" : "N";
        info(String.format("%d lateralisable Concepts will be checked they do not have an odd number of children.", size));
        for (Concept lateralisableConcept : lateralisableConcepts) {
            counter++;
            info(String.format("Processing %d/%d lateralisable Concepts with membership of 723264001 being %s.", counter, size, isMember));
            if (!lateralisableConcept.isActive()) {
                report(PRIMARY_REPORT, lateralisableConcept.getConceptId(), lateralisableConcept.getFsn(), lateralisableConcept.getSemTag(), isMember, "Required", "Concept is inactive and should be removed from reference set.");
                continue;
            }

            Collection<Concept> lateralisedChildren = findConcepts(String.format("<! %s : 272741003 |Laterality| = (7771000 |left| OR 24028007 |right| OR 51440002 |right and left|)", lateralisableConcept.getConceptId()));
            if (lateralisedChildren.isEmpty()) {
                report(PRIMARY_REPORT, lateralisableConcept.getConceptId(), lateralisableConcept.getFsn(), lateralisableConcept.getSemTag(), isMember, "Not required", "No lateralisable children.");
                continue;
            }

            int lateralised = lateralisedChildren.size();
            if (lateralised == 1) {
                report(PRIMARY_REPORT, lateralisableConcept.getConceptId(), lateralisableConcept.getFsn(), lateralisableConcept.getSemTag(), isMember, "Required", "Possibly missing content as only 1 lateralised child.");
            } else if (lateralised % 2 != 0) {
                report(PRIMARY_REPORT, lateralisableConcept.getConceptId(), lateralisableConcept.getFsn(), lateralisableConcept.getSemTag(), isMember, "Required", String.format("Possibly missing content as only %d lateralised children.", lateralised));
            }
        }
    }
}
