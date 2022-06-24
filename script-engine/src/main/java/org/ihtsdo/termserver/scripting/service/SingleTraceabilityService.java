package org.ihtsdo.termserver.scripting.service;

import org.apache.commons.lang3.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.client.JiraHelper;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.JobRun;
import org.snomed.otf.traceability.domain.Activity;
import org.snomed.otf.traceability.domain.ActivityType;

import net.rcarz.jiraclient.Issue;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class SingleTraceabilityService implements TraceabilityService {
	
	static Logger logger = LoggerFactory.getLogger(SingleTraceabilityService.class);
	private static int WORKER_COUNT = 4;
	
	private TraceabilityServiceClient client;
	private JiraHelper jiraHelper;
	private TermServerScript ts;
	private static int MAX_PENDING_SIZE  = 100;
	private static int MIN_PENDING_SIZE  = 50;
	
	private Map<String, Issue> jiraIssueMap = new HashMap<>();
	
	private static int IDX_USERNAME = 0;
	private static int IDX_BRANCH = 1;
	private static int IDX_COMMIT_DATE = 2;
	
	private int requestCount = 0;
	
	private Worker[] workers;
	
	public SingleTraceabilityService(JobRun jobRun, TermServerScript ts) {
		//this.client = new TraceabilityServiceClient("http://localhost:8085/", jobRun.getAuthToken());
		this.client = new TraceabilityServiceClient(jobRun.getTerminologyServerUrl(), jobRun.getAuthToken());
		this.jiraHelper = new JiraHelper();
		this.ts = ts;
	}
	
	public void tidyUp() {
		for (Worker worker : workers) {
			worker.shutdown();
		}
	}
	
	public void populateTraceabilityAndReport(String fromDate, String toDate, int reportTabIdx, Concept c, Object... details) throws TermServerScriptException {
		ReportRow row = new ReportRow(fromDate, toDate, reportTabIdx, c, details);
		//Do we have a work thread running?
		if (workers == null) {
			workers = new Worker[WORKER_COUNT];
			for (int i=0;i<WORKER_COUNT; i++) {
				workers[i] = new Worker(i);
				Thread t = new Thread(workers[i]);
				t.start();
			}
		}
		
		//Pop this row in our queue and we'll get to it when we get to it.
		//Pick a new worker to add to each request
		int failedWorkerCount = 0;
		boolean successfulAdd = false;
		while (successfulAdd == false) {
			successfulAdd = workers[requestCount%WORKER_COUNT].addToQueue(row);
			requestCount++;
			if (++failedWorkerCount > WORKER_COUNT) {
				throw new TermServerScriptException("All Workers Failed");
			}
		}
	}
	
	private void populateReportRowWithTraceabilityInfo(ReportRow row) {
		List<Activity> traceabilityInfo = robustlyRecoverTraceabilityInfo(row);
		if (traceabilityInfo.size() == 0) {
			logger.warn("Failed to recover any traceability information for concept {}", row.c.getConceptId());
		}
		
		for (Activity activity : traceabilityInfo) {
			Object[] info = new Object[3];
			info[IDX_USERNAME] = activity.getUsername();
			info[IDX_BRANCH] = activity.getBranch();
			if (activity.getCommitDate() == null) {
				info[IDX_COMMIT_DATE] = null;
			} else {
				info[IDX_COMMIT_DATE] = activity.getCommitDate().toInstant().atZone(ZoneId.systemDefault());
			}
			
			if (StringUtils.isEmpty((String)info[1])) {
				continue;  //Skip blanks
			}
			
			if (row.traceabilityInfo != null && !StringUtils.isEmpty((String) row.traceabilityInfo[IDX_BRANCH])) {
				//Keep the latest commit date in the set
				if (((ZonedDateTime)info[IDX_COMMIT_DATE]).isAfter((ZonedDateTime)row.traceabilityInfo[IDX_COMMIT_DATE])) {
					row.traceabilityInfo = info;
				}
			} else {
				row.traceabilityInfo = info;
			}
			
			if (row.traceabilityInfo[IDX_USERNAME] == null || row.traceabilityInfo[IDX_USERNAME].equals("System")) {
				recoverTaskAuthor(row.traceabilityInfo);
			}
		}
	}

	private void recoverTaskAuthor(Object[] info) {
		if (info[IDX_BRANCH] != null) {
			String branch = info[IDX_BRANCH].toString();
			//Have we seen this branch before?
			Issue jiraIssue = jiraIssueMap.get(info[IDX_BRANCH]);
			try {
				if (jiraIssue == null) {
					String taskKey = branch.substring(branch.lastIndexOf("/")+1);
					//Do we infact have a project here?
					if (!taskKey.contains("-")) {
						logger.warn("Cannot retrieve author details from project: " + branch);
						return;
					}
					jiraIssue = jiraHelper.getJiraTicket(taskKey);
				}
				
				if (jiraIssue != null) {
					info[IDX_USERNAME] = jiraIssue.getAssignee().getId();
					jiraIssueMap.put(branch, jiraIssue);
				}
			} catch (Exception e) {
				logger.error("Unable to recover task information related to " + info[IDX_BRANCH],e);
				//Store this failure so that we don't try to recover it again.
				jiraIssueMap.put(branch, null);
			}
		}
	}

	private Activity createDummyActivity(String conceptId, Exception e) {
		Activity activity = new Activity();
		activity.setCommitDate(null);
		activity.setBranch("N/A");
		activity.setUsername("Error recovering traceinformation : " + e.getMessage());
		return activity;
	}
	
	private List<Activity> robustlyRecoverTraceabilityInfo(ReportRow row) {
		String sctId = row.c.getConceptId();
		try {
			//IntOnly and SummaryOnly
			return client.getConceptActivity(sctId, ActivityType.CONTENT_CHANGE, row.fromDate, row.toDate, true, true);
		} catch (Exception e) {
			e.printStackTrace();
			return Collections.singletonList(createDummyActivity(sctId, e));
		}
	}
	
	class ReportRow {
		int reportTabIdx;
		Concept c;
		Object[] details;
		Object[] traceabilityInfo;
		String fromDate;
		String toDate;
		
		ReportRow(String fromDate, String toDate, int reportTabIdx, Concept c, Object[] details) {
			this.reportTabIdx = reportTabIdx;
			this.c = c;
			this.details = details;
			this.fromDate = fromDate;
			this.toDate = toDate;
		}
	}
	
	
	private class Worker implements Runnable {
		private Logger logger = LoggerFactory.getLogger(SingleTraceabilityService.class);
		private Queue<ReportRow> queue = new LinkedBlockingQueue<>();
		boolean shutdownPending = false;
		boolean isRunning = false;
		int workerId;

		public Worker(int id) {
			this.workerId = id;
		}

		@Override
		public void run() {
			isRunning = true;
			logger.debug("Worker {} is running", workerId);
			try {
				while (true) {
					if (shutdownPending) {
						logger.debug("Worker {} shutting down", workerId);
						System.gc();
						isRunning = false;
						break;
					} else if (queue.isEmpty()) {
						logger.debug("Processing queue is empty, worker {} sleeping for 5 seconds", workerId);
						try {
							Thread.sleep(1000 * 5);
						} catch (InterruptedException e) {
							e.printStackTrace();
						}
					} else {
						logger.debug("Worker {}'s queue contains " + queue.size() + " rows to process", workerId);
					}
					while(queue.size() > 0) {
						ReportRow row = queue.remove();
						try {
							process(row);
						} catch (TermServerScriptException e) {
							logger.error("Worker {} Failed to process row {} ", workerId,  row, e);
						}
					}
				}
			} finally {
				isRunning = false;
			}
		}
		
		public boolean addToQueue(ReportRow row) throws TermServerScriptException {
			//If we're not running, don't accept any row
			if (!isRunning) {
				return false;
			}
			
			//If our queue is excessively large, we'll hold the caller thread here until we're
			//back down below our limit
			queue.add(row);
			if (queue.size() > SingleTraceabilityService.MAX_PENDING_SIZE) {
				logger.debug("Worker queue now " + queue.size() + " holding caller until reduced ...");
				while (queue.size() > SingleTraceabilityService.MIN_PENDING_SIZE) {
					try {
						Thread.sleep(1000 * 5);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
					
					if (!isRunning) {
						throw new TermServerScriptException("Worker queue size is " + queue.size() +", but worker is not running");
					}
				}
				logger.debug("Worker queue now " + queue.size() + " resuming processing ...");
			}
			return true;
		}

		public void shutdown() {
			shutdownPending = true;
		}
		
		public boolean isRunning() {
			return isRunning;
		}
		
		public void process(ReportRow row) throws TermServerScriptException {
			SingleTraceabilityService.this.populateReportRowWithTraceabilityInfo(row);
			if (row.details == null) {
				ts.report(row.reportTabIdx, row.c, row.traceabilityInfo);
			} else {
				ts.report(row.reportTabIdx, row.c, row.details, row.traceabilityInfo);
			}
		}
	}

	@Override
	public void flush() throws TermServerScriptException {
		for (Worker worker : workers) {
			worker.shutdown();
			if (worker.isRunning()) {
				logger.debug("Waiting for worker to shut down");
				while (worker.isRunning()) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
				logger.debug("Worker confirmed shutdown");
			}
		}
	}

	@Override
	public void populateTraceabilityAndReport(int tabIdx, Concept c, Object... details)
			throws TermServerScriptException {
		throw new NotImplementedException("This class uses variant that takes date range");
	}
	
}
