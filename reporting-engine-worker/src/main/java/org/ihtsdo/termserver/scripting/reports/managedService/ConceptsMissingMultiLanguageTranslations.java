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

public class ConceptsMissingMultiLanguageTranslations extends TermServerReport implements ReportClass {
	
	private static final String EXTENSION_CONCEPTS_ONLY = "Extension Concepts Only";
	//private static final String INCLUDE_UNTRANSLATED_CONCEPTS = "Include untranslated concepts";
	private static final String INCLUDE_INACTIVE_CONCEPTS = "Include inactive concepts";
	Set<String> expectedLanguages = new HashSet<>();
	boolean includeIntConcepts = true;
	boolean includeUntranslatedConcepts = false;
	boolean includeInactiveConcepts = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(EXTENSION_CONCEPTS_ONLY, "false");
		//params.put(INCLUDE_UNTRANSLATED_CONCEPTS, "false");
		params.put(INCLUDE_INACTIVE_CONCEPTS, "false");
		TermServerReport.run(ConceptsMissingMultiLanguageTranslations.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
		getArchiveManager().setRunIntegrityChecks(false);
		subsetECL = run.getParamValue(ECL);
		includeIntConcepts = !run.getParamBoolean(EXTENSION_CONCEPTS_ONLY);
		//includeUntranslatedConcepts=run.getParamBoolean(INCLUDE_UNTRANSLATED_CONCEPTS);
		includeUntranslatedConcepts = false;
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
			//It might be that we have a single language entry eg "requiredLanguageRefset.da": "554461000005103"
			//Which the Metadata object just can't handle.  Fall back to examining all descriptions
			expectedLanguages = getLanguagesFromDescriptions();
			expectedLanguages.remove("en");
		}
		
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Missing Translation(s), ModuleId"};
		String[] tabNames = new String[] {	
				"Concepts missing translations"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	private Set<String> getLanguagesFromDescriptions() {
		return gl.getAllConcepts().parallelStream()
		.flatMap(c -> c.getDescriptions().stream())
		.map(d -> d.getLang())
		.collect(Collectors.toSet());
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL)
				.add(EXTENSION_CONCEPTS_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				//.add(INCLUDE_UNTRANSLATED_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(INCLUDE_INACTIVE_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.MS_RELEASE_VALIDATION))
				.withName("Concepts Missing Multi-Language Translations")
				.withDescription("This reports is for extensions that use multiple languages. It allows finding concepts that are missing a translation in at least one of the extension languages. To see a list of concepts without any translation, please refer to the untranslated concepts report.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
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
				report (c, missLangStr, gl.getConcept(c.getModuleId()).toStringPref());
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
