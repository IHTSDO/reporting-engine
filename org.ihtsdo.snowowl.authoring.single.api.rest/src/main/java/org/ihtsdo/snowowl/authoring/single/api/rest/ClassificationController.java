package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import net.rcarz.jiraclient.JiraException;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.service.ClassificationService;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import us.monoid.json.JSONException;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class ClassificationController extends AbstractSnomedRestService {

	@Autowired
	private ClassificationService classificationService;

	@Autowired
	private TaskService taskService;

	@ApiOperation(value="Initiate the classifier on a Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/classifications", method= RequestMethod.POST)
	public Classification startClassificationOfTask(@PathVariable final String projectKey, @PathVariable final String taskKey) throws JiraException, RestClientException, JSONException, BusinessServiceException {
		final String branchPath = taskService.getTaskBranchPathUsingCache(projectKey, taskKey);
		return classificationService.startClassification(projectKey, taskKey, branchPath, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Initiate the classifier on a Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/classifications", method= RequestMethod.POST)
	public Classification startClassificationOfProject(@PathVariable final String projectKey) throws JiraException, RestClientException, JSONException, BusinessServiceException {
		final String branchPath = taskService.getProjectBranchPathUsingCache(projectKey);
		return classificationService.startClassification(projectKey, null, branchPath, ControllerHelper.getUsername());
	}

}
