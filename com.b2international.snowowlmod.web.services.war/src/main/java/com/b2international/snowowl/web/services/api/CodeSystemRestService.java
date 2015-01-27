/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.domain.codesystem.ICodeSystem;
import com.b2international.snowowl.rest.service.codesystem.ICodeSystemService;
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
@Api("Code System Metadata")
@RestController
@RequestMapping(
		value = "/codesystems",
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class CodeSystemRestService extends AbstractRestService {

	@Autowired
	protected ICodeSystemService delegate;

	@ApiOperation(
			value="Retrieve all code systems",
			notes="Returns a list containing generic information about registered code systems.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(method=RequestMethod.GET)
	public CollectionResource<ICodeSystem> getCodeSystems() {
		return CollectionResource.of(delegate.getCodeSystems());
	}

	@ApiOperation(
			value="Retrieve code system by short name or OID",
			notes="Returns generic information about a single code system with the specified short name or OID.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system not found", response = RestApiError.class)
	})
	@RequestMapping(value="{shortNameOrOid}", method=RequestMethod.GET)
	public ICodeSystem getCodeSystemByShortNameOrOid(
			@ApiParam(value="The code system identifier (short name or OID)")
			@PathVariable(value="shortNameOrOid") final String shortNameOrOId) {
		return delegate.getCodeSystemByShortNameOrOid(shortNameOrOId);
	}

}
