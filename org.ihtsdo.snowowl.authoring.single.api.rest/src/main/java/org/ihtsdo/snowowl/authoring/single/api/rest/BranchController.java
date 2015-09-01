package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.BranchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

@Api("Branch")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class BranchController extends AbstractSnomedRestService {

	@Autowired
	private BranchService branchService;

	@ApiOperation(value="Generate the conflicts report between the Task and the Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase", method= RequestMethod.GET)
	public ConflictReport retrieveTaskConflicts(@PathVariable final String projectKey, @PathVariable final String taskKey,
			HttpServletRequest request) throws BusinessServiceException {
		return branchService.retrieveConflictReport(projectKey, taskKey, Collections.list(request.getLocales()));
	}
	
	@ApiOperation(value="Generate the conflicts report between the Project and MAIN")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/rebase", method= RequestMethod.GET)
	public ConflictReport retrieveProjectConflicts(@PathVariable final String projectKey, HttpServletRequest request) throws BusinessServiceException {
		return branchService.retrieveConflictReport(projectKey,Collections.list(request.getLocales()));
	}

	@ApiOperation(value="Rebase the task from the project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/rebase", method= RequestMethod.POST)
	public void rebaseTask(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		//The branch object that's returned from this function is empty, so suppressing it here to avoid confusion.
		branchService.rebaseTask(projectKey, taskKey, mergeRequest, ControllerHelper.getUsername());
	}
	
	@ApiOperation(value="Rebase the project from MAIN")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/rebase", method= RequestMethod.POST)
	public void rebaseProject(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		//The branch object that's returned from this function is empty, so suppressing it here to avoid confusion.
		branchService.rebaseProject(projectKey, mergeRequest, ControllerHelper.getUsername());
	}
	
	@ApiOperation(value="Promote the task to the Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/promote", method= RequestMethod.POST)
	public void promoteTask(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		//The branch object that's returned from this function is empty, so suppressing it here to avoid confusion.
		branchService.promoteTask(projectKey, taskKey, mergeRequest, ControllerHelper.getUsername());
	}
	
	@ApiOperation(value="Promote the project to MAIN")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/promote", method= RequestMethod.POST)
	public void promoteProject(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody MergeRequest mergeRequest) throws BusinessServiceException {
		//The branch object that's returned from this function is empty, so suppressing it here to avoid confusion.
		branchService.promoteProject(projectKey, mergeRequest, ControllerHelper.getUsername());
	}


}
