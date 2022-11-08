package org.ihtsdo.snowowl.authoring.scheduler.api.rest;

import io.swagger.annotations.*;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.snowowl.authoring.scheduler.api.configuration.WebSecurityConfig;
import org.ihtsdo.snowowl.authoring.scheduler.api.service.AccessControlService;
import org.ihtsdo.snowowl.authoring.scheduler.api.service.ScheduleService;
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

import javax.servlet.http.HttpServletRequest;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class SchedulerController {
	
	@Autowired
	private ScheduleService scheduleService;
	
	@Autowired
	AccessControlService accessControlService;
	
	@Autowired
	WebSecurityConfig config;
	
	@Value("${schedule.manager.terminology.server.uri}")
	String terminologyServerUrl;
	
	protected Logger logger = LoggerFactory.getLogger(this.getClass());
	
	private static final String X_AUTH_TOK = "X-AUTH-token";
	private static final String X_AUTH_USER = "X-AUTH-username";

	@ApiOperation(value="List Job Types")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs", method= RequestMethod.GET)
	public List<JobType> listJobTypes() throws BusinessServiceException {
		return scheduleService.listJobTypes();
	}

	@ApiOperation(value="List job type categories")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}", method= RequestMethod.GET)
	public List<JobCategory> listJobTypeCategories(@PathVariable final String typeName) throws BusinessServiceException {
		return scheduleService.listJobTypeCategories(typeName);
	}

	@ApiOperation(value="Get job details")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}", method= RequestMethod.GET)
	public Job getJobDetails(@PathVariable final String typeName,
			@PathVariable final String jobName) throws BusinessServiceException {
		return scheduleService.getJob(jobName);
	}

	@ApiOperation(value="List jobs run")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
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
	
	@ApiOperation(value="List all jobs run")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/runs", method= RequestMethod.GET)
	public List<JobRun> listAllJobsRun(HttpServletRequest request,
			@RequestParam(required=false) final Set<JobStatus> statusFilter,
			@RequestParam(required=false) final Integer sinceMins,
			@RequestParam(required=false, defaultValue="0") final Integer page,
			@RequestParam(required=false, defaultValue="50") final Integer size)
		throws BusinessServiceException {
		Pageable pageable = PageRequest.of(page, size, Sort.unsorted());
		
		return santise(scheduleService.listAllJobsRun(statusFilter, sinceMins, pageable));
	}
	
	private Set<String> getVisibleProjects(HttpServletRequest request) throws BusinessServiceException {
		String authToken = request.getHeader(X_AUTH_TOK);
		String username = request.getHeader(X_AUTH_USER);
		//Note that this user is the currently logged in user.  It's also possible to specify
		//a user to the listJobsRun server to filter it down to the user who originally ran the reprot
		
		if (StringUtils.isEmpty(authToken) || StringUtils.isEmpty(username)) {
			//Are local override values available?
			authToken = config.getOverrideToken();
			username = config.getOverrideUsername();
			if (StringUtils.isEmpty(authToken) || StringUtils.isEmpty(username)) {
				throw new BusinessServiceException("Failed to recover authentication details from HTTP headers");
			} else {
				logger.warn("Auth token not recovered from headers, using locally supplied override for user: {}", username);
			}
		}
		return accessControlService.getProjects(username, terminologyServerUrl, authToken);
	}

	@ApiOperation(value="Run job")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/runs", method= RequestMethod.POST)
	public JobRun runJob(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestBody JobRun jobRun) throws BusinessServiceException {
		return scheduleService.runJob(typeName, jobName, jobRun);
	}
	
	@ApiOperation(value="Bulk delete job-runs")
	@ApiResponses({
			@ApiResponse(code = 204, message = "Deleted")
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
	
	@ApiOperation(value="Schedule job")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/schedule", method= RequestMethod.POST)
	public JobSchedule scheduleJob(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestBody JobSchedule jobSchedule) throws BusinessServiceException {
		return scheduleService.scheduleJob(typeName, jobName, jobSchedule);
	}
	
	@ApiOperation(value="Remove job schedule")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/schedule/{scheduleId}", method= RequestMethod.DELETE)
	public void deleteSchedule(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@PathVariable final UUID scheduleId) throws BusinessServiceException {
		scheduleService.deleteSchedule(typeName, jobName, scheduleId);
	}
	
	@ApiOperation(value="Get job run")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/runs/{runId}", method= RequestMethod.GET)
	public JobRun getJobStatus(@PathVariable final String typeName,
			@PathVariable final String jobName,
			@PathVariable final UUID runId) throws BusinessServiceException {
		return scheduleService.getJobRun(typeName, jobName, runId);
	}
	
	@ApiOperation(value="Delete job run")
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
	
	@ApiOperation(value="Re-initialise")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/initialise", method= RequestMethod.GET)
	public void initialise() throws BusinessServiceException {
		scheduleService.initialise();
	}

	@ApiOperation(value="List whitelisted concepts for the given job & code system")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/{codeSystemShortname}/whitelist", method= RequestMethod.GET)
	public Set<WhiteListedConcept> getWhiteList(
			@PathVariable final String typeName,
			@PathVariable final String codeSystemShortname,
			@PathVariable final String jobName) throws BusinessServiceException {
		return scheduleService.getWhiteList(typeName, codeSystemShortname, jobName);
	}
	
	@ApiOperation(value="Set whitelisted concept for the given job & code system")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/{codeSystemShortname}/whitelist", method= RequestMethod.POST)
	public void setWhiteList(
			@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@PathVariable final String codeSystemShortname,
			@RequestBody Set<WhiteListedConcept> whiteList) throws BusinessServiceException {
		scheduleService.setWhiteList(typeName, jobName, codeSystemShortname, whiteList);
	}
	
	private List<JobRun> santise(Page<JobRun> jobRuns) {
		return jobRuns.stream()
				.peek(j -> j.suppressParameters())
				.peek(j -> j.setTerminologyServerUrl(null))
				.peek(j -> j.setIssuesReported(null))
				.peek(j -> j.setDebugInfo(null))
				.collect(Collectors.toList());
	}
}
