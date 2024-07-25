package org.ihtsdo.termserver.scripting.reports.release;

import java.util.*;

import org.apache.commons.lang3.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.mrcm.MRCMAttributeDomain;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class NewAndChangedMRCM extends TermServerReport implements ReportClass {

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(NewAndChangedMRCM.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"UUID, EffectiveTime, RefsetId, Active, ReferencedComponentId,domainConstraint,parentDomain,proximalPrimitiveConstraint,proximalPrimitiveRefinement,domainTemplateForPrecoordination,domainTemplateForPostcoordination,guideURL",
				"UUID, EffectiveTime, RefsetId, Active, ReferencedComponentId,rangeConstraint,attributeRule,ruleStrengthId,contentTypeId",
				"UUID, EffectiveTime, RefsetId, Active, ReferencedComponentId,domainId,grouped,attributeCardinality,attributeInGroupCardinality,ruleStrengthId,contentTypeId"
		};
		String[] tabNames = new String[] {
				"MRCM Domain",
				"MRCM Attribute Range",
				"MRCM Attribute Domain"};
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

	@Override
	public void runJob() throws TermServerScriptException {
		reportRefsetUpdates(PRIMARY_REPORT, gl.getMRCMDomainManager().getMrcmDomainMap());

		reportRefsetUpdates(SECONDARY_REPORT, gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapPreCoord());
		reportRefsetUpdates(SECONDARY_REPORT, gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapPostCoord());
		reportRefsetUpdates(SECONDARY_REPORT, gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapAll());
		reportRefsetUpdates(SECONDARY_REPORT, gl.getMRCMAttributeRangeManager().getMrcmAttributeRangeMapNewPreCoord());

		reportAttributeRefsetUpdates(TERTIARY_REPORT, gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPreCoord());
		reportAttributeRefsetUpdates(TERTIARY_REPORT, gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapPostCoord());
		reportAttributeRefsetUpdates(TERTIARY_REPORT, gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapAll());
		reportAttributeRefsetUpdates(TERTIARY_REPORT, gl.getMRCMAttributeDomainManager().getMrcmAttributeDomainMapNewPreCoord());
	}

	private void reportRefsetUpdates(int tabIdx, Map<Concept,? extends RefsetMember> mrcmMap) throws TermServerScriptException {
		for (RefsetMember rm : mrcmMap.values()) {
			if (StringUtils.isEmpty(rm.getEffectiveTime())) {
				countIssue((Concept)null);
				report(tabIdx, rm.getId(), rm.getEffectiveTime(), rm.getRefsetId(), SnomedUtils.translateActiveState(rm), rm.getReferencedComponentId(), rm.getAdditionalFieldsArray());
			}
		}
	}

	private void reportAttributeRefsetUpdates(int tabIdx, Map<Concept, Map<Concept, MRCMAttributeDomain>> mrcmAttributeMap) throws TermServerScriptException {
		for (Map<Concept, MRCMAttributeDomain> mrcmMap : mrcmAttributeMap.values()) {
			for (RefsetMember rm : mrcmMap.values()) {
				if (StringUtils.isEmpty(rm.getEffectiveTime())) {
					countIssue((Concept) null);
					report(tabIdx, rm.getId(), rm.getEffectiveTime(), rm.getRefsetId(), SnomedUtils.translateActiveState(rm), rm.getReferencedComponentId(), rm.getAdditionalFieldsArray());
				}
			}
		}
	}

}
