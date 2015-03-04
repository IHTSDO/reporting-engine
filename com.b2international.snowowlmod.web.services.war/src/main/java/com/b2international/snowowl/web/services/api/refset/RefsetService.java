/**
* Copyright 2014 IHTSDO
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.b2international.snowowl.web.services.api.refset;

import java.util.Collection;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.web.services.api.AbstractRestService;
import com.b2international.snowowl.web.services.api.snomed.AbstractSnomedRestService;
import com.b2international.snowowlmod.rest.snomed.impl.domain.RefSet;
import com.b2international.snowowlmod.rest.snomed.impl.domain.RefSetComponent;
import com.b2international.snowowlmod.rest.snomed.impl.service.SnomedRefsetServiceImpl;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 *
 */
@Api("Refset Service")
@RestController
@RequestMapping(
		value = "/refset/{version}",
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class RefsetService  extends AbstractSnomedRestService {

	
	@Autowired
	private SnomedRefsetServiceImpl service;
	
	@ApiOperation(
			value="Retrieves concept's compostie view for a given version and concept ids", 
			notes="Returns a concept list with a composite view of concept from a required version branch and"
					+ " given list concept ids. Composite view is made of all concept's details as well as it's"
					+ " preferred term")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK", response = List.class),
		@ApiResponse(code = 404, message = "Code system version not found")
	})
	@RequestMapping(value="/concepts", method=RequestMethod.POST)
	public @ResponseBody Collection<RefSetComponent> getConcepts(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="Concept ids")
			@RequestBody
			final List<String> ids) {

		return service.getConcepts(version, ids);
	}
	
	
	@ApiOperation(
			value="Retrieves refset header for a given version and refset id", 
			notes="Returns a refset header excluding its members")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK", response = List.class),
		@ApiResponse(code = 404, message = "Code system version not found")
	})
	@RequestMapping(value="/header/{id}", method=RequestMethod.GET)
	public @ResponseBody RefSet getRefsetHeader(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="Refset id")
			@PathVariable(value="id")
			final String id) {

		return service.getRefsetHeader(version, id);
	}
	

}
