package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

/**
 * RP-414
 */
public class CompareConceptsBetweenBranches extends TermServerReport implements ReportClass {
	
	private final List<Concept> skipAttributeTypes = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, Boolean.TRUE.toString());
		params.put(ECL, "<< 386053000 |Evaluation procedure|");
		TermServerScript.run(CompareConceptsBetweenBranches.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		
		if (run.getParameters().getMandatoryBoolean(UNPROMOTED_CHANGES_ONLY) && project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("UnpromotedChangesOnly makes no sense when running against MAIN");
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"ConceptId, FSN, SemTag, Has Stated Changes, Has Inferred Changes, Before Stated, Before Inferred, After Stated, After Inferred, Role Group Count (Ignoring skipped types), Concern"};
		String[] tabNames = new String[] {	
				"Comparing MAIN to " + project.getKey()};
		super.postInit(tabNames, columnHeadings);

		skipAttributeTypes.add(gl.getConcept("363702006 |Has focus (attribute)|"));
		skipAttributeTypes.add(IS_A);
		skipAttributeTypes.add(PART_OF);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Compare Concepts Between Branches")
				.withDescription("This report details stated and inferred concept models between MAIN and the selected project/task.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = SnomedUtils.sort(new ArrayList<>(findConcepts(subsetECL)));
		} else {
			conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		}
		
		for (Concept c : conceptsOfInterest) {
			if (unpromotedChangesHelper.hasUnpromotedChange(c)) {
				Concept before = loadConcept(c, "MAIN");
				String beforeStated = before.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				String beforeInferred = before.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				String afterStated = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
				String afterInferred = c.toExpression(CharacteristicType.INFERRED_RELATIONSHIP);
				String hasStatedChanges = beforeStated.equals(afterStated)? "N" : "Y";
				String hasInferredChanges = beforeInferred.equals(afterInferred)? "N" : "Y";
				long roleGroupCount = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)
						.stream()
						.filter(g -> !isEntirelySkippedAttributes(g))
						.count();
				String concern = checkConceptForConcerns(c, before);
				if (!beforeStated.equals(afterStated) || !beforeInferred.equals(afterInferred)) {
					report(c, hasStatedChanges, hasInferredChanges, beforeStated, beforeInferred, afterStated, afterInferred, roleGroupCount, concern);
				}
			}
		}

	}

	private String checkConceptForConcerns(Concept c, Concept before) {
		String concerns = "";
		if (stillHasSelfGroupedAttributes(c)) {
			concerns += "Remaining Self Grouped Attributes";
		}
		if (startedWithFullerModel(before)) {
			if (!concerns.isEmpty()) {
				concerns += ",\n";
			}
			concerns += "Started with fuller model";
		}
		return concerns;
	}

	private boolean startedWithFullerModel(Concept c) {
		//Check stated groups and return true if any contained more than one attribute
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, false)) {
			if (g.size() > 1) {
				return true;
			}
		}
		return false;
	}

	private boolean stillHasSelfGroupedAttributes(Concept c) {
		//We have concerns about concepts that have self-grouped attributes and other grouped attributes
		boolean hasSelfGroupedAttribute = false;
		boolean hasOtherGroup = false;
		for (RelationshipGroup g : c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP, false)) {
			//Ignore group 0
			if (g.getGroupId() == 0) {
				continue;
			}
			if (g.size() == 1) {
				//Skip attribute types we're ignoring
				if (containsSkippedAttribute(g)) {
					continue;
				}
				hasSelfGroupedAttribute = true;
			} else {
				hasOtherGroup = true;
			}

			if (hasSelfGroupedAttribute && hasOtherGroup) {
				return true;
			}
		}
		return false;
	}

	private boolean containsSkippedAttribute(RelationshipGroup g) {
		for (Relationship r : g.getRelationships()) {
			if (skipAttributeTypes.contains(r.getType())) {
				return true;
			}
		}
		return false;
	}

	private boolean isEntirelySkippedAttributes(RelationshipGroup g) {
		for (Relationship r : g.getRelationships()) {
			if (!skipAttributeTypes.contains(r.getType())) {
				return false;
			}
		}
		return true;
	}


}
