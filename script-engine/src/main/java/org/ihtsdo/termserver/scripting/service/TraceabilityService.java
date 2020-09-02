package org.ihtsdo.termserver.scripting.service;

import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.traceability.domain.Activity;
import org.snomed.otf.traceability.domain.ActivityType;
import org.snomed.otf.traceability.domain.ConceptChange;

public class TraceabilityService {
	
	static Logger logger = LoggerFactory.getLogger(TraceabilityService.class);

	TraceabilityServiceClient client;
	TermServerScript ts;
	private static int BATCH_SIZE = 100;
	Map<Long, List<ReportRow>> batchedReportRowMap = new LinkedHashMap<>();
	String areaOfInterest;
	
	public TraceabilityService(JobRun jobRun, TermServerScript ts, String areaOfInterest) {
		this.client = new TraceabilityServiceClient(jobRun.getTerminologyServerUrl(), jobRun.getAuthToken());
		this.ts = ts;
		this.areaOfInterest = areaOfInterest;
	}
	
	public void populateTraceabilityAndReport(int reportTabIdx, Concept c, Object... details) throws TermServerScriptException {
		//We'll cache this row until we have enough to be worth making a call to traceability
		Long conceptId = Long.parseLong(c.getConceptId());
		
		//Do we already have a row for this concept?
		List<ReportRow> rows = batchedReportRowMap.get(conceptId);
		if (rows == null) {
			rows = new ArrayList<>();
			batchedReportRowMap.put(conceptId, rows);
		}
		rows.add(new ReportRow(reportTabIdx, c, details));
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
		List<Long> conceptIds = new ArrayList<>(batchedReportRowMap.keySet());
		List<Activity> traceabilityInfo = client.getConceptActivity(conceptIds, areaOfInterest, ActivityType.CONTENT_CHANGE);
		if (traceabilityInfo.size() == 0) {
			logger.warn("Failed to recover any traceability information for {} concepts", conceptIds.size());
		}
		for (Activity activity : traceabilityInfo) {
			String[] info = new String[3];
			info[0] = activity.getUser().getUsername();
			info[1] = activity.getBranch().getBranchPath();
			info[2] = activity.getCommitDate().toInstant().atZone(ZoneId.systemDefault()).toString();
			for (ReportRow row : batchedReportRowMap.get(getConceptId(activity, conceptIds))) {
				row.traceabilityInfo = info;
			}
		}
	}
	
	private Long getConceptId(Activity activity, List<Long> conceptsOfInterest) {
		Long conceptId = null;
		for (ConceptChange change : activity.getConceptChanges()) {
			//If the data coming back wasn't about a concept we're interested in, ignore it
			if (change.getConceptId() != null && conceptsOfInterest.contains(change.getConceptId())) {
				conceptId = change.getConceptId(); 
			}
		}
		return conceptId;
	}

	private void report() throws TermServerScriptException {
		for (List<ReportRow> conceptRows : batchedReportRowMap.values()) {
			for (ReportRow row : conceptRows) {
				if (row.details == null) {
					ts.report(row.reportTabIdx, row.c, row.traceabilityInfo);
				} else {
					ts.report(row.reportTabIdx, row.c, row.details, row.traceabilityInfo);
				}
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
