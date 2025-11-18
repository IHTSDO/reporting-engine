package org.ihtsdo.authoring.scheduler.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.ihtsdo.authoring.scheduler.api.configuration.WebSecurityConfig;
import org.ihtsdo.authoring.scheduler.api.rest.tools.AllReportRunner;
import org.ihtsdo.authoring.scheduler.api.rest.tools.BatchTools;
import org.ihtsdo.authoring.scheduler.api.service.AccessControlService;
import org.ihtsdo.authoring.scheduler.api.service.ScheduleService;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.utils.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.*;

import jakarta.servlet.http.HttpServletRequest;

@Tag(name = "Scheduler")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class SchedulerController {
	
	@Autowired
	private ScheduleService scheduleService;
	
	@Autowired
    AccessControlService accessControlService;
	
	@Autowired
    WebSecurityConfig config;

	@Autowired
	private AllReportRunner allReportRunner;

	@Autowired
	private BatchTools batchTools;

	@Value("${reporting.service.terminology.server.uri}")
	String terminologyServerUrl;

	private Map<String, List<JobCategory>> jobCache = new HashMap<>();
	private Date lastCacheUpdate = new Date(0);
	private static final long CACHE_TIMEOUT = 1000L * 60 * 30; // 30 minutes

	private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerController.class);

	private static final String X_AUTH_TOK = "X-AUTH-token";
	private static final String X_AUTH_USER = "X-AUTH-username";

	private static class AuthData {
		public final String authToken;
		public final String userName;

		public AuthData(String authToken, String userName) {
			this.authToken = authToken;
			this.userName = userName;
		}
	}

	private AuthData getAuthData(HttpServletRequest request) throws BusinessServiceException {
		String authToken = request.getHeader(X_AUTH_TOK);
		String userName = request.getHeader(X_AUTH_USER);

		if (StringUtils.isEmpty(authToken) || StringUtils.isEmpty(userName)) {
			//Are local override values available?
			authToken = config.getOverrideToken();
			userName = config.getOverrideUsername();

			if (StringUtils.isEmpty(authToken) || StringUtils.isEmpty(userName)) {
				throw new BusinessServiceException("Failed to recover authentication details from HTTP headers");
			} else {
				LOGGER.warn("Auth token not recovered from headers, using locally supplied override for user: {}", userName);
			}
		}

		return new AuthData(authToken, userName);
	}

	@Operation(summary="List Job Types")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs")
	public List<JobType> listJobTypes() {
		return scheduleService.listJobTypes();
	}

	@Operation(summary="List job type categories")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/{typeName}")
	public synchronized List<JobCategory> listJobTypeCategories(@PathVariable final String typeName) throws BusinessServiceException {
		//Do we need to refresh the cache?
		if (new Date().getTime() - lastCacheUpdate.getTime() > CACHE_TIMEOUT) {
			jobCache.clear();
			lastCacheUpdate = new Date();
		}

		//Do we have the data cached?
		if (jobCache.containsKey(typeName)) {
			return jobCache.get(typeName);
		}
		LOGGER.info("Populating cache of known jobs for type: {}.  Refresh scheduled for 30mins.", typeName);
		List<JobCategory> jobCategories = scheduleService.listJobTypeCategories(typeName).stream()
				.filter(jc -> !jc.getJobs().isEmpty())
				.map(this::reverseParameterOptions)
				.toList();
		jobCache.put(typeName, jobCategories);
		return jobCategories;
	}

	private JobCategory reverseParameterOptions(JobCategory jobCategory) {
		for (Job job : jobCategory.getJobs()) {
			for (JobParameter jobParameter : job.getParameters().getParameterMap().values()) {
				if (jobParameter.getOptions() != null) {
					List<String> reversedOptions = new ArrayList<>(jobParameter.getOptions());
					Collections.reverse(reversedOptions);
					jobParameter.setOptions(reversedOptions);
					if (jobParameter.getValues() != null) {
						List<String> reversedDefaultValues = new ArrayList<>(jobParameter.getValues());
						Collections.reverse(reversedDefaultValues);
						jobParameter.setValues(reversedDefaultValues);
					}
				}

			}
		}
		return jobCategory;
	}

	@Operation(summary="Get job details")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/{typeName}/{jobName}")
	public Job getJobDetails(@PathVariable final String typeName,
			@PathVariable final String jobName) {
		return scheduleService.getJob(jobName);
	}

	@Operation(summary="List jobs run")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/{typeName}/{jobName}/runs")
	public Page<JobRun> listJobsRun(HttpServletRequest request,
			@PathVariable final String typeName,
			@PathVariable final String jobName,
			@RequestParam(required=false, defaultValue="0") final Integer page,
			@RequestParam(required=false, defaultValue="20") final Integer size,
			@RequestParam(required=false) final String user) throws BusinessServiceException {
		
		Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
		return scheduleService.listJobsRun(typeName, jobName, user, getVisibleProjects(request), pageable);
	}
	
	@Operation(summary="List all jobs run")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/runs")
	public Page<JobRun> listAllJobsRun(HttpServletRequest request,
			@RequestParam(required=false) final Set<JobStatus> statusFilter,
			@RequestParam(required=false) final Integer sinceMins,
			@RequestParam(required=false, defaultValue="0") final Integer page,
			@RequestParam(required=false, defaultValue="50") final Integer size) {
		
		Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
		Page<JobRun> jobRunPage = scheduleService.listAllJobsRun(statusFilter, sinceMins, pageable);
		sanitise(jobRunPage);
		return jobRunPage;
	}

	@Operation(summary="List batches")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/listbatches")
	public Iterable<JobRunBatch> listLastBatch(@RequestParam(required=false, defaultValue = "0") final Long number) {
		return batchTools.getLastNBatches(number);
	}

	@Operation(summary="Get batch")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/batch/{id}")
	public List<JobRun> getBatch(@PathVariable final Long id) {
		return batchTools.getBatch(id);
	}

	@Operation(summary="Run all jobs")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value="/jobs/runall")
	public JobRunBatch runAll(
			HttpServletRequest request,
			@RequestParam(name= "dryRun", required=false, defaultValue="true") final Boolean dryRun,
			@RequestParam(name= "international", required=false, defaultValue="true") final Boolean international,
			@RequestParam(name= "managedService", required=false, defaultValue="true") final Boolean managedService,
			@RequestParam(name= "project", required=false, defaultValue="") final String project
	) throws BusinessServiceException {
		AuthData authData = getAuthData(request);
		return allReportRunner.runAllReports(dryRun, international, managedService, project, authData.userName, authData.authToken);
	}

	private Set<String> getVisibleProjects(HttpServletRequest request) throws BusinessServiceException {
		AuthData authData = getAuthData(request);
		return accessControlService.getProjects(authData.userName, terminologyServerUrl, authData.authToken);
	}

	@Operation(summary="Run job")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value="/jobs/{typeName}/{jobName}/runs")
	public JobRun runJob(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestBody JobRun jobRun) throws BusinessServiceException {
		return scheduleService.runJob(typeName, jobName, jobRun);
	}
	
	@Operation(summary="Bulk delete job-runs")
	@ApiResponse(responseCode = "204", description = "Deleted")
	@PostMapping(value="/jobs/{typeName}/{jobName}/runs/delete")
	public Page<JobRun> deleteJobRuns(
			HttpServletRequest request,
			@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestParam(required=false) final String user,
			@RequestBody List<UUID> uuids) throws BusinessServiceException {
		for (UUID uuid : uuids) {
			scheduleService.deleteJobRun(null, null, uuid);
		}
		return scheduleService.listJobsRun(typeName, jobName, user, getVisibleProjects(request), null);
	}
	
	@Operation(summary="Schedule job")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value="/jobs/{typeName}/{jobName}/schedule")
	public JobSchedule scheduleJob(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestBody JobSchedule jobSchedule) {
		return scheduleService.scheduleJob(typeName, jobName, jobSchedule);
	}
	
	@Operation(summary="Remove job schedule")
	@ApiResponse(responseCode = "200", description = "OK")
	@DeleteMapping(value="/jobs/{typeName}/{jobName}/schedule/{scheduleId}")
	public void deleteSchedule(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@PathVariable final UUID scheduleId) {
		scheduleService.deleteSchedule(typeName, jobName, scheduleId);
	}
	
	@Operation(summary="Get job run")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/{typeName}/{jobName}/runs/{runId}")
	public JobRun getJobStatus(@PathVariable final String typeName,
			@PathVariable final String jobName,
			@PathVariable final UUID runId) {
		return scheduleService.getJobRun(typeName, jobName, runId);
	}
	
	@Operation(summary="Delete job run")
	@DeleteMapping(value = "/jobs/{typeName}/{jobName}/runs/{runId}")
	public ResponseEntity<JobRun> deleteJobRun(@PathVariable final String typeName,
			@PathVariable final String jobName,
			@PathVariable final UUID runId) {
		boolean deleted = scheduleService.deleteJobRun(typeName, jobName, runId);
		if (!deleted) {
			return new ResponseEntity<>(HttpStatus.NOT_FOUND);
		}
		return new ResponseEntity<>(HttpStatus.NO_CONTENT);
	}
	
	@Operation(summary="Re-initialise")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/initialise")
	public void initialise(HttpServletRequest request) throws BusinessServiceException {
		scheduleService.initialise();
		AuthData authData = getAuthData(request);
		accessControlService.clearCache(authData.userName);
	}

	@Operation(summary="Clear all Caches")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/clear-all-caches")
	@PreAuthorize("hasPermission('ADMIN', 'global')")
	public void clearCaches() {
		jobCache.clear();
		accessControlService.clearAllCaches();
	}

	@Operation(summary="List whitelisted concepts for the given job & code system")
	@ApiResponse(responseCode = "200", description = "OK")
	@GetMapping(value="/jobs/{typeName}/{jobName}/{codeSystemShortname}/whitelist")
	public Set<WhiteListedConcept> getWhiteList(
			@PathVariable final String typeName,
			@PathVariable final String codeSystemShortname,
			@PathVariable final String jobName) {
		return scheduleService.getWhiteList(typeName, codeSystemShortname, jobName);
	}
	
	@Operation(summary="Set whitelisted concept for the given job & code system")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value="/jobs/{typeName}/{jobName}/{codeSystemShortname}/whitelist")
	public void setWhiteList(
			@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@PathVariable final String codeSystemShortname,
			@RequestBody Set<WhiteListedConcept> whiteList) {
		scheduleService.setWhiteList(typeName, jobName, codeSystemShortname, whiteList);
	}

	@Operation(summary="Clear any jobs that have been stuck for more than 10 hours.")
	@ApiResponse(responseCode = "200", description = "OK")
	@PostMapping(value="/jobs/clearStuckJobs")
	public int clearStuckJobs() {
		return scheduleService.clearStuckJobs();
	}
	
	private Page<JobRun> sanitise(Page<JobRun> jobRuns) {
		List<JobRun> sanitizedJobRuns = jobRuns.stream()
				.map(this::sanitise)
				.toList();
		return new PageImpl<>(sanitizedJobRuns, jobRuns.getPageable(), jobRuns.getTotalElements());
	}

	private JobRun sanitise(JobRun jobRun) {
		jobRun.suppressParameters();
		jobRun.setTerminologyServerUrl(null);
		jobRun.setIssuesReported(null);
		jobRun.setDebugInfo(null);
		return jobRun;
	}
}
