/**
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 */
package com.b2international.snowowl.web.services.api.snomed;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.domain.history.IHistoryInfo;
import com.b2international.snowowl.rest.snomed.service.ISnomedConceptHistoryService;
import com.b2international.snowowl.web.services.api.AbstractRestService;
import com.b2international.snowowl.web.services.domain.CollectionResource;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * @author apeteri
 * @since 1.0
 */
@Api("SNOMED CT History")
@RestController
@RequestMapping(
		value="/snomed-ct/{version}", 
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class SnomedConceptHistoryRestService extends AbstractSnomedRestService {

	@Autowired
	protected ISnomedConceptHistoryService delegate;

	@ApiOperation(
			value="Retrieve history for a concept", 
			notes="Returns the change history for the specified concept.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK", response = Void.class),
		@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/concepts/{conceptId}/history", method=RequestMethod.GET)
	public CollectionResource<IHistoryInfo> getHistory(
			@ApiParam(value="The code system version")
			@PathVariable(value="version") 
			final String version,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId) {

		return getHistoryOnTask(version, null, conceptId);
	}

	@ApiOperation(
			value="Retrieve history for a concept on task", 
			notes="Returns the change history for the specified concept.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK", response = Void.class),
		@ApiResponse(code = 404, message = "Code system version, task or concept not found")
	})
	@RequestMapping(
			value="/tasks/{taskId}/concepts/{conceptId}/history", 
			method=RequestMethod.GET)
	public CollectionResource<IHistoryInfo> getHistoryOnTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version") 
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId) {

		final IComponentRef conceptRef = createComponentRef(version, taskId, conceptId);
		return CollectionResource.of(delegate.getHistory(conceptRef));
	}
}
