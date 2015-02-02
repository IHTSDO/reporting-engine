/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api.snomed;

import com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration;
import com.b2international.snowowl.rest.snomed.service.ISnomedRf2ImportService;
import com.b2international.snowowl.web.services.api.AbstractRestService;
import com.b2international.snowowl.web.services.domain.snomed.SnomedImportDetails;
import com.b2international.snowowl.web.services.domain.snomed.SnomedImportRestConfiguration;
import com.b2international.snowowl.web.services.util.Responses;
import com.b2international.snowowlmod.rest.snomed.impl.domain.SnomedImportConfiguration;
import com.wordnik.swagger.annotations.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import static com.b2international.snowowl.web.services.domain.snomed.SnomedImportStatus.getImportStatus;
import static com.google.common.base.Preconditions.checkNotNull;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.linkTo;
import static org.springframework.hateoas.mvc.ControllerLinkBuilder.methodOn;

/**
 * @author apeteri
 * @since 1.0
 */
@RestController
@RequestMapping(
		value="/snomed-ct/{version}",
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
@Api(value="SNOMED CT Import")
public class SnomedImportRestService extends AbstractSnomedRestService {

	@Autowired
	private ISnomedRf2ImportService delegate;

	@ApiOperation(
			value="Import SNOMED CT content",
			notes="Configures processes to import RF2 based archives. The configured process will wait until the archive actually uploaded via the <em>/archive</em> endpoint. "
					+ "The actual import process will start after the file upload completed.")
	@ApiResponses({
		@ApiResponse(code = 201, message = "Created"),
		@ApiResponse(code = 404, message = "Code system version not found"),
	})
	@RequestMapping(value="/imports",
		method=RequestMethod.POST,
		consumes={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
	@ResponseStatus(HttpStatus.CREATED)
	public ResponseEntity<Void> create(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="Import parameters")
			@RequestBody
			final SnomedImportRestConfiguration importConfiguration) {

		final UUID importId = delegate.create(version, convertToConfiguration(version, importConfiguration));
		return Responses.created(linkTo(methodOn(SnomedImportRestService.class).getImportDetails(version, importId)).toUri()).build();
	}

	@ApiOperation(
			value="Retrieve import run details", 
			notes="Returns the specified import run's configuration and status.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version or import not found"),
	})
	@RequestMapping(value="/imports/{importId}", method=RequestMethod.GET)
	public SnomedImportDetails getImportDetails(
			@ApiParam(value="The code system version")
			@PathVariable(value="version") 
			final String version,
			
			@ApiParam(value="The import identifier")
			@PathVariable(value="importId") 
			final UUID importId) {

		return convertToDetails(importId, delegate.getImportDetails(version, importId));
	}
	
	@ApiOperation(
			value="Delete import run", 
			notes="Removes a pending or finished import configuration from the server.")
	@ApiResponses({
		@ApiResponse(code = 204, message = "Delete successful"),
		@ApiResponse(code = 404, message = "Code system version or import not found"),
	})
	@RequestMapping(value="/imports/{importId}", method=RequestMethod.DELETE)
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void deleteImportDetails(
			@ApiParam(value="The code system version")
			@PathVariable(value="version") 
			final String version,
			
			@ApiParam(value="The import identifier")
			@PathVariable(value="importId") 
			final UUID importId) {
		
		delegate.deleteImportDetails(version, importId);
	}
	
	@ApiOperation(
			value="Upload archive file and start the import", 
			notes="Removes a pending or finished import configuration from the server.")
	@ApiResponses({
		@ApiResponse(code = 204, message = "No content"),
		@ApiResponse(code = 404, message = "Code system version or import not found"),
	})
	@RequestMapping(value="/imports/{importId}/archive", 
		method=RequestMethod.POST, 
		consumes={ MediaType.MULTIPART_FORM_DATA_VALUE })
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void startImport(
			@ApiParam(value="The code system version")
			@PathVariable(value="version") 
			final String version,
			
			@ApiParam(value="The import identifier")
			@PathVariable(value="importId") 
			final UUID importId,
			
			@ApiParam(value="RF2 import archive")
			@RequestPart("file") 
			final MultipartFile file) {
		
		checkNotNull(file, "SNOMED CT RF2 release archive should be specified.");
		
		try (final InputStream is = file.getInputStream()) {
			delegate.startImport(version, importId, is);
		} catch (final IOException e) {
			throw new RuntimeException("Error while reading SNOMED CT RF2 release archive content.");
		}
	}
	
	private SnomedImportDetails convertToDetails(final UUID importId, 
			final ISnomedImportConfiguration configuration) {
		
		final SnomedImportDetails details = new SnomedImportDetails();
		details.setId(importId);
		details.setStatus(getImportStatus(configuration.getStatus()));
		details.setType(configuration.getRf2ReleaseType());
		details.setCompletionDate(configuration.getCompletionDate());
		details.setStartDate(configuration.getStartDate());
		return details;
	}
	
	private ISnomedImportConfiguration convertToConfiguration(final String version,
			final SnomedImportRestConfiguration configuration) {
		
		return new SnomedImportConfiguration(
				configuration.getType(), 
				version,
				configuration.getLanguageRefSetId(), 
				configuration.getCreateVersions());
	}
	
}
