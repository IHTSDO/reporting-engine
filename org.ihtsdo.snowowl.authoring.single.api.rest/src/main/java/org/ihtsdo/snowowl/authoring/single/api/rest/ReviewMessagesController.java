package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewConcept;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewMessageCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.concurrent.ExecutionException;

@Api("Review Messages")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class ReviewMessagesController extends AbstractSnomedRestService {

	@Autowired
	private ReviewService reviewService;

	@ApiOperation(value="Retrieve a list of stored details for a task review concept, including last view date for the user and a list of messages.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review", method= RequestMethod.GET)
	public List<ReviewConcept> retrieveTaskReview(
			@PathVariable final String projectKey,

			@PathVariable final String taskKey) throws BusinessServiceException {

		return reviewService.retrieveTaskReviewConceptDetails(projectKey, taskKey, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Retrieve a list of stored details for a project review concept, including last view date for the user and a list of messages.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/review", method= RequestMethod.GET)
	public List<ReviewConcept> retrieveProjectReview(

			@PathVariable final String projectKey) throws BusinessServiceException {

		return reviewService.retrieveProjectReviewConceptDetails(projectKey, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Record a review feedback message on task concepts.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/message", method= RequestMethod.POST)
	public ReviewMessage postTaskReviewMessage(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody ReviewMessageCreateRequest createRequest) {
		return reviewService.postReviewMessage(projectKey, taskKey, createRequest, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Record a review feedback message on project concepts.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/review/message", method= RequestMethod.POST)
	public ReviewMessage postProjectReviewMessage(@PathVariable final String projectKey,
			@RequestBody ReviewMessageCreateRequest createRequest) {
		return reviewService.postReviewMessage(projectKey, null, createRequest, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Mark a task review concept as viewed for this user.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/concepts/{conceptId}/view", method= RequestMethod.POST)
	public void markTaskReviewConceptViewed(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String conceptId) throws ExecutionException, InterruptedException {
		reviewService.recordConceptView(projectKey, taskKey, conceptId, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Mark a project review concept as viewed for this user.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/review/concepts/{conceptId}/view", method= RequestMethod.POST)
	public void markProjectReviewConceptViewed(@PathVariable final String projectKey,
			@PathVariable final String conceptId) throws ExecutionException, InterruptedException {
		reviewService.recordConceptView(projectKey, null, conceptId, ControllerHelper.getUsername());
	}

}
