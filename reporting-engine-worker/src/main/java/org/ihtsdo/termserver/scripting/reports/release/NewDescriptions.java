package org.ihtsdo.termserver.scripting.reports.release;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import com.google.common.util.concurrent.AtomicLongMap;

/**
 * RP-414
 */
public class NewDescriptions extends TermServerReport implements ReportClass {
	
	private static final String UNPROMOTED_CHANGES_ONLY = "Unpromoted changes only";
	
	Logger logger = LoggerFactory.getLogger(this.getClass());
	AtomicLongMap<String> componentCounts = AtomicLongMap.create();
	boolean unpromotedChangesOnly = false;
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		Map<String, String> params = new HashMap<>();
		params.put(UNPROMOTED_CHANGES_ONLY, "true");
		TermServerReport.run(NewDescriptions.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setPopulateReleasedFlag(true);
		ReportSheetManager.targetFolderId = "1od_0-SCbfRz0MY-AYj_C0nEWcsKrg0XA"; //Release Stats
		subsetECL = run.getParamValue(ECL);
		super.init(run);
		
		if (run.getParameters().getMandatoryBoolean(UNPROMOTED_CHANGES_ONLY) && project.getKey().equals("MAIN")) {
			throw new TermServerScriptException("UnpromotedChangesOnly makes no sense when running against MAIN");
		}
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {
				"Id, FSN, SemTag, Lang, Term, Description",
				"Id, FSN, SemTag, Lang, Term, Text Definition"};
		String[] tabNames = new String[] {	
				"Description", "Text Defn"};
		super.postInit(tabNames, columnHeadings, false);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters()
				.add(ECL).withType(JobParameter.Type.ECL)
				.add(UNPROMOTED_CHANGES_ONLY).withType(JobParameter.Type.BOOLEAN).withDefaultValue(false)
				.build();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.RELEASE_VALIDATION))
				.withName("New Descriptions")
				.withDescription("This report lists all discriptions (optionally filtered by ECL) created in the current authoring cycle." +
				"Ticking the 'Unpromoted Changes' box will cause only those new descriptions that have been created since the last time the project was promoted, to be listed.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withTag(MS)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		//Are we running locally from command line?
		if (jobRun.getTerminologyServerUrl() == null) {
			logger.warn("No TS specified.  Using localhost");
			jobRun.setTerminologyServerUrl("http://localhost:8085/");
		}
		
		List<Concept> conceptsOfInterest;
		if (subsetECL != null && !subsetECL.isEmpty()) {
			conceptsOfInterest = new ArrayList<>(findConcepts(subsetECL));
		} else {
			conceptsOfInterest = new ArrayList<>(gl.getAllConcepts());
		}
		
		conceptsOfInterest.sort(Comparator.comparing(Concept::getSemTag).thenComparing(Concept::getFsn));
		
		List<Description> unpromotedDescriptions = null;
		if (jobRun.getParameters().getMandatoryBoolean(UNPROMOTED_CHANGES_ONLY)) {
			unpromotedDescriptions = tsClient.getUnpromotedDescriptions(project.getBranchPath(), true);
			info("Recovered " + unpromotedDescriptions.size() + " unpromoted descriptions");
		}
		
		for (Concept c : conceptsOfInterest) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getId().equals("4588845012")) {
					debug("here");
				}
				if (!d.isReleased() && inScope(d) && 
						(unpromotedDescriptions == null || unpromotedDescriptions.contains(d))) {
					int tabIdx = d.getType().equals(DescriptionType.TEXT_DEFINITION) ? SECONDARY_REPORT : PRIMARY_REPORT;
					report(tabIdx, c, d.getLang(), d.getTerm(), d);
					countIssue(c);
				}
			}
		}
	}

}