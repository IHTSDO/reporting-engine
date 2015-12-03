package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.*;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewMessageCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@Api("Review")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class ReviewController extends AbstractSnomedRestService {

	@Autowired
	private ReviewService reviewService;

	@ApiOperation(value="Retrieve the review list for a Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review", method= RequestMethod.GET)
	public AuthoringTaskReview retrieveTaskReview(
			@PathVariable final String projectKey,

			@PathVariable final String taskKey,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) throws BusinessServiceException {

		return reviewService.retrieveTaskReview(projectKey, taskKey, getExtendedLocales(languageSetting), ControllerHelper.getUsername());
	}

	@ApiOperation(value="Retrieve the review list for a Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/review", method= RequestMethod.GET)
	public AuthoringTaskReview retrieveProjectReview(

			@PathVariable final String projectKey,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) throws BusinessServiceException {

		return reviewService.retrieveProjectReview(projectKey, getExtendedLocales(languageSetting), ControllerHelper.getUsername());
	}

	@ApiOperation(value="Comment on a Task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/message", method= RequestMethod.POST)
	public ReviewMessage postTaskReviewMessage(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody ReviewMessageCreateRequest createRequest) {
		return reviewService.postReviewMessage(projectKey, taskKey, createRequest, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Comment on a Project")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/review/message", method= RequestMethod.POST)
	public ReviewMessage postProjectReviewMessage(@PathVariable final String projectKey,
			@RequestBody ReviewMessageCreateRequest createRequest) {
		return reviewService.postReviewMessage(projectKey, null, createRequest, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Mark a review and concept pair as read for this user.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/concepts/{conceptId}/read", method= RequestMethod.POST)
	public void markTaskReviewAndConceptRead(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String conceptId) throws ExecutionException, InterruptedException {
		reviewService.markAsRead(projectKey, taskKey, conceptId, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Mark a review and concept pair as read for this user.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/review/concepts/{conceptId}/read", method= RequestMethod.POST)
	public void markProjectReviewAndConceptRead(@PathVariable final String projectKey,
			@PathVariable final String conceptId) throws ExecutionException, InterruptedException {
		reviewService.markAsRead(projectKey, null, conceptId, ControllerHelper.getUsername());
	}

}
