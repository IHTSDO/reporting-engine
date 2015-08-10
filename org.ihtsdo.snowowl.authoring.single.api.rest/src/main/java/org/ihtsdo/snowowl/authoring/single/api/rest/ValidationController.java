package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.single.api.service.ValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import us.monoid.json.JSONException;

import java.io.IOException;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class ValidationController extends AbstractSnomedRestService {

	@Autowired
	private ValidationService validationService;

	@ApiOperation(value = "Initiate validation on a task")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.POST)
	public String startValidation(@PathVariable final String projectKey, @PathVariable final String taskKey) throws JiraException,
			JSONException, IOException, BusinessServiceException {
		return validationService.startValidation(projectKey, taskKey, ControllerHelper.getUsername());
	}

	@ApiOperation(value = "Recover the most recent validation on a task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value = "/projects/{projectKey}/tasks/{taskKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey, @PathVariable final String taskKey) throws IOException, JSONException {
		return validationService.getValidationJson(projectKey, taskKey);
	}
	
	@ApiOperation(value = "Initiate validation on a project")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/validation", method = RequestMethod.POST)
	public String startValidation(@PathVariable final String projectKey) throws JiraException, JSONException, IOException,
			BusinessServiceException {
		return validationService.startValidation(projectKey, ControllerHelper.getUsername());
	}

	@ApiOperation(value = "Recover the most recent validation on project")
	@ApiResponses({ @ApiResponse(code = 200, message = "OK") })
	@RequestMapping(value = "/projects/{projectKey}/validation", method = RequestMethod.GET)
	@ResponseBody
	public String getValidation(@PathVariable final String projectKey) throws IOException, JSONException {
		return validationService.getValidationJson(projectKey);
	}

}
