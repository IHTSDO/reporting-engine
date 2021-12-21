package org.ihtsdo.termserver.scripting.reports.managedService;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.scheduler.domain.JobParameter.Type;
import org.snomed.otf.script.dao.ReportSheetManager;

public class ConceptsMissingTranslations extends TermServerReport implements ReportClass {
	
	private static final String INCLUDE_INT_CONCEPTS = "Include INT concepts";
	private static final String INCLUDE_UNTRANSLATED_CONCEPTS = "Include untranslated concepts";
	private static final String INCLUDE_INACTIVE_CONCEPTS = "Include inactive concepts";
	Set<String> expectedLanguages = new HashSet<>();
	boolean includeIntConcepts = true;
	boolean includeUntranslatedConcepts = false;
	boolean includeInactiveConcepts = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(INCLUDE_INT_CONCEPTS, "true");
		params.put(INCLUDE_UNTRANSLATED_CONCEPTS, "false");
		params.put(INCLUDE_INACTIVE_CONCEPTS, "false");
		TermServerReport.run(ConceptsMissingTranslations.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "15WXT1kov-SLVi4cvm2TbYJp_vBMr4HZJ"; //Release QA Reports
		getArchiveManager().setRunIntegrityChecks(false);
		subsetECL = run.getParamValue(ECL);
		includeIntConcepts = run.getParamBoolean(INCLUDE_INT_CONCEPTS);
		includeUntranslatedConcepts=run.getParamBoolean(INCLUDE_UNTRANSLATED_CONCEPTS);
		includeInactiveConcepts = run.getParamBoolean(INCLUDE_INACTIVE_CONCEPTS);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Concepts Missing Translations report cannot be run against MAIN");
		}
	}
	
	public void postInit() throws TermServerScriptException {
		if (project.getMetadata() != null && project.getMetadata().getRequiredLanguageRefsets() != null) {
			expectedLanguages = project.getMetadata().getLangLangRefsetMapping().keySet();
		} else {
			throw new TermServerScriptException ("MS Project expected. " + project.getKey() + " is not configured with a required language reference sets.");
		}
		
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Missing Translation(s), ModuleId"};
		String[] tabNames = new String[] {	
				"Concepts missing translations"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL)
				.add(INCLUDE_INT_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.add(INCLUDE_UNTRANSLATED_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(INCLUDE_INACTIVE_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("Concepts Missing Translations")
				.withDescription("This report lists concepts which are missing translations.  Optionally including international concepts, and optionally those that have not been translated at all.")
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
			Set<String> missingLanguages = new HashSet<>(expectedLanguages);
			missingLanguages.removeAll(getLanguages(c, false));
			if (missingLanguages.size() > 0) {
				String missLangStr = String.join(", ", missingLanguages);
				report (c, missLangStr, c.getModuleId());
				countIssue(c);
			}
		}
	}
	
	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		return superSet.stream()
		.filter (c -> inScope(c))
		.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
		.collect(Collectors.toList());
	}
	
	private boolean inScope(Concept c) {
		return ((includeInactiveConcepts || c.isActive()) 
			&& (includeIntConcepts || !SnomedUtils.isInternational(c))
			&& (includeUntranslatedConcepts || getLanguages(c, false).size() > 0));
	}

	private Set<String> getLanguages(Concept c, boolean includeEnglish) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
				.map(d -> d.getLang())
				.filter(s -> (includeEnglish || !s.equals("en")))
				.collect(Collectors.toSet());
	}
}
