package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivatedConcepts extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivatedConcepts.class);

	static final String RELEASE = "Release Package";
	static final String SEMTAG_FILTER_PARAM = "Filter for SemTag";
	private String prevRelease;
	private String thisEffectiveTime;
	private String semtagFilter;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(RELEASE, "dev_xSnomedCT_InternationalRF2_PREALPHA_20200731T120000Z.zip");
		//params.put(SEMTAG_FILTER_PARAM, "(procedure)");
		TermServerReport.run(InactivatedConcepts.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		semtagFilter = run.getParamValue(SEMTAG_FILTER_PARAM);
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Reason, Assoc Type, Assoc Value"};
		String[] tabNames = new String[] {	
				"Concepts Inactivated"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SEMTAG_FILTER_PARAM).withType(Type.STRING)
				.add(RELEASE).withType(Type.STRING)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Inactivated Concepts")
				.withDescription("This report lists all concepts inactivated in the current release cycle along with the reason and historical association, " + 
				"optionally restricted to a particular semantic tag.  The issue count here is the total number of concepts inactivated." +
						" Also optional is specifying a previously published release package to run against.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : scopeAndSort(gl.getAllConcepts())) {
			boolean reported = false;
			InactivationIndicator i = c.getInactivationIndicator();
			for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
				String assocType = SnomedUtils.getAssociationType(a);
				Concept assocValue = gl.getConcept(a.getTargetComponentId());
				report (c, i, assocType, assocValue);
				reported = true;
			}
			if (!reported) {
				report (c, i , "N/A");
			}
			countIssue(c);
		}
	}
	
	private boolean inScope(Concept c) {
		//We want inactive concepts modified either in the current release cycle or in the 
		//latest release if we're looking at a particular release package
		//Optionally for a specific semantic tag
		return !c.isActive() 
				&& hasRequiredSemTag(c)
				&& ( 
					(thisEffectiveTime == null && StringUtils.isEmpty(c.getEffectiveTime()) ||
					(thisEffectiveTime != null && thisEffectiveTime.equals(c.getEffectiveTime())))
				);
	}
	
	private boolean hasRequiredSemTag(Concept c) {
		//If we didn't specify a filter, then it's in
		if (StringUtils.isEmpty(semtagFilter)) {
			return true;
		}
		
		String semTag = SnomedUtils.deconstructFSN(c.getFsn(), true)[1];
		return semTag != null && semTag.contains(semtagFilter);
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		prevRelease = getJobRun().getParamValue(RELEASE);
		if (!StringUtils.isEmpty(prevRelease)) {
			LOGGER.info ("Loading previously published package: " + prevRelease);
			getProject().setKey(prevRelease);
			super.loadProjectSnapshot(fsnOnly);
			thisEffectiveTime = gl.getCurrentEffectiveTime();
			LOGGER.info ("Detected this effective time as " + thisEffectiveTime);
		} else {
			super.loadProjectSnapshot(fsnOnly);
		}
	};
	
	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		//filter for appropriate scope at the same time - avoids problems with FSNs without semtags
		return superSet.stream()
		.filter (c -> inScope(c))
		.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
		.collect(Collectors.toList());
	}
	
}
