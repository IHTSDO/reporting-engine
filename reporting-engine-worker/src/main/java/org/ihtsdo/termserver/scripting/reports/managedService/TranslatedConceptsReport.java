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
/**
 * RP-548 / MSSP-1306
 */
public class TranslatedConceptsReport extends TermServerReport implements ReportClass {
	
	private static final String EXTENSION_CONCEPTS_ONLY = "Extension Concepts Only";
	private static final String INCLUDE_INACTIVE_CONCEPTS = "Include inactive concepts";
	//private static final String VERBOSE_OUTPUT = "Verbose Output";
	
	Set<String> expectedLanguages = new HashSet<>();
	boolean includeIntConcepts = true;
	boolean includeInactiveConcepts = false;
	private boolean verboseOutput = true;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(EXTENSION_CONCEPTS_ONLY, "false");
		params.put(INCLUDE_INACTIVE_CONCEPTS, "true");
		//params.put(VERBOSE_OUTPUT, "true");
		TermServerReport.run(TranslatedConceptsReport.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
		//getArchiveManager().setRunIntegrityChecks(false);
		subsetECL = run.getParamValue(ECL);
		includeIntConcepts = !run.getParamBoolean(EXTENSION_CONCEPTS_ONLY);
		includeInactiveConcepts = run.getParamBoolean(INCLUDE_INACTIVE_CONCEPTS);
		//verboseOutput = run.getParameters().getMandatoryBoolean(VERBOSE_OUTPUT);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Translated Concepts report cannot be run against MAIN");
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
		
		String[] columnHeadings = new String[] {"SCTID, FSN, SemTag, Descriptions"};
		if (verboseOutput) {
			columnHeadings = new String[] {"SCTID, FSN, SemTag, language, DescriptionId, term"};
		}
		String[] tabNames = new String[] {	
				"Translated Concepts"};
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
				.add(INCLUDE_INACTIVE_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				//.add(VERBOSE_OUTPUT).withType(JobParameter.Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Translated Concepts")
				.withDescription("This reports lists all descriptions in the configured language(s)")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withExpectedDuration(30)
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
			if (verboseOutput) {
				for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
					if (expectedLanguages.contains(d.getLang())) {
						report (c, d.getLang(), d.getId(), d.getTerm());
					}
				}
			} else {
				String detail = c.getDescriptions(ActiveState.ACTIVE).stream()
						.filter(d -> d.isActive())
						.filter(d -> expectedLanguages.contains(d.getLang()))
						.map(d -> d.toString())
						.collect(Collectors.joining(",\n"));
				report (c, detail);
			}
			countIssue(c);
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
			&& getLanguages(c, false).size() > 0);
	}

	private Set<String> getLanguages(Concept c, boolean includeEnglish) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
				.map(d -> d.getLang())
				.filter(s -> (includeEnglish || !s.equals("en")))
				.collect(Collectors.toSet());
	}
}
