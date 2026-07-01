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
import org.jspecify.annotations.NonNull;
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
	private static final String PERCENTAGE_FORMAT = "%.1f%%";

	Set<String> expectedLanguages = new HashSet<>();
	boolean includeIntConcepts = false;
	boolean includeInactiveConcepts = false;
	private boolean verboseOutput = true;

	private Map<String, Map<Concept, Set<Concept>>> translationsByLangAndHierarchy = new LinkedHashMap<>();
	private Map<String, Map<Concept, Integer>> descCountByLangAndHierarchy = new LinkedHashMap<>();
	private Map<String, Integer> totalDescriptionsByLang = new LinkedHashMap<>();
	private List<Concept> topLevelHierarchies;
	private Map<Concept, Integer> hierarchyConceptCounts = new LinkedHashMap<>();
	private int totalActiveConcepts;
	
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

		String[] columnHeadings = getColumnHeadings();
		String[] tabNames = new String[]{"Descriptions", "Text Definitions", "Summary by Hierarchy"};
		super.postInit(tabNames, columnHeadings);

		topLevelHierarchies = new ArrayList<>(ROOT_CONCEPT.getChildren(CharacteristicType.INFERRED_RELATIONSHIP));
		topLevelHierarchies.sort(Comparator.comparing(Concept::getFsn));
		for (Concept topLevel : topLevelHierarchies) {
			int count = (int) gl.getDescendantsCache().getDescendants(topLevel).stream()
					.filter(Concept::isActive)
					.count() + (topLevel.isActiveSafely() ? 1 : 0);
			hierarchyConceptCounts.put(topLevel, count);
			totalActiveConcepts += count;
		}
	}

	private String @NonNull [] getColumnHeadings() {
		String activeModifier1 = "\n(Active Only)";
		String activeModifier2 = " Active Only";
		String activeModifier3 = "";
		if (includeInactiveConcepts) {
			activeModifier1 = "\n(including inactive concepts)";
			activeModifier2 = "";
			activeModifier3 = activeModifier1;
		}
		String summaryHeading = "SCTID, FSN, SemTag, Language, Translated Concepts%s, Total Descriptions, Total %sConcepts%s, %% of Hierarchy, %% of All Concepts"
				.formatted(activeModifier1, activeModifier2, activeModifier3);
		String[] columnHeadings;
		if (verboseOutput) {
			columnHeadings = new String[]{
					"SCTID, FSN, SemTag, Lang, DescriptionId, EffectiveTime, Term",
					"SCTID, FSN, SemTag, Lang, TextDefinitionId, EffectiveTime, Term",
					summaryHeading
			};
		} else {
			columnHeadings = new String[]{
					"SCTID, FSN, SemTag, Descriptions",
					"SCTID, FSN, SemTag, Text Definitions",
					summaryHeading
			};
		}
		return columnHeadings;
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
				.withDescription("This reports lists all active descriptions in the configured language(s), optionally a) including those on inactive concepts, and b) filtered by an ECL selection.")
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
		reportSummaryByHierarchy();
	}

	private void outputConceptDescriptions(Concept c) throws TermServerScriptException {
		Concept hierarchy = SnomedUtils.getHierarchy(gl, c);
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!expectedLanguages.contains(d.getLang())) {
				continue;
			}
			recordTranslation(c, d, hierarchy);
			if (verboseOutput) {
				int reportIdx = DescriptionType.TEXT_DEFINITION.equals(d.getType()) ? SECONDARY_REPORT : PRIMARY_REPORT;
				report(reportIdx, c, d.getLang(), d.getId(), d.getEffectiveTimeSafely(), d.getTerm());
			}
		}
		if (!verboseOutput) {
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

	private void recordTranslation(Concept c, Description d, Concept hierarchy) {
		if (hierarchy == null) {
			return;
		}
		String lang = d.getLang();
		translationsByLangAndHierarchy
				.computeIfAbsent(lang, k -> new LinkedHashMap<>())
				.computeIfAbsent(hierarchy, k -> new HashSet<>())
				.add(c);
		descCountByLangAndHierarchy
				.computeIfAbsent(lang, k -> new LinkedHashMap<>())
				.merge(hierarchy, 1, Integer::sum);
		totalDescriptionsByLang.merge(lang, 1, Integer::sum);
	}

	private void reportSummaryByHierarchy() throws TermServerScriptException {
		for (Map.Entry<String, Map<Concept, Set<Concept>>> entry : translationsByLangAndHierarchy.entrySet()) {
			String lang = entry.getKey();
			Map<Concept, Set<Concept>> conceptsByHierarchy = entry.getValue();
			Map<Concept, Integer> descsByHierarchy = descCountByLangAndHierarchy.getOrDefault(lang, Collections.emptyMap());

			int totalTranslated = conceptsByHierarchy.values().stream().mapToInt(Set::size).sum();
			int totalDescs = totalDescriptionsByLang.getOrDefault(lang, 0);

			report(TERTIARY_REPORT, ROOT_CONCEPT, lang, totalTranslated, totalDescs, totalActiveConcepts,
					"N/A", String.format(PERCENTAGE_FORMAT, 100.0 * totalTranslated / totalActiveConcepts));

			for (Concept hierarchy : topLevelHierarchies) {
				int translatedCount = conceptsByHierarchy.getOrDefault(hierarchy, Collections.emptySet()).size();
				int descCount = descsByHierarchy.getOrDefault(hierarchy, 0);
				int hierarchyTotal = hierarchyConceptCounts.getOrDefault(hierarchy, 0);
				report(TERTIARY_REPORT, hierarchy, lang, translatedCount, descCount, hierarchyTotal,
						hierarchyTotal > 0 ? String.format(PERCENTAGE_FORMAT, 100.0 * translatedCount / hierarchyTotal) : "N/A",
						String.format(PERCENTAGE_FORMAT, 100.0 * translatedCount / totalActiveConcepts));
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
