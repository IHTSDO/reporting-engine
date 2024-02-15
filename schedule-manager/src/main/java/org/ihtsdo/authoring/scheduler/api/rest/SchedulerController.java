package org.ihtsdo.authoring.scheduler.api.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

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

	@Value("${schedule.manager.terminology.server.uri}")
	String terminologyServerUrl;

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
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs", method= RequestMethod.GET)
	public List<JobType> listJobTypes() throws BusinessServiceException {
		return scheduleService.listJobTypes();
	}

	@Operation(summary="List job type categories")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}", method= RequestMethod.GET)
	public List<JobCategory> listJobTypeCategories(@PathVariable final String typeName) throws BusinessServiceException {
		return scheduleService.listJobTypeCategories(typeName);
	}

	@Operation(summary="Get job details")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}", method= RequestMethod.GET)
	public Job getJobDetails(@PathVariable final String typeName,
			@PathVariable final String jobName) throws BusinessServiceException {
		return scheduleService.getJob(jobName);
	}

	@Operation(summary="List jobs run")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/runs", method= RequestMethod.GET)
	public List<JobRun> listJobsRun(HttpServletRequest request,
			@PathVariable final String typeName,
			@PathVariable final String jobName,
			@RequestParam(required=false, defaultValue="0") final Integer page,
			@RequestParam(required=false, defaultValue="20") final Integer size,
			@RequestParam(required=false) final String user) throws BusinessServiceException {
		
		Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
		return scheduleService.listJobsRun(typeName, jobName, user, getVisibleProjects(request), pageable).getContent();
	}
	
	@Operation(summary="List all jobs run")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/runs", method= RequestMethod.GET)
	public List<JobRun> listAllJobsRun(HttpServletRequest request,
			@RequestParam(required=false) final Set<JobStatus> statusFilter,
			@RequestParam(required=false) final Integer sinceMins,
			@RequestParam(required=false, defaultValue="0") final Integer page,
			@RequestParam(required=false, defaultValue="50") final Integer size)
		throws BusinessServiceException {
		Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
		
		return sanitise(scheduleService.listAllJobsRun(statusFilter, sinceMins, pageable));
	}

	@Operation(summary="List batches")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "OK")})
	@GetMapping(value="/jobs/listbatches")
	public Iterable<JobRunBatch> listLastBatch(@RequestParam(required=false, defaultValue = "0") final Long number) {
		return batchTools.getLastNBatches(number);
	}

	@Operation(summary="Get batch")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "OK")})
	@GetMapping(value="/jobs/batch/{id}")
	public List<JobRun> getBatch(@PathVariable final Long id) {
		return batchTools.getBatch(id);
	}

	@Operation(summary="Run all jobs")
	@ApiResponses({@ApiResponse(responseCode = "200", description = "OK")})
	@RequestMapping(value="/jobs/runall", method= RequestMethod.POST)
	@ResponseBody
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
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/runs", method= RequestMethod.POST)
	public JobRun runJob(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestBody JobRun jobRun) throws BusinessServiceException {
		return scheduleService.runJob(typeName, jobName, jobRun);
	}
	
	@Operation(summary="Bulk delete job-runs")
	@ApiResponses({
			@ApiResponse(responseCode = "204", description = "Deleted")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/runs/delete", method= RequestMethod.POST)
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
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/schedule", method= RequestMethod.POST)
	public JobSchedule scheduleJob(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestBody JobSchedule jobSchedule) throws BusinessServiceException {
		return scheduleService.scheduleJob(typeName, jobName, jobSchedule);
	}
	
	@Operation(summary="Remove job schedule")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/schedule/{scheduleId}", method= RequestMethod.DELETE)
	public void deleteSchedule(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@PathVariable final UUID scheduleId) throws BusinessServiceException {
		scheduleService.deleteSchedule(typeName, jobName, scheduleId);
	}
	
	@Operation(summary="Get job run")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/runs/{runId}", method= RequestMethod.GET)
	public JobRun getJobStatus(@PathVariable final String typeName,
			@PathVariable final String jobName,
			@PathVariable final UUID runId) throws BusinessServiceException {
		return scheduleService.getJobRun(typeName, jobName, runId);
	}
	
	@Operation(summary="Delete job run")
	@RequestMapping(value = "/jobs/{typeName}/{jobName}/runs/{runId}", method = RequestMethod.DELETE)
	public ResponseEntity<?> deleteJobRun(@PathVariable final String typeName,
			@PathVariable final String jobName,
			@PathVariable final UUID runId) {
		boolean deleted = scheduleService.deleteJobRun(typeName, jobName, runId);
		if (!deleted) {
			return new ResponseEntity<JobRun>(HttpStatus.NOT_FOUND);
		}
		return new ResponseEntity<JobRun>(HttpStatus.NO_CONTENT);
	}
	
	@Operation(summary="Re-initialise")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/initialise", method= RequestMethod.GET)
	public void initialise(HttpServletRequest request) throws BusinessServiceException {
		scheduleService.initialise();
		AuthData authData = getAuthData(request);
		accessControlService.clearCache(authData.userName);
	}

	@Operation(summary="List whitelisted concepts for the given job & code system")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/{codeSystemShortname}/whitelist", method= RequestMethod.GET)
	public Set<WhiteListedConcept> getWhiteList(
			@PathVariable final String typeName,
			@PathVariable final String codeSystemShortname,
			@PathVariable final String jobName) throws BusinessServiceException {
		return scheduleService.getWhiteList(typeName, codeSystemShortname, jobName);
	}
	
	@Operation(summary="Set whitelisted concept for the given job & code system")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/{codeSystemShortname}/whitelist", method= RequestMethod.POST)
	public void setWhiteList(
			@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@PathVariable final String codeSystemShortname,
			@RequestBody Set<WhiteListedConcept> whiteList) throws BusinessServiceException {
		scheduleService.setWhiteList(typeName, jobName, codeSystemShortname, whiteList);
	}

	@Operation(summary="Clear any jobs that have been stuck for more than 10 hours.")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "OK")
	})
	@RequestMapping(value="/jobs/clearStuckJobs", method= RequestMethod.POST)
	public int clearStuckJobs() throws BusinessServiceException {
		return scheduleService.clearStuckJobs();
	}
	
	private List<JobRun> sanitise(Page<JobRun> jobRuns) {
		return jobRuns.stream()
				.peek(j -> j.suppressParameters())
				.peek(j -> j.setTerminologyServerUrl(null))
				.peek(j -> j.setIssuesReported(null))
				.peek(j -> j.setDebugInfo(null))
				.collect(Collectors.toList());
	}
}
