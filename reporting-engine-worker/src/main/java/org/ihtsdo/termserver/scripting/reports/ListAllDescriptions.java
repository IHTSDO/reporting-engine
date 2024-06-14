package org.ihtsdo.termserver.scripting.reports;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;
/**
 * See FD#25496
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ListAllDescriptions extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(ListAllDescriptions.class);

	private static String DESCRIPTION_PER_LINE = "Description Per Line";
	private static String VERBOSE_MODE = "Verbose Mode";
	private Set<Concept> alreadyReported = new HashSet<>();
	private boolean descriptionPerLine = false;
	private boolean verboseMode = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "^ 1157358007 |International Classification for Nursing Practice reference set|"); 
		params.put(VERBOSE_MODE, "true");
		TermServerReport.run(ListAllDescriptions.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc Reports
		additionalReportColumns="FSN, SemTag, Defn, Parents, Description";
		
		descriptionPerLine = run.getParamBoolean(DESCRIPTION_PER_LINE);
		verboseMode = run.getParamBoolean(VERBOSE_MODE);
		
		if (verboseMode) {
			additionalReportColumns="FSN, SemTag, Defn, Parents, Langs Present, Desc Type, Lang, Preferred In, Desc Id, Term";
		}
		super.init(run);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(DESCRIPTION_PER_LINE).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.add(VERBOSE_MODE).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("List all Descriptions")
				.withDescription("This report lists all descriptions in a given hierarchy." +
						"The issues count will show the number of concepts reported." + 
						"Description per line will do just that, and verbose mode will " +
						"repeat the concept details on every line, and split the descriptions " +
						"up into their component fields - for easy filtering.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	public void runJob() throws TermServerScriptException {
		List<Concept> concepts = SnomedUtils.sortFSN(findConcepts(subsetECL));
		for (Concept concept : concepts) {
			listDescriptions(concept);
		}
	}

	private void listDescriptions(Concept c) throws TermServerScriptException {
		//Have we already seen this concept?
		if (alreadyReported.contains(c)) {
			return;
		}
		
		String defn = SnomedUtils.translateDefnStatus(c.getDefinitionStatus());
		String parents = getParentsStr(c);
		
		List<Description> descriptions = c.getDescriptions(ActiveState.ACTIVE);
		SnomedUtils.prioritise(descriptions);
		
		if (verboseMode) {
			String langsPresent = descriptions.stream()
					.map(Description::getLang)
					.sorted()
					.distinct()
					.collect(Collectors.joining(","));
			
			for (Description d : descriptions) {
				report(c, defn, parents,
					langsPresent,
					d.getType(),
					d.getLang(),
					getPreferredIn(d),
					d.getId(),
					d.getTerm());
			}
		} else if (descriptionPerLine) {
			report(c, defn, parents);
			for (Description d : descriptions) {
				report((Concept)null, "", "", d);
			}
		} else {
			String descriptionStr = descriptions.stream()
					.map(d -> d.getTerm())
					.collect(Collectors.joining(",\n"));
			report(c, defn, parents, descriptionStr);
		}
		
		incrementSummaryInformation("Concepts reported");
		countIssue(c);
	}

	private String getPreferredIn(Description d) {
		return d.getLangRefsetEntries().stream()
				.filter(LangRefsetEntry::isActive)
				.filter(l -> l.getAcceptabilityId().equals(SCTID_PREFERRED_TERM))
				.map(l -> l.getRefsetId())
				.sorted()
				.collect(Collectors.joining(",\n"));
	}

	private String getParentsStr(Concept c) {
		Set<Concept> parents = c.getParents(CharacteristicType.INFERRED_RELATIONSHIP);
		return parents.stream().map(p->p.toString())
				.collect(Collectors.joining(",\n"));
	}

}
