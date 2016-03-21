package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskStatus;
import org.ihtsdo.snowowl.authoring.single.api.service.UiStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Api("UI State")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class UiStateController extends AbstractSnomedRestService {

	public static final String SHARED = "SHARED";
	@Autowired
	private UiStateService uiStateService;
	
	@Autowired
	private TaskService taskService;

	@ApiOperation(value="Persist User UI panel state", notes="This endpoint may be used to persist UI state using any json object. " +
			"State is stored and retrieved under Project, Task, User and panel. This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}", method= RequestMethod.POST)
	public void persistTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException, BusinessServiceException, JiraException {

		// TODO - move this to an explicit "Start progress" endpoint.
		taskService.conditionalStateTransition(projectKey, taskKey, TaskStatus.NEW, TaskStatus.IN_PROGRESS);

		uiStateService.persistTaskPanelState(projectKey, taskKey, ControllerHelper.getUsername(), panelId, jsonState);
	}

	@ApiOperation(value="Retrieve User UI panel state", notes="This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}", method= RequestMethod.GET)
	public String retrieveTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId) throws IOException {

		return uiStateService.retrieveTaskPanelState(projectKey, taskKey, ControllerHelper.getUsername(), panelId);
	}

	@ApiOperation(value="Delete User UI panel state", notes="This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}", method= RequestMethod.DELETE)
	public void deleteTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId) throws IOException {

		uiStateService.deleteTaskPanelState(projectKey, taskKey, ControllerHelper.getUsername(), panelId);
	}


	@ApiOperation(value="Persist Shared UI panel state", notes="This endpoint may be used to persist UI state using any json object. " +
			"State is stored and retrieved under Project, Task and panel shared between all users. This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}", method= RequestMethod.POST)
	public void persistSharedTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException, BusinessServiceException, JiraException {

		uiStateService.persistTaskPanelState(projectKey, taskKey, SHARED, panelId, jsonState);
	}

	@ApiOperation(value="Retrieve Shared UI panel state", notes="This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}", method= RequestMethod.GET)
	public String retrieveSharedTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId) throws IOException {

		return uiStateService.retrieveTaskPanelState(projectKey, taskKey, SHARED, panelId);
	}

	@ApiOperation(value="Delete Shared UI panel state", notes="This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/shared-ui-state/{panelId}", method= RequestMethod.DELETE)
	public void deleteSharedTaskUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId) throws IOException {

		uiStateService.deleteTaskPanelState(projectKey, taskKey, SHARED, panelId);
	}


	@ApiOperation(value="Persist User UI panel state", notes="This endpoint may be used to persist UI state using any json object. " +
			"State is stored and retrieved under Project, Task, User and panel. This also sets the Task status to In Progress if it's New.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/ui-state/{panelId}", method= RequestMethod.POST)
	public void persistUiPanelState(@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException, BusinessServiceException, JiraException {

		uiStateService.persistPanelState(ControllerHelper.getUsername(), panelId, jsonState);
	}

	@ApiOperation(value="Retrieve User UI panel state", notes="This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/ui-state/{panelId}", method= RequestMethod.GET)
	public String retrieveUiPanelState(@PathVariable final String panelId) throws IOException {

		return uiStateService.retrievePanelState(ControllerHelper.getUsername(), panelId);
	}

	@ApiOperation(value="Delete User UI panel state", notes="This endpoint may be used to delete the UI state.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/ui-state/{panelId}", method= RequestMethod.DELETE)
	public void deleteUiPanelState(@PathVariable final String panelId) throws IOException {

		uiStateService.deletePanelState(ControllerHelper.getUsername(), panelId);
	}

}
