package org.ihtsdo.termserver.scripting.reports.managedService;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
import org.apache.commons.lang.StringUtils;

public class UntranslatedConcepts extends TermServerReport implements ReportClass {
	private String intEffectiveTime;
	boolean includeLegacyIssues = false;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<<404684003 |Clinical finding (finding)|");
		params.put(INCLUDE_ALL_LEGACY_ISSUES, "Y");
		TermServerScript.run(UntranslatedConcepts.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA Reports
		includeLegacyIssues = run.getParameters().getMandatoryBoolean(INCLUDE_ALL_LEGACY_ISSUES);
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Untranslated Concepts report cannot be run against MAIN");
		}
	}

	@Override
	public void postInit() throws TermServerScriptException {
		
		if (project.getMetadata() != null && project.getMetadata().getDependencyRelease() != null) {
			intEffectiveTime = project.getMetadata().getDependencyRelease();
		} else {
			throw new TermServerScriptException ("MS Project expected. " + project.getKey() + " is not configured with a dependency release effectiveTime");
		}
		
		String[] columnHeadings = new String[] {
			"Id, FSN, SemTag, Concept Effective Time, English Preferred Term (acceptability), Case Significance"};

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
				.add(ECL)
					.withType(JobParameter.Type.ECL)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.MS_RELEASE_VALIDATION))
				.withName("Untranslated Concepts")
				.withDescription("This report lists concepts (optionally filtered by ECL) which have no translation - " +
				"specifically no descriptions in the default module of the project.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		Collection<Concept> conceptsOfInterest;

		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = findConcepts(subsetECL);
		} else {
			conceptsOfInterest = gl.getAllConcepts();
		}
		
		for (Concept concept : scopeAndSort(conceptsOfInterest)) {
			StringBuilder englishPreferredTerm = new StringBuilder();
			StringBuilder caseSignificance = new StringBuilder();
            List<Description> descriptions = concept.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);

			for(Description description : descriptions) {
				if (!caseSignificance.isEmpty()) {
					englishPreferredTerm.append("\n");
					caseSignificance.append("\n");
				}

				englishPreferredTerm.append(description.getTerm());
				caseSignificance.append(SnomedUtils.translateCaseSignificanceFromEnum(description.getCaseSignificance()));
			}

			report(concept, concept.getEffectiveTime(), englishPreferredTerm, caseSignificance);
			countIssue(concept);
		}
	}
	
	private boolean inScope(Concept c) {
		//For this report we're interested in International Concepts 
		//(optionally in the last (dependency) release) which have no translations 
		//in the target module
		return (c.isActive()
			&& SnomedUtils.inModule(c, INTERNATIONAL_MODULES)
			&& (c.getEffectiveTime() == null || c.getEffectiveTime().equals(intEffectiveTime) || includeLegacyIssues)
			&& !hasTranslation(c));
	}
	
	private boolean hasTranslation(Concept c) {
		return !StringUtils.isEmpty(getTranslations(c));
	}

	private String getTranslations(Concept c) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
			.filter(this::inScope)
			.map(Description::getTerm)
			.collect(Collectors.joining(", \n"));
	}

	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		//We're going to sort on top level hierarchy, then alphabetically
		//filter for appropriate scope at the same time - avoids problems with FSNs without semtags
		return superSet.stream()
			.filter (this::inScope)
			.sorted(SnomedUtils::compareSemTagFSN)
			.toList();
	}
}
