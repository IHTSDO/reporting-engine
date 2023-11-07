package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;

import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.CodeSystem;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.*;

public class FindConceptsAcrossAllExtensions extends TermServerReport implements ReportClass {

	private static final SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd_HHmmss");
	private static final String browserURL = "https://browser.ihtsdotools.org/snowstorm/snomed-ct";
	private static final Logger LOGGER = LoggerFactory.getLogger(FindConceptsAcrossAllExtensions.class);

	private List<CodeSystem> codeSystems;
	private List<String> internationalModules;

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "<< 73632009 |Laparoscopy (procedure)|");
		TermServerReport.run(FindConceptsAcrossAllExtensions.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1F-KrAwXrXbKj5r-HBLM0qI5hTzv-JgnU"; //Ad-hoc
		super.init(run);
		tsClient = new TermServerClient(browserURL, getAuthenticatedCookie());
		codeSystems = tsClient.getCodeSystems();
		internationalModules = codeSystems.stream()
				.filter(cs -> cs.getShortName().equals("SNOMEDCT"))
				.findFirst().get()
				.getModules()
				.stream()
				.map(Module::getConceptId)
				.collect(Collectors.toList());
	}

	public void loadProjectSnapshot(boolean includeFSNs) throws TermServerScriptException {
		//Nothing needed here.  The ECL return will have everything we need.
	}
	
	public void postInit() throws TermServerScriptException {
		List<String> tabList = codeSystems.stream()
				.map(CodeSystem::getShortName)
				.map(sn -> sn.replace("SNOMEDCT-", ""))
				.map(sn -> sn.replace("SNOMEDCT", "INT"))
				.collect(Collectors.toList());

		tabList.add(0, "Summary");

		String[] columnHeadings = IntStream.rangeClosed(1, tabList.size())
				.mapToObj(i -> "SCTID, FSN, SemTag,")
				.toArray(String[]::new);
		columnHeadings[0] = "Extension, count";
		postInit(tabList.toArray(String[]::new), columnHeadings, false);
		
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.STRING).withMandatory()
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.ADHOC_QUERIES))
				.withName("Find Concepts Across All Extensions")
				.withDescription("This report lists all concepts that match the specified ECL across all extensions")
				.withParameters(params)
				.withProductionStatus(Job.ProductionStatus.PROD_READY)
				.withTag(INT)
				.build();
	}

	@Override
	public String getReportName() {
		return "FindConceptsAcrossAllExtensions_" + subsetECL.replace(" ", "_");
	}
	
	public void runJob() throws TermServerScriptException {
		int tabIdx = 0;
		for (CodeSystem cs : codeSystems) {
			tabIdx++;

			if (cs.getLatestVersion() == null) {
				report(tabIdx, null, "No version found for " + cs.getShortName());
				continue;
			} else if (cs.getLatestVersion().getBranchPath() == null) {
				report(tabIdx, null, "No branch path found for " + cs.getShortName());
				continue;
			}
			overrideEclBranch = cs.getLatestVersion().getBranchPath();
			String eclForExtensionModules = addModuleFilter(subsetECL, cs);
			report(tabIdx, null,eclForExtensionModules);
			try {
				for (Concept c : findConcepts(eclForExtensionModules)) {
					report(tabIdx, c);
					issueSummaryMap.merge(cs.getName(), 1, Integer::sum);
				}
			} catch (Exception e) {
				report(tabIdx, null, "Failed to find concepts for " + cs.getShortName() + " due to " + e.getMessage());
			}
		}
		this.populateSummaryTabAndTotal(PRIMARY_REPORT);
	}

	private String addModuleFilter(String ecl, CodeSystem cs) {
		if (cs.getShortName().equals("SNOMEDCT")) {
			return ecl;
		}

		String moduleList = cs.getModules().stream()
				.map(Module::getConceptId)
				.filter(m -> !internationalModules.contains(m))
				.collect(Collectors.joining(" "));
		return ecl + " {{ C moduleId = (" + moduleList + ") }}";
	}

}
