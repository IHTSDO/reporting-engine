package org.ihtsdo.termserver.scripting.service;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.traceability.TraceabilityServiceClient;
import org.ihtsdo.otf.utils.ExceptionUtils;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.LinkedBlockingQueue;

public class SingleTraceabilityService extends CommonTraceabilityService {

	protected static final String EXCEPTION_ENCOUNTERED = "Exception encountered";

	private Set<String> unacceptableUsernames = new HashSet<>();
	{
		unacceptableUsernames.add("System");
		unacceptableUsernames.add("mapping-prod");
		unacceptableUsernames.add("mchu");
		unacceptableUsernames.add("kkewley");
		unacceptableUsernames.add("eilyukhina");
		unacceptableUsernames.add("dmcgaw");
		unacceptableUsernames.add("pwilliams");
		unacceptableUsernames.add("tcooksey");
	}

	private static DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
	private static final Logger LOGGER = LoggerFactory.getLogger(SingleTraceabilityService.class);
	private static int WORKER_COUNT = 4;
	
	private JiraHelper jiraHelper;
	private static final int MAX_PENDING_SIZE  = 100;
	private static final int MIN_PENDING_SIZE  = 50;
	
	private Map<String, Issue> jiraIssueMap = new HashMap<>();
	
	private static final int IDX_USERNAME = 0;
	private static final int IDX_BRANCH = 1;
	private static final int IDX_COMMIT_DATE = 2;
	
	private int requestCount = 0;
	String branchPrefix = null;
	
	private Worker[] workers;

	private Map<String, Map<String, Object[]>> cachePerTimeSlot = new HashMap<>();
	
	public SingleTraceabilityService(JobRun jobRun, TermServerScript ts) {
		this.client = new TraceabilityServiceClient(jobRun.getTerminologyServerUrl(), jobRun.getAuthToken());
		this.jiraHelper = new JiraHelper();
		this.ts = ts;
	}

	@Override
	public void tidyUp() {
		if (workers == null) {
			LOGGER.info("No traceability workers have been created, skipping tidy up.");
		} else {
			for (Worker worker : workers) {
				worker.shutdown();
			}
		}
	}
	
	public void populateTraceabilityAndReport(String fromDate, String toDate, int reportTabIdx, Concept c, Object... details) throws TermServerScriptException {
		ReportRow row = new ReportRow(fromDate, toDate, reportTabIdx, c, details);
		//Do we have a work thread running?
		if (workers == null) {
			workers = new Worker[WORKER_COUNT];
			for (int i=0;i<WORKER_COUNT; i++) {
				workers[i] = new Worker(i, branchPrefix);
				Thread t = new Thread(workers[i]);
				t.start();
			}
		}
		
		//Pop this row in our queue and we'll get to it when we get to it.
		//Pick a new worker to add to each request
		int failedWorkerCount = 0;
		boolean successfulAdd = false;
		while (successfulAdd == false) {
			int thisWorker = requestCount%WORKER_COUNT;
			successfulAdd = workers[thisWorker].addToQueue(row);
			if (++failedWorkerCount > WORKER_COUNT) {
				throw new TermServerScriptException("All Workers Failed.  Last failure: " + workers[thisWorker].getFailureReaason());
			}
			requestCount++;
		}
	}
	
	private void populateReportRowWithTraceabilityInfo(ReportRow row, boolean intOnly, String onBranch2) throws TermServerScriptException {
		if (row == null) {
			throw new TermServerScriptException("Request to populate row with traceability information, but row was not supplied");
		}
		//Do we have a traceability cache for this particular time slot?
		String timeSlotKey = row.toDate == null? "NULL" : row.toDate;
		Map<String, Object[]> traceabilityCache = cachePerTimeSlot.get(timeSlotKey);
		if (traceabilityCache == null) {
			traceabilityCache = new HashMap<>();
			cachePerTimeSlot.put(timeSlotKey, traceabilityCache);
		}
		
		//Do we already have cached information for this row?
		if (traceabilityCache.containsKey(row.c.getId())) {
			row.traceabilityInfo = traceabilityCache.get(row.c.getId());
		} else {
			List<Activity> traceabilityInfo = robustlyRecoverTraceabilityInfo(row, intOnly, branchPrefix);
			if (traceabilityInfo.size() == 0) {
				LOGGER.warn("Failed to recover any traceability information for concept {}", row.c.getConceptId());
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
			}
			
			if (row.traceabilityInfo == null) {
				LOGGER.warn("Failed to find any traceability information for {} can't even look up task", row.c);
			} else {
				if (row.traceabilityInfo[IDX_USERNAME] == null
						|| StringUtils.isEmpty(row.traceabilityInfo[IDX_USERNAME])
						|| StringUtils.isEmpty(row.traceabilityInfo[IDX_USERNAME].toString().trim())
						|| unacceptableUsernames.contains(row.traceabilityInfo[IDX_USERNAME].toString())) {
					recoverTaskAuthor(row.traceabilityInfo);
				}
				traceabilityCache.put(row.c.getId(), row.traceabilityInfo);
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
						LOGGER.warn("Cannot retrieve author details from project: " + branch);
						return;
					}
					jiraIssue = jiraHelper.getJiraTicket(taskKey);
				}
				
				if (jiraIssue != null) {
					info[IDX_USERNAME] = jiraIssue.getAssignee().getId();

					//It might be that we have a 'name' and id is null
					if (StringUtils.isEmpty(info[IDX_USERNAME])) {
						info[IDX_USERNAME] = jiraIssue.getAssignee().getName();
					}
					//If the username is still unacceptable, try the reporter
					if (unacceptableUsernames.contains(info[IDX_USERNAME].toString())) {
						info[IDX_USERNAME] = "*" + jiraIssue.getReporter().getName();
					}
					jiraIssueMap.put(branch, jiraIssue);
				}
			} catch (Exception e) {
				LOGGER.error("Unable to recover task information related to " + info[IDX_BRANCH],e);
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
	
	private List<Activity> robustlyRecoverTraceabilityInfo(ReportRow row, boolean intOnly, String branchPrefix) {
		String sctId = row.c.getConceptId();
		try {
			boolean summaryOnly = true;
			return client.getConceptActivity(sctId, ActivityType.CONTENT_CHANGE, row.fromDate, row.toDate, summaryOnly, intOnly, branchPrefix);
		} catch (Exception e) {
			LOGGER.error(EXCEPTION_ENCOUNTERED,e);
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
		private final Logger LOGGER_WORKER = LoggerFactory.getLogger(Worker.class);
		private Queue<ReportRow> queue = new LinkedBlockingQueue<>();
		boolean shutdownPending = false;
		boolean isRunning = false;
		private String failureReaason;
		int workerId;
		boolean intOnly = true;
		String branchPrefix = null;

		public Worker(int id, String branchPrefix) {
			this.workerId = id;
			if (branchPrefix != null) {
				intOnly = false;
				this.branchPrefix = branchPrefix;
			}
		}

		@Override
		public void run() {
			isRunning = true;
			LOGGER_WORKER.debug("Worker {} is running", workerId);
			try {
				while (true) {
					if (shutdownPending) {
						LOGGER_WORKER.debug("Worker {} shutting down", workerId);
						System.gc();
						isRunning = false;
						break;
					} else if (queue.isEmpty()) {
						LOGGER_WORKER.debug("Processing queue is empty, worker {} sleeping for 5 seconds", workerId);
						try {
							Thread.sleep(1000 * 5);
						} catch (InterruptedException e) {
							LOGGER.error(EXCEPTION_ENCOUNTERED,e);
						}
					} else {
						LOGGER_WORKER.debug("Worker {}'s queue contains " + queue.size() + " rows to process", workerId);
					}
					while(queue.size() > 0) {
						ReportRow row = queue.remove();
						try {
							process(row);
						} catch (TermServerScriptException e) {
							LOGGER_WORKER.error("Worker {} Failed to process row {} ", workerId, row, e);
						}
					}
				}
			} catch (Exception e) {
				String msg = "Unexpected worker " + workerId + " termination: " + ExceptionUtils.getExceptionCause("", e);
				String stack = ExceptionUtils.getStackTrace(e);
				setFailureReaason(msg + "\n" + stack);
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
				LOGGER_WORKER.debug("Worker {} queue now {} holding caller until reduced ...", this.workerId, queue.size());
				while (queue.size() > SingleTraceabilityService.MIN_PENDING_SIZE) {
					try {
						Thread.sleep(1000 * 5);
					} catch (InterruptedException e) {
						LOGGER.error(EXCEPTION_ENCOUNTERED,e);
					}
					
					if (!isRunning) {
						throw new TermServerScriptException("Worker queue size is " + queue.size() +", but worker is not running");
					}
				}
				LOGGER_WORKER.debug("Worker {} queue now {} resuming processing ...", this.workerId, queue.size());
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
			SingleTraceabilityService.this.populateReportRowWithTraceabilityInfo(row, intOnly, branchPrefix);
			
			//Snip the processing date a bit if it has been populated
			if (row.traceabilityInfo != null && row.traceabilityInfo[IDX_COMMIT_DATE] != null) {
				if (!(row.traceabilityInfo[IDX_COMMIT_DATE] instanceof String)) {
					try {
						row.traceabilityInfo[IDX_COMMIT_DATE] = ((ZonedDateTime)row.traceabilityInfo[IDX_COMMIT_DATE]).format(dateFormatter);
					} catch (Exception e) {
						LOGGER_WORKER.error("Formatting error on '" + row.traceabilityInfo[IDX_COMMIT_DATE] + "' " + ExceptionUtils.getExceptionCause("", e), workerId);
						LOGGER_WORKER.error(ExceptionUtils.getStackTrace(e));
					}
				}
			}
			
			if (row.details == null) {
				ts.report(row.reportTabIdx, row.c, row.c.getEffectiveTime(), row.traceabilityInfo);
			} else {
				ts.report(row.reportTabIdx, row.c, row.details, row.traceabilityInfo);
			}
		}

		public String getFailureReaason() {
			return failureReaason;
		}

		public void setFailureReaason(String failureReaason) {
			LOGGER_WORKER.error(failureReaason);
			this.failureReaason = failureReaason;
		}
	}

	@Override
	public void flush() throws TermServerScriptException {
		if (workers == null) {
			LOGGER.info("No traceability workers have been created, skipping shut down.");
		} else {
			for (Worker worker : workers) {
				worker.shutdown();
				if (worker.isRunning()) {
					LOGGER.debug("Waiting for worker to shut down");
					while (worker.isRunning()) {
						try {
							Thread.sleep(1000);
						} catch (InterruptedException e) {
							LOGGER.error(EXCEPTION_ENCOUNTERED,e);
						}
					}
					LOGGER.info("Worker confirmed shutdown");
				}
			}
		}
	}

	@Override
	public void setBranchPath(String onBranch) {
		this.branchPrefix = onBranch;
	}

	@Override
	public int populateTraceabilityAndReport(int tabIdx, Component c, Object... details)
			throws TermServerScriptException {
		return NOT_SET;
	}
	
}
