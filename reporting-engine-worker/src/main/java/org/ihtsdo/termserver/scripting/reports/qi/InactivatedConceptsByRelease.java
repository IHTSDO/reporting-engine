package org.ihtsdo.termserver.scripting.reports.qi;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ReportClass;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.reports.TermServerReport;
import org.ihtsdo.termserver.scripting.service.TraceabilityService;
import org.ihtsdo.termserver.scripting.service.SingleTraceabilityService;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * QI-1019 List inactivated concepts from 20180131 to current
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InactivatedConceptsByRelease extends TermServerReport implements ReportClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(InactivatedConceptsByRelease.class);

	List<String> releaseETs;
	private static int startYear = 2018;
	private static String startET = "20180131";
	TraceabilityService traceabilityService;
	
	SimpleDateFormat dateFormat =  new SimpleDateFormat("yyyyMMdd");
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(InactivatedConceptsByRelease.class, args, params);
	}
	
	public void init (JobRun run) throws TermServerScriptException {
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
		ReportSheetManager.setTargetFolderId("11i7XQyb46P2xXNBwlCOd3ssMNhLOx1m1"); //QI / Misc Analysis
		super.init(run);
	}
	
	public void postInit() throws TermServerScriptException {
		populateReleaseEffectiveTimes();
		int releases = releaseETs.size();
		String[] columnHeadings = new String[releases];
		Arrays.fill(columnHeadings,"Id, FSN, SemTag, EffectiveTime, Indicators, Associations, Author, Branch, Date, Spare");
		String[] tabNames = releaseETs.toArray(new String[releases]);
		tabNames[0] = "Current";
		super.postInit(tabNames, columnHeadings);
		traceabilityService = new SingleTraceabilityService(jobRun, this);
	}
	
	private void populateReleaseEffectiveTimes() {
		releaseETs = new ArrayList<>();
		int year = Calendar.getInstance().get(Calendar.YEAR);
		int month = Calendar.getInstance().get(Calendar.MONTH);
		releaseETs.add("");
		for (int i=year; i >= startYear; i--) {
			//Are we in the 2nd half of the year?
			if (i != year || month > 6) {
				releaseETs.add(i + "0731");
			}
			releaseETs.add(i + "0131");
		}
	}

	@Override
	public Job getJob() {
		JobParameters params = new JobParameters();
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Inactivated Concepts By Release")
				.withDescription("This report lists concept inactivations broken down by release with author attribution.")
				.withProductionStatus(ProductionStatus.PROD_READY)
				.withParameters(params)
				.withTag(INT)
				.withExpectedDuration(60)
				.build();
	}
	
	public void runJob() throws TermServerScriptException {
		List<Concept> recentlyInactiveConcepts = gl.getAllConcepts().stream()
				.filter(c -> !c.isActive())
				.filter(c -> StringUtils.isEmpty(c.getEffectiveTime()) ||
						c.getEffectiveTime().compareTo(startET) > 0)
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
		//recentlyInactiveConcepts = Collections.singletonList(gl.getConcept("371283006"));
		try {
			for (Concept c : recentlyInactiveConcepts) {
				int tab = determineRelease(c);
				String toDate = tab > 0 ? releaseETs.get(tab-1) : null;
				String fromDate = releaseETs.get(tab);
				//Work 2 months earlier, as changes could have been sitting in a task
				try {
					if (StringUtils.isEmpty(fromDate) || fromDate.length() < 5) {
						LOGGER.warn("What's the story here?");
						//If we 're running to the current time, work back 
						fromDate = dateFormat.format(new Date());
					}
					Date fromDateDate = DateUtils.addDays(DateUtils.parseDate(fromDate, "yyyyMMdd"),-180);
					fromDate = dateFormat.format(fromDateDate);
				} catch (ParseException e) {
					throw new TermServerScriptException("Unable to parse date '" + fromDate + "'");
				}
				traceabilityService.populateTraceabilityAndReport(fromDate, toDate, tab, c,
						c.getEffectiveTime(),
						c.getInactivationIndicator(),
						SnomedUtils.prettyPrintHistoricalAssociations(c, gl));
			}
		} finally {
			traceabilityService.flush();
		}
	}

	private int determineRelease(Concept c) {
		String et = c.getEffectiveTime();
		if (StringUtils.isEmpty(et)) {
			return 0;
		}
		for (int i=1; i < releaseETs.size(); i++) {
			if (et.compareTo(releaseETs.get(i)) > 0) {
				return i;
			}
		}
		throw new IllegalArgumentException("Could not determine release of " + et);
	}

}
