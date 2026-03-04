package org.ihtsdo.termserver.scripting.reports;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.LanguageHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
/**
 * See FD#25496
 */
public class ListAllDescriptions extends TermServerReport implements ReportClass {

	private static final String DESCRIPTION_PER_LINE = "Description Per Line";
	private static final String INCLUDE_INACTIVE_DESCRIPTIONS = "Include inactive descriptions";

	private boolean includeInactiveDescriptions = false;
	private boolean descriptionPerLine = false;
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		//params.put(SUB_HIERARCHY, "43959009 |Cataract of eye due to diabetes mellitus (disorder)|");
		//params.put(SUB_HIERARCHY, "38199008 |Tooth structure (body structure)|");
		//params.put(SUB_HIERARCHY, "25093002 |Disorder of eye due to diabetes mellitus (disorder)|");
		//params.put(SUB_HIERARCHY, "19598007 |Generalized epilepsy (disorder)|");
		//params.put(SUB_HIERARCHY, "84757009 |Epilepsy (disorder)|"); 
		params.put(ECL, "<<84757009 |Epilepsy (disorder)|");
		TermServerScript.run(ListAllDescriptions.class, args, params);
	}

	@Override
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"); //Ad-hoc Reports

		descriptionPerLine = run.getParamBoolean(DESCRIPTION_PER_LINE);
		includeInactiveDescriptions = run.getParamBoolean(INCLUDE_INACTIVE_DESCRIPTIONS);
		super.init(run);
	}

	@Override
	public void postInit() throws TermServerScriptException {

		String[] columnHeadings = new String[] {
				"SCTID, FSN, SemTag, Defn, Parents, Descriptions"
		};
		if (descriptionPerLine) {
			columnHeadings = new String[] {
					"SCTID, FSN, SemTag, Defn, Languages, DescriptionId, EffectiveTime, Active, ModuleId, Lang, Acceptability, Term, CaseSignificance"
			};
		}
		String[] tabNames = new String[] {
				"Descriptions"
		};
		super.postInit(tabNames, columnHeadings);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(INCLUDE_INACTIVE_DESCRIPTIONS).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(DESCRIPTION_PER_LINE).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Descriptions")
				.withDescription("This report lists all descriptions in a given hierarchy. " +
						"The issues count will show the number of concepts reported.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}

	@Override
	public void runJob() throws TermServerScriptException {
		List<Concept> concepts = SnomedUtils.sortFSN(findConcepts(subsetECL));
		for (Concept concept : concepts) {
			listDescriptions(concept);
		}
	}

	private void listDescriptions(Concept c) throws TermServerScriptException {

		String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		
		List<Description> descriptions = includeInactiveDescriptions ? c.getDescriptions(ActiveState.BOTH) : c.getDescriptions(ActiveState.ACTIVE);
		SnomedUtils.prioritise(descriptions);
		
		if (descriptionPerLine) {
			Set<String> allLanguages = descriptions.stream().map(Description::getLang).collect(Collectors.toSet());

			for (Description d : descriptions) {
				report(c, defn, allLanguages,
						d.getId(),
						d.getEffectiveTimeSafely(),
						d.isActiveSafely() ? "Y" : "N",
						d.getModuleId(),
						d.getLang(),
						LanguageHelper.toString(d.getAcceptabilityMap()),
						d.getTerm(),
						d.getCaseSignificance());
			}
		} else {
			String parents = getParentsStr(c);

			String descriptionStr = descriptions.stream()
					.map(d -> (d.isActiveSafely() ? "" : "*") + d.getTerm())
					.collect(Collectors.joining(",\n"));
			report(c, defn, parents, descriptionStr);
		}
		
		incrementSummaryInformation("Concepts reported");
		countIssue(c);
	}

	private String getParentsStr(Concept c) {
		Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		return parents.stream().map(Concept::toString)
				.collect(Collectors.joining(",\n"));
	}

}
