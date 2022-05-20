package org.ihtsdo.termserver.scripting.service;

import org.apache.commons.lang3.NotImplementedException;
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
import org.springframework.util.StringUtils;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class BulkTraceabilityService implements TraceabilityService {
	
	static Logger logger = LoggerFactory.getLogger(BulkTraceabilityService.class);

	TraceabilityServiceClient client;
	TermServerScript ts;
	private static int MAX_PENDING_SIZE  = 1000;
	Map<String, List<ReportRow>> batchedReportRowMap = new LinkedHashMap<>();
	Map<String, Object[]> traceabilityInfoCache = new HashMap<>();
	
	public BulkTraceabilityService(JobRun jobRun, TermServerScript ts) {
		//this.client = new TraceabilityServiceClient("http://localhost:8085/", jobRun.getAuthToken());
		this.client = new TraceabilityServiceClient(jobRun.getTerminologyServerUrl(), jobRun.getAuthToken());
		this.ts = ts;
	}
	
	public void tidyUp() {
		traceabilityInfoCache.clear();
	}
	
	public void populateTraceabilityAndReport(int reportTabIdx, Concept c, Object... details) throws TermServerScriptException {
		//We'll cache this row until we have enough to be worth making a call to traceability
		//Do we already have a row for this concept?
		List<ReportRow> rows = batchedReportRowMap.get(c.getConceptId());
		if (rows == null) {
			rows = new ArrayList<>();
			batchedReportRowMap.put(c.getConceptId(), rows);
		}
		rows.add(new ReportRow(reportTabIdx, c, details));
		if (getRequiredRowCount() >= TraceabilityServiceClient.BATCH_SIZE || 
				batchedReportRowMap.size() >= MAX_PENDING_SIZE) {
			flush();
		}
	}
	
	private int getRequiredRowCount() {
		//Of the batch rows, only count the number we don't have cached information for
		int infoRequiredCount = 0;
		for (String conceptId : batchedReportRowMap.keySet()) {
			if (!traceabilityInfoCache.containsKey(conceptId)) {
				infoRequiredCount++;
			}
		}
		return infoRequiredCount;
	}


	public void flush() throws TermServerScriptException {
		try {
			populateReportRowsWithTraceabilityInfo(ts.getProject().getKey());
		} catch (Exception e) {
			throw new TermServerScriptException(e);
		}
		report();
		batchedReportRowMap.clear();
		System.gc();
	}

	private void populateReportRowsWithTraceabilityInfo(String branchFilter) throws InterruptedException {
		List<String> conceptIds = new ArrayList<>(batchedReportRowMap.keySet());
		if (conceptIds.size() == 0) {
			return;
		}
		//Firstly, what rows can we satisfy from the cache?
		List<String> populatedFromCache = new ArrayList<>();
		for (Map.Entry<String, List<ReportRow>> entry: batchedReportRowMap.entrySet()) {
			String conceptId = entry.getKey();
			//Do we have data for this concept Id?
			if (traceabilityInfoCache.containsKey(conceptId)) {
				populatedFromCache.add(conceptId);
				for (ReportRow row : entry.getValue()) {
					row.traceabilityInfo = traceabilityInfoCache.get(conceptId);
				}
			}
		}
		conceptIds.removeAll(populatedFromCache);
		logger.info("Recovered cached information for " + populatedFromCache.size() + " concepts (cache size: " + traceabilityInfoCache.size() + ")");
		
		//Anything left, we'll make a call to traceability to return
		if (conceptIds.size() > 0) {
			branchFilter = "/" + branchFilter;
			List<Activity> traceabilityInfo = robustlyRecoverTraceabilityInfo(conceptIds);
			if (traceabilityInfo.size() == 0) {
				logger.warn("Failed to recover any traceability information for {} concepts", conceptIds.size());
			}
			for (Activity activity : traceabilityInfo) {
				Object[] info = new Object[3];
				info[0] = activity.getUsername();
				info[1] = activity.getBranch();
				if (activity.getCommitDate() == null) {
					info[2] = null;
				} else {
					info[2] = activity.getCommitDate().toInstant().atZone(ZoneId.systemDefault());
				}
				
				if (StringUtils.isEmpty(info[1])) {
					continue;  //Skip blanks
				}
				for (ReportRow row : getRelevantReportRows(activity, conceptIds)) {
					//Preference for any branch that matches our filter, or the more recent update if both do
					if (row.traceabilityInfo != null && !StringUtils.isEmpty(row.traceabilityInfo[1])) {
						row.traceabilityInfo = chooseBestInfo(branchFilter, row.traceabilityInfo, info);
					} else {
						row.traceabilityInfo = info;
					}
				}
				
				//Cache this info if we've not seen this
				for (String id : conceptIds) {
					if (traceabilityInfoCache.containsKey(id)) {
						Object[] bestInfo = chooseBestInfo(branchFilter, traceabilityInfoCache.get(id), info);
						traceabilityInfoCache.put(id, bestInfo);
					} else {
						traceabilityInfoCache.put(id, info);
					}
				}
			}
		}
	}
	
	private List<Activity> robustlyRecoverTraceabilityInfo(List<String> conceptIds) {
		try {
			return client.getConceptActivity(conceptIds, ActivityType.CONTENT_CHANGE, null);
		} catch (Exception e) {
			e.printStackTrace();
			return conceptIds.stream()
					.map(c -> createDummyActivity(c, e))
					.collect(Collectors.toList());
		}
	}
	
	private Activity createDummyActivity(String conceptId, Exception e) {
		Activity activity = new Activity();
		activity.setCommitDate(null);
		activity.setBranch("N/A");
		activity.setUsername("Error recovering traceinformation : " + e.getMessage());
		return activity;
	}

	private Object[] chooseBestInfo(String branchFilter, Object[] origInfo, Object[] newInfo) {
		if (origInfo[1].toString().contains(branchFilter) &&
				!newInfo[1].toString().contains(branchFilter)) {
			return origInfo;
		} else if (!origInfo[1].toString().contains(branchFilter) &&
				newInfo[1].toString().contains(branchFilter)) {
			return newInfo;
		} else if (origInfo[1].toString().contains(branchFilter) &&
				newInfo[1].toString().contains(branchFilter)) {
			//Both are a match, take whatever is newer!
			if (((ZonedDateTime)origInfo[2]).isAfter((ZonedDateTime)newInfo[2])) {
				return origInfo;
			} else {
				return newInfo;
			}
		} else {
			//Nothing matches, we'll take what we can get
			return newInfo;
		}
	}

	private List<ReportRow> getRelevantReportRows(Activity activity, List<String> conceptIds) {
		List<ReportRow> reportRows = new ArrayList<>();
		for (String id : getConceptIds(activity, conceptIds)) {
			reportRows.addAll(batchedReportRowMap.get(id));
		}
		return reportRows;
	}
	
	private Set<String> getConceptIds(Activity activity, List<String> conceptsOfInterest) {
		Set<String> conceptIds = new HashSet<>();
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

	@Override
	public void populateTraceabilityAndReport(String fromDate, String toDate, int tab, Concept c, Object... details) {
		throw new NotImplementedException("This class uses bulk method, not single concept lookup");
	}
}
