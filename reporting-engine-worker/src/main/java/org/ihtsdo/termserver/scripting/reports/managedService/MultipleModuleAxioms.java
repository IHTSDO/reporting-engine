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
 * RP-456 Report to find concepts in the core module that have active
 * axioms in the target module
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultipleModuleAxioms extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(MultipleModuleAxioms.class);

	public static String FILTER_CORE = "Ignore original core concepts";
	private boolean ignoreOriginalCore = true;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(FILTER_CORE, "true");
		TermServerReport.run(MultipleModuleAxioms.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1mvrO8P3n94YmNqlWZkPJirmFKaFUnE0o"; //Managed Service
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		
		ignoreOriginalCore = run.getParameters().getMandatoryBoolean(FILTER_CORE);
		
		if (project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("Multiple Module Axioms report cannot be run against MAIN");
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Concept Module, Core Axiom ID, EffectiveTime, Non-core Axiom Id, EffectiveTime"};
		String[] tabNames = new String[] {	
				"Concepts with Axioms in Multiple Modules"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(FILTER_CORE).withType(Type.BOOLEAN).withDefaultValue(true)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.MS_RELEASE_VALIDATION))
				.withName("Multiple Module Axioms")
				.withDescription("This report lists concepts with active axioms in multiple modules")
				.withParameters(params)
				.withTag(MS)
				.withTag(INT)
				.withProductionStatus(ProductionStatus.PROD_READY)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		for (Concept c : gl.getAllConcepts()) {
			if (ignoreOriginalCore && isOriginalCore(c)) {
				continue;
			}
			List<AxiomEntry> coreAxioms = c.getAxiomEntries(ActiveState.ACTIVE, false)
					.stream()
					.filter(a -> SnomedUtils.inModule(a, INTERNATIONAL_MODULES))
					.collect(Collectors.toList());
			List<AxiomEntry> nonCoreAxioms = c.getAxiomEntries(ActiveState.ACTIVE, false)
					.stream()
					.filter(a -> !SnomedUtils.inModule(a, INTERNATIONAL_MODULES))
					.collect(Collectors.toList());
			if (coreAxioms.size() > 0 && nonCoreAxioms.size() > 0) {
				AxiomEntry firstCoreAxiom = coreAxioms.get(0);
				for (AxiomEntry a : nonCoreAxioms) {
					report (c, c.getModuleId(), firstCoreAxiom.getId(), firstCoreAxiom.getEffectiveTime(), a.getId(), a.getEffectiveTime());
					countIssue(c);
				}
			}
		}
	}

	private boolean isOriginalCore(Concept c) {
		String id = c.getId();
		int coreIndicatorPos = id.length() - 3; //3rd character from the right
		return id.charAt(coreIndicatorPos) == '0';
	}
	
}
