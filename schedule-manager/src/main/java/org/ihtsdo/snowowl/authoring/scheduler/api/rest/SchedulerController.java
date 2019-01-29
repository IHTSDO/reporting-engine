package org.ihtsdo.snowowl.authoring.scheduler.api.rest;

import io.swagger.annotations.*;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.scheduler.api.service.ScheduleService;
import org.snomed.otf.scheduler.domain.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={MediaType.APPLICATION_JSON_VALUE})
public class SchedulerController {
	
	@Autowired
	private ScheduleService scheduleService;

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
	@RequestMapping(value="/jobs/{typeName}/{jobName}", method= RequestMethod.POST)
	public Job getJobDetails(@PathVariable final String typeName,
			@PathVariable final String jobName) throws BusinessServiceException {
		return scheduleService.getJob(jobName);
	}

	@ApiOperation(value="List jobs run")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/runs", method= RequestMethod.GET)
	public List<JobRun> listJobsRun(@PathVariable final String typeName,
			@PathVariable final String jobName,
			@RequestParam(required=false) final String user) throws BusinessServiceException {
		return scheduleService.listJobsRun(typeName, jobName, user);
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
	@RequestMapping(value="/jobs/intitialise", method= RequestMethod.GET)
	public void getJobStatus() throws BusinessServiceException {
		scheduleService.initialise();
	}

	@ApiOperation(value="List whitelisted concepts for the given job")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/whitelist", method= RequestMethod.GET)
	public Set<WhiteListedConcept> getWhiteList(@PathVariable final String typeName,
			@PathVariable final String jobName) throws BusinessServiceException {
		return scheduleService.getWhiteList(typeName, jobName);
	}
	
	@ApiOperation(value="Set whitelisted concept for the given job")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/jobs/{typeName}/{jobName}/whitelist", method= RequestMethod.POST)
	public void setWhiteList(@PathVariable final String typeName, 
			@PathVariable final String jobName,
			@RequestBody Set<WhiteListedConcept> whiteList) throws BusinessServiceException {
		scheduleService.setWhiteList(typeName, jobName, whiteList);
	}
}
