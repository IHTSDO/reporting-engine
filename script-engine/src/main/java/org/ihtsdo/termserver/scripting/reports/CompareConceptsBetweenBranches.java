package org.ihtsdo.termserver.scripting.reports;

import com.google.common.util.concurrent.AtomicLongMap;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.RelationshipGroup;
import org.ihtsdo.termserver.scripting.reports.release.UnpromotedChangesHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;

/**
 * RP-414
 */
public class CompareConceptsBetweenBranches extends TermServerReport implements ReportClass {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(CompareConceptsBetweenBranches.class);

	private final List<Concept> skipAttributeTypes = new ArrayList<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, Boolean.TRUE.toString());
		params.put(ECL, "<< 386053000 |Evaluation procedure|");
		TermServerReport.run(CompareConceptsBetweenBranches.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		
		if (run.getParameters().getMandatoryBoolean(UNPROMOTED_CHANGES_ONLY) && project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("UnpromotedChangesOnly makes no sense when running against MAIN");
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"ConceptId, FSN, SemTag, Before Stated, Before Inferred, After Stated, After Inferred, Role Group Count (Ignoring skipped types), Concern"};
		String[] tabNames = new String[] {	
				"Comparing MAIN to " + project.getKey()};
		super.postInit(tabNames, columnHeadings, false);

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
				long roleGroupCount = c.getRelationshipGroups(CharacteristicType.INFERRED_RELATIONSHIP)
						.stream()
						.filter(g -> !isEntirelySkippedAttributes(g))
						.count();
				String concern = checkConceptForConcern(c) ? "Y" : "N";
				if (!beforeStated.equals(afterStated) || !beforeInferred.equals(afterInferred)) {
					report (c, beforeStated, beforeInferred, afterStated, afterInferred, roleGroupCount, concern);
				}
			}
		}

	}

	private boolean checkConceptForConcern(Concept c) {
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
