package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.springframework.util.StringUtils;

public class ConceptsInactivated extends TermServerReport implements ReportClass {
	
	static final String RELEASE = "Release Package";
	private String prevRelease;
	private String thisEffectiveTime;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(RELEASE, "dev_xSnomedCT_InternationalRF2_PREALPHA_20200731T120000Z.zip");
		TermServerReport.run(ConceptsInactivated.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		subHierarchyECL = run.getParamValue(ECL);
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
				.add(ECL).withType(JobParameter.Type.ECL).withDefaultValue("<< " + ROOT_CONCEPT)
				.add(RELEASE).withType(Type.STRING)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Inactivated Concepts")
				.withDescription("This report lists all concepts inactivated in the current release cycle along with the reason and historical association, " + 
				"optionally restricted to a subset defined by an ECL expression.  The issue count here is the total number of concepts inactivated." +
						" Also optional is specifying a previously published release package to run against.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest;
		if (subHierarchyECL != null && !subHierarchyECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subHierarchyECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		for (Concept c : scopeAndSort(conceptsOfInterest)) {
			boolean reported = false;
			InactivationIndicator i = c.getInactivationIndicator();
			for (AssociationEntry a : c.getAssociations(ActiveState.ACTIVE, true)) {
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
		return !c.isActive() && ( (thisEffectiveTime == null && StringUtils.isEmpty(c.getEffectiveTime()) ||
				(thisEffectiveTime != null && thisEffectiveTime.equals(c.getEffectiveTime()))));
	}
	
	@Override
	protected void loadProjectSnapshot(boolean fsnOnly) throws TermServerScriptException, InterruptedException, IOException {
		prevRelease = getJobRun().getParamValue(RELEASE);
		if (prevRelease != null) {
			getProject().setKey(prevRelease);
			super.loadProjectSnapshot(fsnOnly);
			thisEffectiveTime = gl.getCurrentEffectiveTime();
			info ("Detected this effective time as " + thisEffectiveTime);
		} else {
			super.loadProjectSnapshot(fsnOnly);
		}
	};
	
	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		//filter for appropriate scope at the same time - avoids problems with FSNs without semtags
		return superSet.stream()
		.filter (c -> inScope(c))
		.sorted((c1, c2) -> compareSemTagFSN(c1,c2))
		.collect(Collectors.toList());
	}

	private int compareSemTagFSN(Concept c1, Concept c2) {
		String[] fsnSemTag1 = SnomedUtils.deconstructFSN(c1.getFsn());
		String[] fsnSemTag2 = SnomedUtils.deconstructFSN(c2.getFsn());
		
		if (fsnSemTag1[1] == null || fsnSemTag2[1] == null) {
			System.out.println("FSN Encountered without semtag: " + fsnSemTag1[1] == null ? c1 : c2);
			return fsnSemTag1[0].compareTo(fsnSemTag2[0]);
		} 
		
		if (fsnSemTag1[1].equals(fsnSemTag2[1])) {
			return fsnSemTag1[0].compareTo(fsnSemTag2[0]);
		}
		return fsnSemTag1[1].compareTo(fsnSemTag2[1]);
	}
	
}
