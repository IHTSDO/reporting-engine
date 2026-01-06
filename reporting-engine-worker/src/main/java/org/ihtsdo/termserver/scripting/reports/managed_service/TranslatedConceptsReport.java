package org.ihtsdo.termserver.scripting.reports.managed_service;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
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

	Set<String> expectedLanguages = new HashSet<>();
	boolean includeIntConcepts = false;
	boolean includeInactiveConcepts = false;
	private boolean verboseOutput = true;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		params.put(EXTENSION_CONCEPTS_ONLY, "false");
		params.put(INCLUDE_INACTIVE_CONCEPTS, "true");
		TermServerScript.run(TranslatedConceptsReport.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"); //Managed Service
		subsetECL = run.getParamValue(ECL);
		includeIntConcepts = !run.getParamBoolean(EXTENSION_CONCEPTS_ONLY);
		includeInactiveConcepts = run.getParamBoolean(INCLUDE_INACTIVE_CONCEPTS);
		super.init(run);
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Translated Concepts report cannot be run against MAIN");
		}
	}

	@Override
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
				"SCTID, FSN, SemTag, Descriptions",
				"SCTID, FSN, SemTag, Text Definitions"
		};
		if (verboseOutput) {
			columnHeadings = new String[] {
					"SCTID, FSN, SemTag, Lang, DescriptionId, EffectiveTime, Term",
					"SCTID, FSN, SemTag, Lang, TextDefinitionId, EffectiveTime, Term"
			};
		}
		String[] tabNames = new String[] {	
				"Descriptions", "Text Definitions"};
		super.postInit(tabNames, columnHeadings);
	}
	
	private Set<String> getLanguagesFromDescriptions() {
		return gl.getAllConcepts().parallelStream()
		.flatMap(c -> c.getDescriptions().stream())
		.map(Description::getLang)
		.collect(Collectors.toSet());
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(Type.ECL)
				.add(EXTENSION_CONCEPTS_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(INCLUDE_INACTIVE_CONCEPTS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Translated Concepts")
				.withDescription("This reports lists all descriptions in the configured language(s)")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.withExpectedDuration(30)
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
		
		for (Concept c : scopeAndSort(conceptsOfInterest)) {
			outputConceptDescriptions(c);
			countIssue(c);
		}
	}

	private void outputConceptDescriptions(Concept c) throws TermServerScriptException {
		if (verboseOutput) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (expectedLanguages.contains(d.getLang())) {
					int reportIdx = DescriptionType.TEXT_DEFINITION.equals(d.getType()) ? SECONDARY_REPORT : PRIMARY_REPORT;
					report(reportIdx, c, d.getLang(), d.getId(), d.getEffectiveTimeSafely(), d.getTerm());
				}
			}
		} else {
			String descriptions = c.getDescriptions(ActiveState.ACTIVE, List.of(DescriptionType.FSN, DescriptionType.SYNONYM)).stream()
					.filter(Component::isActiveSafely)
					.filter(d -> expectedLanguages.contains(d.getLang()))
					.map(Description::toString)
					.collect(Collectors.joining(",\n"));
			report(PRIMARY_REPORT, c, descriptions);

			String textDefinitions = c.getDescriptions(ActiveState.ACTIVE, List.of(DescriptionType.TEXT_DEFINITION)).stream()
					.filter(Component::isActiveSafely)
					.filter(d -> expectedLanguages.contains(d.getLang()))
					.map(Description::toString)
					.collect(Collectors.joining(",\n"));
			if (!textDefinitions.isEmpty()) {
				report(SECONDARY_REPORT, c, textDefinitions);
			}
		}
	}

	private List<Concept> scopeAndSort(Collection<Concept> superSet) {
		return superSet.stream()
		.filter (this::inScope)
		.sorted(SnomedUtils::compareSemTagFSN)
		.toList();
	}
	
	private boolean inScope(Concept c) {
		return ((includeInactiveConcepts || c.isActive()) 
			&& (includeIntConcepts || !SnomedUtils.isInternational(c))
			&& !getLanguages(c, false).isEmpty());
	}

	private Set<String> getLanguages(Concept c, boolean includeEnglish) {
		return c.getDescriptions(ActiveState.ACTIVE).stream()
				.map(Description::getLang)
				.filter(s -> (includeEnglish || !s.equals("en")))
				.collect(Collectors.toSet());
	}
}
