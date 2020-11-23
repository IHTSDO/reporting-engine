package org.ihtsdo.termserver.scripting.service;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.traceability.domain.*;
import org.springframework.util.StringUtils;

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
		populateReportRowsWithTraceabilityInfo(ts.getProject().getKey());
		report();
		batchedReportRowMap.clear();
	}

	private void populateReportRowsWithTraceabilityInfo(String branchFilter) {
		branchFilter = "/" + branchFilter;
		List<Long> conceptIds = new ArrayList<>(batchedReportRowMap.keySet());
		List<Activity> traceabilityInfo = client.getConceptActivity(conceptIds, areaOfInterest, ActivityType.CONTENT_CHANGE);
		if (traceabilityInfo.size() == 0) {
			logger.warn("Failed to recover any traceability information for {} concepts", conceptIds.size());
		}
		for (Activity activity : traceabilityInfo) {
			Object[] info = new Object[3];
			info[0] = activity.getUser().getUsername();
			info[1] = activity.getBranch().getBranchPath();
			info[2] = activity.getCommitDate().toInstant().atZone(ZoneId.systemDefault());
			
			if (StringUtils.isEmpty(info[1])) {
				continue;  //Skip blanks
			}
			for (ReportRow row : getRelevantReportRows(activity, conceptIds)) {
				//Preference for any branch that matches our filter, or the more recent update if both do
				if (row.traceabilityInfo != null && !StringUtils.isEmpty(row.traceabilityInfo[1])) {
					if (row.traceabilityInfo[1].toString().contains(branchFilter) &&
							!info[1].toString().contains(branchFilter)) {
						//Keep the one we've got
					} else if (!row.traceabilityInfo[1].toString().contains(branchFilter) &&
							info[1].toString().contains(branchFilter)) {
						row.traceabilityInfo = info;
					} else if (row.traceabilityInfo[1].toString().contains(branchFilter) &&
						info[1].toString().contains(branchFilter)) {
						//Both are a match, take whatever is newer!
						if (((ZonedDateTime)row.traceabilityInfo[2]).isAfter((ZonedDateTime)info[2])) {
							//Keep the one we've got
						} else {
							row.traceabilityInfo = info;
						}
					} else {
						//Nothing matches, we'll take what we can get
						row.traceabilityInfo = info;
					}
				} else {
					row.traceabilityInfo = info;
				}
			}
		}
	}
	
	private List<ReportRow> getRelevantReportRows(Activity activity, List<Long> conceptIds) {
		List<ReportRow> reportRows = new ArrayList<>();
		for (Long id : getConceptIds(activity, conceptIds)) {
			reportRows.addAll(batchedReportRowMap.get(id));
		}
		return reportRows;
	}
	
	private Set<Long> getConceptIds(Activity activity, List<Long> conceptsOfInterest) {
		Set<Long> conceptIds = new HashSet<>();
		for (ConceptChange change : activity.getConceptChanges()) {
			//If the data coming back wasn't about a concept we're interested in, ignore it
			if (change.getConceptId() != null && conceptsOfInterest.contains(change.getConceptId())) {
				conceptIds.add(change.getConceptId()); 
			}
		}
		return conceptIds;
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
