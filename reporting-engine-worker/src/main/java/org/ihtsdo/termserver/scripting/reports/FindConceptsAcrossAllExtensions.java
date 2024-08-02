package org.ihtsdo.termserver.scripting.reports;

import org.ihtsdo.otf.exception.TermServerScriptException;

import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.client.TermServerClient;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.Module;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.util.*;
import java.util.stream.*;

public class FindConceptsAcrossAllExtensions extends TermServerReport implements ReportClass {

	private static final String browserURL = "https://browser.ihtsdotools.org/snowstorm/snomed-ct";

	private List<CodeSystem> codeSystems;
	private List<String> internationalModules;

	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(ECL, "< 195967001 |Asthma (disorder)|");
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
		//In fact, we're going to wipe the graph loader so that we don't
		//try to use our local copy when we recover ECL results from other extensions
		gl.reset();
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
				.withDescription("This report lists all concepts that match the specified ECL across all extensions.  The issue count here will be for the count of concepts outside of the International Edition.")
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
					if (!cs.getShortName().equals("SNOMEDCT")) {
						countIssue(c);
					}
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
