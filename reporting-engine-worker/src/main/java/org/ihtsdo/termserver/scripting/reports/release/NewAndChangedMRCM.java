package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.RefsetMember;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * FRI-353 List changes made in the current authoring cycle to SEP and Laterality Refsets
 */
public class NewAndChangedMRCM extends TermServerReport implements ReportClass {
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		TermServerReport.run(NewAndChangedMRCM.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"RefsetId, Active, isNew, Mapping, UUID",
				"RefsetId, Active, isNew, Concept, UUID"};
		String[] tabNames = new String[] {
				"MRCM Domain",
				"MRCM Attribute"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Updates to SEP and Lateralizable Refsets")
				.withDescription("This report lists updates to the SEP and Lateralizable refsets.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.withExpectedDuration(40)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		reportRefsetUpdates(SECONDARY_REPORT, null, "SE Refset");
	}

	private void reportRefsetUpdates(int tabIdx, String refsetId, String infoPrefix) throws TermServerScriptException {
		Collection<RefsetMember> refsetMembers = tsClient.findRefsetMembers(project.getBranchPath(), refsetId, true);
		for (RefsetMember rm : refsetMembers) {
			countIssue(null);
			String detail = gl.getConcept(rm.getReferencedComponentId()).toString();
			if (rm.isActive()) {
				if (rm.isReleased()) {
					incrementSummaryInformation(infoPrefix + " modified");
				} else {
					incrementSummaryInformation(infoPrefix + " created");
				}
			} else {
				incrementSummaryInformation(infoPrefix + " inactivated");
			}
			if (rm.hasAdditionalField("targetComponentId")) {
				detail += " --> ";
				String targetId = rm.getAdditionalFields().get("targetComponentId");
				detail += gl.getConcept(targetId).toString();
			}
			report(tabIdx, refsetId, rm.isActive(), !rm.getReleased(), detail, rm.getId());
		}
	}
	
}