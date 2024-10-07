package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * FRI-353 List changes made in the current authoring cycle to SEP and Laterality Refsets
 */
public class SEP_Laterality_RefsetUpdates extends TermServerReport implements ReportClass {

	public static final String SCTID_SE_REFSETID = "734138000";
	public static final String SCTID_SP_REFSETID = "734139008";
	public static final String SCTID_LAT_REFSETID = "723264001";
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(SEP_Laterality_RefsetUpdates.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		summaryTabIdx = PRIMARY_REPORT;
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Item, Count",
				"RefsetId, Active, isNew, Mapping, UUID",
				"RefsetId, Active, isNew, Concept, UUID"};
		String[] tabNames = new String[] {
				"Summary",
				"SEP Updates",
				"Lateralizable Updates"};
		super.postInit(tabNames, columnHeadings);
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

	@Override
	public void runJob() throws TermServerScriptException {
		reportRefsetUpdates(SECONDARY_REPORT, SCTID_SE_REFSETID, "SE Refset");
		reportRefsetUpdates(SECONDARY_REPORT, SCTID_SP_REFSETID, "SP Refset");
		reportRefsetUpdates(TERTIARY_REPORT, SCTID_LAT_REFSETID, "LAT Refset");
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
