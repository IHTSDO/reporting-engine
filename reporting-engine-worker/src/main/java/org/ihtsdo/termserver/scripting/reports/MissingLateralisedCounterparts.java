package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

/**
 * RP-585 Report for finding missing lateralised anatomy counterparts. For example, report if there is a 'Left' Concept with no corresponding 'Right' Concept in the body structure hierarchy.
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MissingLateralisedCounterparts extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MissingLateralisedCounterparts.class);

	private static final String CURRENT_CYCLE = "Concepts new/modified"; // Toggle whether to process all Concepts or only those that are new/modified.
	private static final String NOT_YET_MEMBERS = "Concepts not in lateralisable reference set"; // Toggle whether to process Concepts not yet in the lateralisable reference set.
	private static final String ALREADY_MEMBERS = "Concepts in lateralisable reference set"; // Toggle whether to process Concepts already in the lateralisable reference set.
	private static final String LATERALITY = "272741003";
	private static final String LEFT = "7771000";
	private static final String RIGHT = "24028007";
	private static final String RIGHT_AND_LEFT = "51440002";

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(CURRENT_CYCLE, "true");
		params.put(NOT_YET_MEMBERS, "true");
		params.put(ALREADY_MEMBERS, "true");
		TermServerScript.run(MissingLateralisedCounterparts.class, args, params);
	}

	@Override
	public void init(JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		super.init(run);
	}

	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Missing lateralised anatomy counterparts")
				.withDescription("This report finds missing lateralised anatomy counterparts. For example, if there is a 'Left' Concept with no corresponding 'Right' Concept in the body structure hierarchy.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(
						new JobParameters()
								.add(CURRENT_CYCLE).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
								.add(NOT_YET_MEMBERS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
								.add(ALREADY_MEMBERS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
								.build()
				)
				.withTag(INT)
				.build();
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] tabNames = new String[]{
				"Result",
		};
		String[] columnHeadings = new String[]{
				"Identifier, FSN, SemTag, Member, Comment"
		};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		if (jobRun.getParamBoolean(NOT_YET_MEMBERS)) {
			// Process lateralisable Concepts not yet added to 723264001 |Lateralisable body structure reference set|.
			String byLaterality = "( (<< 91723000 |Anatomical structure (body structure)| : 272741003 | Laterality (attribute) | = 182353008 |Side (qualifier value)|) MINUS ( * : 272741003 | Laterality (attribute) | = (7771000 |Left (qualifier value)| OR 24028007 |Right (qualifier value)| OR 51440002 |Right and left (qualifier value)|) ) )  MINUS (^ 723264001)";
			String byHierarchy = "(( << 91723000 |Anatomical structure (body structure)| MINUS (* : 272741003 | Laterality (attribute) | = (7771000 |Left (qualifier value)| OR 24028007 |Right (qualifier value)| OR 51440002 |Right and left (qualifier value)|)))  AND (<  (^ 723264001)))   MINUS (^ 723264001)";
			Set<Concept> notYetMembers = getConceptsByECL(byLaterality, byHierarchy);

			reportOddNumberOfLateralisedChildren(notYetMembers, false);
		}

		if (jobRun.getParamBoolean(ALREADY_MEMBERS)) {
			// Process lateralisable Concepts already added to 723264001 |Lateralisable body structure reference set|.
			String byMembership = "^ 723264001";
			Set<Concept> alreadyMembers = getConceptsByECL(byMembership);

			reportOddNumberOfLateralisedChildren(alreadyMembers, true);
		}
	}

	private Set<Concept> getConceptsByECL(String... eclStatements) throws TermServerScriptException {
		Set<Concept> concepts = new HashSet<>();
		for (String eclStatement : eclStatements) {
			if (jobRun.getParamBoolean(CURRENT_CYCLE)) {
				concepts.addAll(findConceptsWithoutEffectiveTime(eclStatement));
			} else {
				concepts.addAll(findConcepts(eclStatement));
			}
		}

		return concepts;
	}

	private void reportOddNumberOfLateralisedChildren(Set<Concept> lateralisableConcepts, boolean memberOfLateralisableReferenceSet) throws TermServerScriptException {
		int counter = 0;
		int size = lateralisableConcepts.size();
		String isMember = memberOfLateralisableReferenceSet ? "Y" : "N";
		LOGGER.info(String.format("%d lateralisable Concepts will be checked they do not have an odd number of children.", size));
		for (Concept lateralisableConcept : lateralisableConcepts) {
			counter++;
			LOGGER.info(String.format("Processing %d/%d lateralisable Concepts with membership of 723264001 being %s.", counter, size, isMember));
			if (!lateralisableConcept.isActive()) {
				report(PRIMARY_REPORT, lateralisableConcept.getConceptId(), lateralisableConcept.getFsn(), lateralisableConcept.getSemTag(), isMember, "Concept is inactive and should be removed from reference set.");
				countIssue(lateralisableConcept);
				continue;
			}

			Set<Concept> lateralisedChildren = getLateralisedChildren(lateralisableConcept.getConceptId());
			if (lateralisedChildren.isEmpty()) {
				continue;
			}

			int lateralised = lateralisedChildren.size();
			if (lateralised == 1) {
				countIssue(lateralisableConcept);
				report(PRIMARY_REPORT, lateralisableConcept.getConceptId(), lateralisableConcept.getFsn(), lateralisableConcept.getSemTag(), isMember, "Possibly missing content as only 1 lateralised child.");
			} else if (lateralised % 2 != 0) {
				countIssue(lateralisableConcept);
				report(PRIMARY_REPORT, lateralisableConcept.getConceptId(), lateralisableConcept.getFsn(), lateralisableConcept.getSemTag(), isMember, String.format("Possibly missing content as only %d lateralised children.", lateralised));
			}
		}
	}

	private Set<Concept> getLateralisedChildren(String conceptId) throws TermServerScriptException {
		Set<Concept> children = gl.getConcept(conceptId).getChildren(CharacteristicType.INFERRED_RELATIONSHIP);
		children.removeIf(c -> {
			for (Relationship relationship : c.getRelationships()) {
				String typeId = relationship.getType().getId();
				String targetId = relationship.getTarget().getId();

				if (LATERALITY.equals(typeId) && (LEFT.equals(targetId) || RIGHT.equals(targetId) || RIGHT_AND_LEFT.equals(targetId))) {
					return false;
				}
			}

			return true;
		});

		return children;
	}
}
