package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.single.api.review.domain.ReviewMessage;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.ReviewMessageCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.service.ReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.concurrent.ExecutionException;
import javax.servlet.http.HttpServletRequest;

@Api("Review")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class ReviewController extends AbstractSnomedRestService {

	@Autowired
	private ReviewService reviewService;

	@ApiOperation(value="Retrieve the review list for a task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review", method= RequestMethod.GET)
	public AuthoringTaskReview retrieveTaskReview(@PathVariable final String projectKey, @PathVariable final String taskKey,
			HttpServletRequest request) throws ExecutionException, InterruptedException {
		return reviewService.retrieveTaskReview(projectKey, taskKey, Collections.list(request.getLocales()), ControllerHelper.getUsername());
	}

	@ApiOperation(value="Comment on a task")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/message", method= RequestMethod.POST)
	public ReviewMessage postReviewMessage(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@RequestBody ReviewMessageCreateRequest createRequest) {
		return reviewService.postReviewMessage(projectKey, taskKey, createRequest, ControllerHelper.getUsername());
	}

	@ApiOperation(value="Mark a review and concept pair as read for this user.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/review/concepts/{conceptId}/read", method= RequestMethod.POST)
	public void markReviewAndConceptRead(@PathVariable final String projectKey, @PathVariable final String taskKey,
			@PathVariable final String conceptId) throws ExecutionException, InterruptedException {
		reviewService.markAsRead(projectKey, taskKey, conceptId, ControllerHelper.getUsername());
	}


}
