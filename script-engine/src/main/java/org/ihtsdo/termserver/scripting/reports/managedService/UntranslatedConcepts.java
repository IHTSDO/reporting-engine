package org.ihtsdo.termserver.scripting.reports.managedService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.dao.ReportSheetManager;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.springframework.util.StringUtils;

public class UntranslatedConcepts extends TermServerReport implements ReportClass {
	
	private String intEffectiveTime;
	boolean includeLegacyIssues = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<<404684003 |Clinical finding (finding)|");
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "Y");
		TermServerReport.run(UntranslatedConcepts.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA Reports
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Untranslated Concepts report cannot be run against MAIN");
		}
	}
	
	public void postInit() throws TermServerScriptException {
		
		if (project.getMetadata() != null && project.getMetadata().getDependencyRelease() != null) {
			intEffectiveTime = project.getMetadata().getDependencyRelease();
		} else {
			throw new TermServerScriptException ("MS Project expected. " + project.getKey() + " is not configured with a dependency release effectiveTime");
		}
		
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Concept Effective Time"};
		String[] tabNames = new String[] {	
				"Untranslated Concepts"};
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
				.withName("Untranslated Concepts")
				.withDescription("This report lists concepts (optionally filtered by ECL) which have no translation - " +
				"specifically no descriptions in the default module of the project.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		for (Concept c : scopeAndSort(conceptsOfInterest)) {
			report(c, c.getEffectiveTime());
			countIssue(c);
		}
	}
	
	private boolean inScope(Concept c) {
		//For this report we're interested in International Concepts 
		//(optionally in the last (dependency) release) which have no translations 
		//in the target module
		return (c.isActive() 
			&& (c.getEffectiveTime().equals(intEffectiveTime) || includeLegacyIssues)
			&& !hasTranslation(c));
	}
	
	private boolean hasTranslation(Concept c) {
		return !StringUtils.isEmpty(getTranslations(c));
	}

	private String getTranslations(Concept c) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
			.filter(d -> inScope(d))
			.map(d -> d.getTerm())
			.collect(Collectors.joining(", \n"));
	}

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
