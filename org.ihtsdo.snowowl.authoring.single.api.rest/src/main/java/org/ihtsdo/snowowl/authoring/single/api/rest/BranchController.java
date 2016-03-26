package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.*;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.ihtsdo.snowowl.authoring.single.api.service.monitor.MonitorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import com.b2international.snowowl.core.merge.Merge;

@Api("Branch")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class BranchController extends AbstractSnomedRestService {

	@Autowired
	private BranchService branchService;

	@Autowired
	private MonitorService monitorService;

	@ApiOperation(value="Generate the conflicts report between the Task and the Project.",
			notes = "The new report has a limited lifespan and can become stale early if a change is made on the Task or Project. " +
			"This endpoint also creates a monitor of this Task and report.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase-conflicts", method= RequestMethod.POST)
	public ConflictReport retrieveTaskConflicts(
			@PathVariable final String projectKey,

			@PathVariable final String taskKey,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) throws BusinessServiceException {

		final ConflictReport conflictReport = branchService.createConflictReport(projectKey, taskKey, getExtendedLocales(languageSetting));
		monitorService.updateUserFocus(ControllerHelper.getUsername(), projectKey, taskKey, conflictReport);
		return conflictReport;
	}
	
	@ApiOperation(value="Generate the conflicts report between the Project and MAIN.",
			notes =	"The new report has a limited lifespan and can become stale early if a change is made on the Project or MAIN. " +
			"This endpoint also creates a monitor of this Project and report.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/rebase-conflicts", method= RequestMethod.POST)
	public ConflictReport retrieveProjectConflicts(

			@PathVariable final String projectKey,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) throws BusinessServiceException {

		final ConflictReport conflictReport = branchService.createConflictReport(projectKey, getExtendedLocales(languageSetting));
		monitorService.updateUserFocus(ControllerHelper.getUsername(), projectKey, null, conflictReport);
		return conflictReport;
	}

	@ApiOperation(value="Rebase the Task from the Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase", method= RequestMethod.POST)
	public Merge rebaseTask(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		return branchService.rebaseTask(projectKey, taskKey, mergeRequest, ControllerHelper.getUsername());
	}
	
	@ApiOperation(value="Rebase the Project from MAIN")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/rebase", method= RequestMethod.POST)
	public Merge rebaseProject(@PathVariable final String projectKey,
			@RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		return branchService.rebaseProject(projectKey, mergeRequest, ControllerHelper.getUsername());
	}
	
	@ApiOperation(value="Promote the Task to the Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/promote", method= RequestMethod.POST)
	public void promoteTask(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody MergeRequest mergeRequest) throws BusinessServiceException, JiraException {
		//The branch object that's returned from this function is empty, so suppressing it here to avoid confusion.
		branchService.promoteTask(projectKey, taskKey, mergeRequest, ControllerHelper.getUsername());
	}
	
	@ApiOperation(value="Promote the Project to MAIN")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/promote", method= RequestMethod.POST)
	public Merge promoteProject(@PathVariable final String projectKey, @RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		//The branch object that's returned from this function is empty, so suppressing it here to avoid confusion.
		return branchService.promoteProject(projectKey, mergeRequest, ControllerHelper.getUsername());
	}


}
