package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.single.api.service.UiStateService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@Api("UI State")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class UiStateController extends AbstractSnomedRestService {

	@Autowired
	private UiStateService uiStateService;

	@ApiOperation(value="Persist UI panel state", notes="This endpoint may be used to persist UI state using any json object. " +
			"State is stored and retrieved under project, task, user and panel.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}", method= RequestMethod.POST)
	public void persistUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId, @RequestBody final String jsonState) throws IOException {

		UserDetails details = ControllerHelper.getUserDetails();
		uiStateService.persistPanelState(projectKey, taskKey, details.getUsername(), panelId, jsonState);
	}

	@ApiOperation(value="Retrieve UI panel state", notes="This endpoint may be used to retrieve UI state using any json object.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/ui-state/{panelId}", method= RequestMethod.GET)
	public String retrieveUiPanelState(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String panelId) throws IOException {

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		UserDetails details = (UserDetails) authentication.getPrincipal();
		return uiStateService.retrievePanelState(projectKey, taskKey, details.getUsername(), panelId);
	}

}
