package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RefsetMember;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

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
				"UUID, EffectiveTime, RefsetId, Active, ReferencedComponentId, DOMAIN_CONSTRAINT, PARENT_DOMAIN, PROXIMAL_PRIMITIVE_CONSTRAINT," + 
				"			PROXIMAL_PRIMITIVE_REFINEMENT, DOMAIN_TEMPLATE_FOR_PRECOORDINATION, DOMAIN_TEMPLATE_FOR_POSTCOORDINATION, GUIDE_URL",
				"UUID, EffectiveTime, RefsetId, Active, ReferencedComponentId, RANGE_CONSTRAINT, ATTRIBUTE_RULE, RULE_STRENGTH_ID, CONTENT_TYPE_ID"};
		String[] tabNames = new String[] {
				"MRCM Domain",
				"MRCM Attribute"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("New and Changed MRCM")
				.withDescription("This report lists updates to the MRCM refsets.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(new JobParameters())
				.withTag(INT)
				.withExpectedDuration(40)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		reportRefsetUpdates(PRIMARY_REPORT, gl.getMrcmDomainMap());
		reportRefsetUpdates(SECONDARY_REPORT, gl.getMrcmAttributeRangeMapPreCoord());
		reportRefsetUpdates(SECONDARY_REPORT, gl.getMrcmAttributeRangeMapPostCoord());
		reportRefsetUpdates(SECONDARY_REPORT, gl.getMrcmAttributeRangeMapAll());
		reportRefsetUpdates(SECONDARY_REPORT, gl.getMrcmAttributeRangeMapNewPreCoord());
	}

	private void reportRefsetUpdates(int tabIdx, Map<Concept,? extends RefsetMember> mrcmMap) throws TermServerScriptException {
		for (RefsetMember rm : mrcmMap.values()) {
			if (StringUtils.isEmpty(rm.getEffectiveTime())) {
				countIssue((Concept)null);
				report(tabIdx, rm.getId(), rm.getEffectiveTime(), rm.getRefsetId(), rm.isActive()?"1":"0", rm.getReferencedComponentId(), rm.getAdditionalFieldsArray());
			}
		}
	}
	
}