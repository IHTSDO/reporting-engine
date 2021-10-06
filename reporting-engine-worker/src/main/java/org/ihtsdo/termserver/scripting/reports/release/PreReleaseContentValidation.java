package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;
import java.util.stream.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * FRI-254 A number of what were originally SQL queries now converted into a user-runnable
 * report
 * */
public class PreReleaseContentValidation extends HistoricDataUser implements ReportClass {
	
	List<Concept> allActiveConceptsSorted;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		//params.put(THIS_RELEASE, "SnomedCT_InternationalRF2_PRODUCTION_20200731T120000Z.zip");
		TermServerReport.run(PreReleaseContentValidation.class, args, params);
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(PREV_RELEASE).withType(JobParameter.Type.STRING)
				.add(THIS_RELEASE).withType(JobParameter.Type.STRING)
				.add(MODULES).withType(JobParameter.Type.STRING)
				.build();

		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_STATS))
				.withName("Pre-release Content Validation")
				.withDescription("A set of counts and informational queries originally run as SQL")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.build();
	}
	

	public void init (JobRun run) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		
		origProject = run.getProject();
		if (!StringUtils.isEmpty(run.getParamValue(THIS_RELEASE))) {
			projectName = run.getParamValue(THIS_RELEASE);
			run.setProject(projectName);
		}
		
		if (!StringUtils.isEmpty(run.getParamValue(MODULES))) {
			moduleFilter = Stream.of(run.getParamValue(MODULES).split(",", -1))
					.collect(Collectors.toList());
		}
		
		super.init(run);
	}

	public void postInit() throws TermServerScriptException {
		//Need to set the original project back, otherwise it'll get filtered
		//out by the security of which projects a user can see
		if (getJobRun() != null) {
			getJobRun().setProject(origProject);
		}
		
		String[] columnHeadings = new String[] {"Stat, count",
												"SCTID, FSN, SemTag, New Hierarchy, Old Hierarchy", 
												"SCTID, FSN, SemTag, New FSN, Old FSN, Difference",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag",
												"SCTID, FSN, SemTag"};
		String[] tabNames = new String[] {	"Summary Counts",
											"Hierarchy Switches", 
											"FSN Changes",
											"Query 3",
											"Query 4",
											"Query 5",
											"Query 6"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	public void runJob() throws TermServerScriptException {
		
		allActiveConceptsSorted = SnomedUtils.sortActive(gl.getAllConcepts());
		TransitiveClosure tc = gl.generateTransativeClosure();
		
		info ("Loading Previous Data");
		loadData(prevRelease);
		
		info("Reporting Top Level Hierarchy Switch");
		topLevelHierarchySwitch(tc);
		
		info("Checking for fsn changes");
		checkforFsnChange();
		
		
	}

	private void topLevelHierarchySwitch(TransitiveClosure tc) throws TermServerScriptException {
		for (Concept c : allActiveConceptsSorted) {
			try {
				//Was this concept in the previous release and if so, has it switched?
				Datum prevDatum = prevData.get(c.getId());
				if (prevDatum != null) {
					Concept topLevel = getHierarchy(tc, c);
					if (!prevDatum.hierarchy.equals(topLevel.getId())) {
						report (SECONDARY_REPORT, c, topLevel.toStringPref(), gl.getConcept(prevDatum.hierarchy).toStringPref());
					}
				}
			} catch (Exception e) {
				report (SECONDARY_REPORT, c, "Error recovering hierarchy: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}
	
	private void checkforFsnChange() throws TermServerScriptException {
		for (Concept c : allActiveConceptsSorted) {
			try {
				//Was this concept in the previous release and if so, has it switched?
				Datum prevDatum = prevData.get(c.getId());
				if (prevDatum != null) {
					if (!prevDatum.fsn.equals(c.getFsn())) {
						report (TERTIARY_REPORT, c, c.getFsn(), prevDatum.fsn, StringUtils.difference(c.getFsn(), prevDatum.fsn));
					}
				}
			} catch (Exception e) {
				report (TERTIARY_REPORT, c, "Error recovering FSN: " + ExceptionUtils.getExceptionCause("", e));
			}
		}
	}

}
