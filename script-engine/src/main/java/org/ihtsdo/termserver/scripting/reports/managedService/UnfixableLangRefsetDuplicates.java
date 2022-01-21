package org.ihtsdo.termserver.scripting.reports.managedService;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

public class UnfixableLangRefsetDuplicates extends TermServerReport implements ReportClass {
	
	boolean includeLegacyIssues = false;
	Set<RefsetMember> mentioned = new HashSet<>();
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<<404684003 |Clinical finding (finding)|");
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "N");
		TermServerReport.run(UnfixableLangRefsetDuplicates.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA Reports
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("UnfixableLangRefsetDuplicates report cannot be run against MAIN");
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Issue, IntActive, IntEffective, IntRM, ExtActive, ExtEffective, ExtRM"};
		String[] tabNames = new String[] {	
				"UnfixableLangRefsetDuplicates"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(INCLUDE_ALL_LEGACY_ISSUES)
					.withType(JobParameter.Type.BOOLEAN)
					.withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Unfixable LangRefset Duplicates")
				.withDescription("This report lists language refset duplicates which cannot be fixed due to having been" +
				"previously published.  The best we can do is ensure that one of them is inactivated.")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getId().equals("732901000124115")) {
					debug("here");
				}
				reportUnfixableDuplicates(c, d.getLangRefsetEntries());
			}
		}
	}

	private void reportUnfixableDuplicates(Concept c, List<LangRefsetEntry> refsetMembers) throws TermServerScriptException {
		for (final RefsetMember thisEntry : refsetMembers) {
			// Check against every other entry
			for (final RefsetMember thatEntry : refsetMembers) {
				// If we've already decided we're keeping this entry or deleting this entry, skip
				if (thisEntry.getId().equals(thatEntry.getId()) || 
						mentioned.contains(thisEntry) ||
						mentioned.contains(thatEntry) ) {
					continue;
				}
				
				if (hasModule(INTERNATIONAL_MODULES, true, thatEntry) != null && SnomedUtils.isEmpty(thatEntry.getEffectiveTime())) {
					report (c, "International Module LRM with null effectivTime!", thatEntry.isActive(), thatEntry.getEffectiveTime(), thatEntry.toString());
					mentioned.contains(thatEntry);
				}
				
				if (thisEntry.getRefsetId().equals(thatEntry.getRefsetId())) {
					//Are they both published?  If INT is active and EXT is inactive with no effective time then that's as good as we'll get
					if (thisEntry.isReleased() && thatEntry.isReleased()) {
						RefsetMember intRM = hasModule(INTERNATIONAL_MODULES, true, thisEntry, thatEntry);
						RefsetMember extRM = hasModule(INTERNATIONAL_MODULES, false, thisEntry, thatEntry);
						if (intRM != null && extRM != null) {
							if (intRM.isActive() && !extRM.isActive() && StringUtils.isEmpty(extRM.getEffectiveTime())) {
								report(c, "Active Int replaced Inactive Ext", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							} else if (intRM.isActive() && extRM.isActive()) {
								report(c, "Active Int duplicates Active Ext", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							} else if (!intRM.isActive() && extRM.isActive() && (StringUtils.isEmpty(extRM.getEffectiveTime()) || StringUtils.isEmpty(intRM.getEffectiveTime()))) {
								report(c, "Active Ext duplicates Inactive Int", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							} else if (StringUtils.isEmpty(extRM.getEffectiveTime()) || StringUtils.isEmpty(intRM.getEffectiveTime())){
								report(c, "Other situation", intRM.isActive(), intRM.getEffectiveTime(), intRM.toString(), extRM.isActive(), extRM.getEffectiveTime(), extRM.toString());
								mentioned.add(thisEntry);
								mentioned.add(thatEntry);
							}
						} else {
							report(c, "Same Module Duplication " + thisEntry.getModuleId(), thisEntry.isActive(), thisEntry.getEffectiveTime(), thisEntry.toString(), thatEntry.isActive(), thatEntry.getEffectiveTime(), thatEntry.toString());
							mentioned.add(thisEntry);
							mentioned.add(thatEntry);
						}
					} 
				}
			}
		}
	}
	
	private RefsetMember hasModule(String[] targetModules, boolean matchLogic, RefsetMember... refsetMembers) {
		for (RefsetMember rm : refsetMembers) {
			if (matchLogic && SnomedUtils.hasModule(rm, targetModules)) {
				return rm;
			} else if (!matchLogic && SnomedUtils.hasNotModule(rm, targetModules)) {
				return rm;
			}
		}
		return null;
	}

}
