package org.ihtsdo.termserver.scripting.service;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.traceability.domain.Activity;
import org.snomed.otf.traceability.domain.ActivityType;
import org.snomed.otf.traceability.domain.ConceptChange;

public class TraceabilityService {

	TraceabilityServiceClient client;
	TermServerScript ts;
	private static int BATCH_SIZE = 100;
	Map<Long, ReportRow> batchedReportRowMap = new LinkedHashMap<>();
	String areaOfInterest;
	
	public TraceabilityService(JobRun jobRun, TermServerScript ts, String areaOfInterest) {
		this.client = new TraceabilityServiceClient(jobRun.getTerminologyServerUrl(), jobRun.getAuthToken());
		this.ts = ts;
		this.areaOfInterest = areaOfInterest;
	}
	
	public void populateTraceabilityAndReport(int reportTabIdx, Concept c, Object... details) throws TermServerScriptException {
		//We'll cache this row until we have enough to be worth making a call to traceability
		Long conceptId = Long.parseLong(c.getConceptId());
		batchedReportRowMap.put(conceptId, new ReportRow(reportTabIdx, c, details));
		if (batchedReportRowMap.size() >= BATCH_SIZE) {
			flush();
		}
	}
	
	public void flush() throws TermServerScriptException {
		populateReportRowsWithTraceabilityInfo();
		report();
		batchedReportRowMap.clear();
	}

	private void populateReportRowsWithTraceabilityInfo() {
		List<Long> conceptIds = batchedReportRowMap.values().stream()
				.map(r -> Long.parseLong(r.c.getConceptId()))
				.collect(Collectors.toList());
		List<Activity> traceabilityInfo = client.getConceptActivity(conceptIds, areaOfInterest, ActivityType.CONTENT_CHANGE);
		for (Activity activity : traceabilityInfo) {
			String[] info = new String[3];
			info[0] = activity.getUser().getUsername();
			info[1] = activity.getBranch().getBranchPath();
			info[2] = activity.getCommitDate().toInstant().atZone(ZoneId.systemDefault()).toString();
			//TODO Check for multiple rows for single concept
			batchedReportRowMap.get(getConceptId(activity)).traceabilityInfo = info;
		}
	}
	
	private Long getConceptId(Activity activity) {
		ConceptChange change = activity.getConceptChanges().iterator().next();
		return change.getConceptId();
	}

	private void report() throws TermServerScriptException {
		for (ReportRow row : batchedReportRowMap.values()) {
			if (row.details == null) {
				ts.report(row.reportTabIdx, row.c, row.traceabilityInfo);
			} else {
				ts.report(row.reportTabIdx, row.c, row.details, row.traceabilityInfo);
			}
		}
	}

	class ReportRow {
		int reportTabIdx;
		Concept c;
		Object[] details;
		Object[] traceabilityInfo;
		
		ReportRow(int reportTabIdx, Concept c, Object[] details) {
			this.reportTabIdx = reportTabIdx;
			this.c = c;
			this.details = details;
		}
	}
}
