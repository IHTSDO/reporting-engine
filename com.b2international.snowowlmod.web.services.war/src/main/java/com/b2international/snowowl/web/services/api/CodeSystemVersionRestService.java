/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api;

import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

import java.net.URI;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.domain.codesystem.ICodeSystemVersion;
import com.b2international.snowowl.rest.service.codesystem.ICodeSystemVersionService;
import com.b2international.snowowl.web.services.domain.CollectionResource;
import com.b2international.snowowl.web.services.domain.VersionInput;
import com.b2international.snowowl.web.services.util.Responses;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * @author Andras Peteri
 * @author Mark Czotter
 * @since 1.0
 */
@Api("Code System Metadata")
@RestController
@RequestMapping(
		value = "/codesystems/{shortName}/versions",
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class CodeSystemVersionRestService extends AbstractRestService {

	@Autowired
	protected ICodeSystemVersionService delegate;

	@ApiOperation(
			value="Retrieve all code system versions",
			notes="Returns a list containing all released code system versions for the specified code system.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system not found", response = RestApiError.class)
	})
	@RequestMapping(method=RequestMethod.GET)
	public CollectionResource<ICodeSystemVersion> getAllCodeSystemVersionsByShortName(
			@ApiParam(value="The code system short name")
			@PathVariable(value="shortName") final String shortName) {

		return CollectionResource.of(delegate.getCodeSystemVersions(shortName));
	}

	@ApiOperation(
			value="Retrieve code system version by identifier",
			notes="Returns a released code system version for the specified code system with the given version identifier.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system or version not found", response = RestApiError.class)
	})
	@RequestMapping(value="/{version}", method=RequestMethod.GET)
	public ICodeSystemVersion getCodeSystemVersionByShortNameAndVersionId(
			@ApiParam(value="The code system short name")
			@PathVariable(value="shortName") 
			final String shortName,
			
			@ApiParam(value="The code system version")
			@PathVariable(value="version") 
			final String version) {

		return delegate.getCodeSystemVersionById(shortName, version);
	}
	
	@ApiOperation(
			value="Create a new code system version",
			notes="Creates a new code system version in the specified terminology.")
	@ApiResponses({
		@ApiResponse(code = 201, message = "Created"),
		@ApiResponse(code = 404, message = "Code system not found", response = RestApiError.class)
	})
	@RequestMapping(method=RequestMethod.POST, consumes = { AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
	@ResponseStatus(value=HttpStatus.CREATED)
	public ResponseEntity<Void> createVersion(
			@ApiParam(value="The code system short name")
			@PathVariable(value="shortName") 
			final String shortName, 
			
			@ApiParam(value="Version parameters")
			@RequestBody final VersionInput input) {

		final ICodeSystemVersion version = delegate.createVersion(shortName, input);
		return Responses.created(getVersionURI(shortName, version.getVersion())).build();
	}

	private URI getVersionURI(String shortName, String version) {
		return linkTo(methodOn(CodeSystemVersionRestService.class).getCodeSystemVersionByShortNameAndVersionId(shortName, version)).toUri();
	}
}
