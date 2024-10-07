package org.ihtsdo.termserver.scripting.reports;

import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
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

	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(InactivatedConcepts.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"); //Release Stats
		semtagFilter = run.getParamValue(SEMTAG_FILTER_PARAM);
		//Unpromoted changes only is picked up by the TermServerReport parent class as part of init
		super.init(run);

		//Running unpromoted changes only can't work with a release archive
		if (unpromotedChangesOnly && !StringUtils.isEmpty(run.getParamValue(RELEASE))) {
			throw new TermServerScriptException("Unpromoted changes only is not supported for use with a release package");
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Reason, Assoc Type, Assoc Value"};
		String[] tabNames = new String[] {	
				"Concepts Inactivated"};
		super.postInit(tabNames, columnHeadings);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(SEMTAG_FILTER_PARAM).withType(Type.STRING)
				.add(UNPROMOTED_CHANGES_ONLY).withType(Type.BOOLEAN).withMandatory().withDefaultValue("false")
				.add(RELEASE).withType(Type.RELEASE_ARCHIVE)
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

	@Override
	public void runJob() throws TermServerScriptException {
		for (Concept c : scopeAndSort(gl.getAllConcepts())) {
			boolean reported = false;
			InactivationIndicator i = c.getInactivationIndicator();
			for (AssociationEntry a : c.getAssociationEntries(ActiveState.ACTIVE, true)) {
				String assocType = SnomedUtils.getAssociationType(a);
				Concept assocValue = gl.getConcept(a.getTargetComponentId());
				report(c, i, assocType, assocValue);
				reported = true;
			}
			if (!reported) {
				report(c, i , "N/A");
			}
			countIssue(c);
		}
	}
	
	private boolean inScope(Concept c) {
		//We want inactive concepts modified either in the current release cycle or in the 
		//latest release if we're looking at a particular release package
		//Optionally for a specific semantic tag
		//Also optionally, unpromoted changes only
		return !c.isActiveSafely()
				&& hasRequiredSemTag(c)
				&& ((thisEffectiveTime == null && StringUtils.isEmpty(c.getEffectiveTime()) ||
					(thisEffectiveTime != null && thisEffectiveTime.equals(c.getEffectiveTime()))))
				&& (!unpromotedChangesOnly || unpromotedChangesHelper.hasUnpromotedChange(c));
	}
	
	private boolean hasRequiredSemTag(Concept c) {
		//If we didn't specify a filter, then it's in
		if (StringUtils.isEmpty(semtagFilter)) {
			return true;
		}
		
		String semTag = SnomedUtilsBase.deconstructFSN(c.getFsn(), true)[1];
		return semTag != null && semTag.contains(semtagFilter);
	}

	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException {
		prevRelease = getJobRun().getParamValue(RELEASE);
		if (!StringUtils.isEmpty(prevRelease)) {
			LOGGER.info("Loading previously published package: {}", prevRelease);
			getProject().setKey(prevRelease);
			super.loadProjectSnapshot(fsnOnly);
			thisEffectiveTime = gl.getCurrentEffectiveTime();
			LOGGER.info("Detected this effective time as {}", thisEffectiveTime);
		} else {
			super.loadProjectSnapshot(fsnOnly);
		}
	};
	
	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		//filter for appropriate scope at the same time - avoids problems with FSNs without semtags
		return superSet.stream()
		.filter (this::inScope)
		.sorted(SnomedUtils::compareSemTagFSN)
		.toList();
	}
	
}
