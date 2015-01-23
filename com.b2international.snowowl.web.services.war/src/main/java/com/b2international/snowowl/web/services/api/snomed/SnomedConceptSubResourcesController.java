/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.api.snomed;

import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.b2international.snowowl.rest.domain.IComponentList;
import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConcept;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescription;
import com.b2international.snowowl.rest.snomed.domain.ISnomedRelationship;
import com.b2international.snowowl.rest.snomed.service.ISnomedDescriptionService;
import com.b2international.snowowl.rest.snomed.service.ISnomedStatementBrowserService;
import com.b2international.snowowl.rest.snomed.service.ISnomedTerminologyBrowserService;
import com.b2international.snowowl.web.services.api.AbstractRestService;
import com.b2international.snowowl.web.services.domain.PageableCollectionResource;
import com.b2international.snowowl.web.services.domain.snomed.SnomedConceptDescriptions;
import com.b2international.snowowl.web.services.domain.snomed.SnomedInboundRelationships;
import com.b2international.snowowl.web.services.domain.snomed.SnomedOutboundRelationships;
import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

/**
 * @author mczotter
 * @since 1.0
 */
@Api("SNOMED CT Concepts")
@RestController
@RequestMapping(
		value="/snomed-ct/{version}",
		produces={ AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE })
public class SnomedConceptSubResourcesController extends AbstractSnomedRestService {

	@Autowired
	protected ISnomedDescriptionService descriptions;

	@Autowired
	protected ISnomedStatementBrowserService statements;

	@Autowired
	protected ISnomedTerminologyBrowserService terminology;

	@ApiOperation(
			value="Retrieve descriptions of a concept", 
			notes="Returns all descriptions associated with the specified concept.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/concepts/{conceptId}/descriptions", method=RequestMethod.GET)
	public SnomedConceptDescriptions getConceptDescriptions(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId) {

		return getConceptDescriptionsOnTask(version, null, conceptId);
	}

	@ApiOperation(
			value="Retrieve descriptions of a concept on task", 
			notes="Returns all descriptions associated with the specified concept, located on a task branch.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version, task or concept not found")
	})
	@RequestMapping(value="/tasks/{taskId}/concepts/{conceptId}/descriptions", method=RequestMethod.GET)
	public SnomedConceptDescriptions getConceptDescriptionsOnTask(
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
		final List<ISnomedDescription> conceptDescriptions = descriptions.readConceptDescriptions(conceptRef);

		final SnomedConceptDescriptions result = new SnomedConceptDescriptions();
		result.setConceptDescriptions(conceptDescriptions);
		return result;
	}

	@ApiOperation(
			value="Retrieve inbound relationships of a concept",
			notes="Returns a list of all inbound relationships of the specified concept.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/concepts/{conceptId}/inbound-relationships", method=RequestMethod.GET)
	public SnomedInboundRelationships getInboundStatements(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,
			
			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit) {

		return getInboundStatementsOnTask(version, null, conceptId, offset, limit);
	}

	@ApiOperation(
			value="Retrieve inbound relationships of a concept on task",
			notes="Returns a list of all inbound relationships of the specified concept on a task branch.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version, task or concept not found")
	})
	@RequestMapping(value="/tasks/{taskId}/concepts/{conceptId}/inbound-relationships", method=RequestMethod.GET)
	public SnomedInboundRelationships getInboundStatementsOnTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit) {

		final IComponentRef conceptRef = createComponentRef(version, taskId, conceptId);
		final IComponentList<ISnomedRelationship> inboundEdges = statements.getInboundEdges(conceptRef, offset, limit);

		final SnomedInboundRelationships result = new SnomedInboundRelationships();
		result.setTotal(inboundEdges.getTotalMembers());
		result.setInboundRelationships(inboundEdges.getMembers());
		return result;
	}

	@ApiOperation(
			value="Retrieve outbound relationships of a concept",
			notes="Returns a list of all outbound relationships of the specified concept.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/concepts/{conceptId}/outbound-relationships", method=RequestMethod.GET)
	public SnomedOutboundRelationships getOutboundStatements(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit) {

		return getOutboundStatementsOnTask(version, null, conceptId, offset, limit);
	}

	@ApiOperation(
			value="Retrieve outbound relationships of a concept on task",
			notes="Returns a list of all outbound relationships of the specified concept on a task branch.",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version, task or concept not found")
	})
	@RequestMapping(value="/tasks/{taskId}/concepts/{conceptId}/outbound-relationships", method=RequestMethod.GET)
	public SnomedOutboundRelationships getOutboundStatementsOnTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit) {

		final IComponentRef conceptRef = createComponentRef(version, taskId, conceptId);
		final IComponentList<ISnomedRelationship> outboundEdges = statements.getOutboundEdges(conceptRef, offset, limit);

		final SnomedOutboundRelationships result = new SnomedOutboundRelationships();
		result.setTotal(outboundEdges.getTotalMembers());
		result.setOutboundRelationships(outboundEdges.getMembers());
		return result;
	}

	@ApiOperation(
			value = "Retrieve descendants of a concept",
			notes = "Returns a list of descendant concepts of the specified concept on a version",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(
			value="/concepts/{conceptId}/descendants",
			method = RequestMethod.GET)
	public PageableCollectionResource<ISnomedConcept> getDescendants(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,
			
			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit,

			@ApiParam(value="Return direct descendants only")
			@RequestParam(value="direct", defaultValue="false", required=false) 
			final boolean direct) {

		return getDescendantsOnTask(version, null, conceptId, offset, limit, direct);
	}

	@ApiOperation(
			value = "Retrieve descendants of a concept",
			notes = "Returns a list of descendant concepts of the specified concept on a task",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version, task or concept not found")
	})
	@RequestMapping(
			value="/tasks/{taskId}/concepts/{conceptId}/descendants",
			method = RequestMethod.GET)
	public PageableCollectionResource<ISnomedConcept> getDescendantsOnTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit,

			@ApiParam(value="Return direct descendants only")
			@RequestParam(value="direct", defaultValue="false", required=false) 
			final boolean direct) {

		final IComponentRef ref = createComponentRef(version, taskId, conceptId);
		final IComponentList<ISnomedConcept> descendants = terminology.getDescendants(ref, direct, offset, limit);
		return toPageable(descendants, offset, limit);
	}

	@ApiOperation(
			value = "Retrieve ancestors of a concept",
			notes = "Returns a list of ancestor concepts of the specified concept on a version",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(
			value="/concepts/{conceptId}/ancestors",
			method = RequestMethod.GET)
	public PageableCollectionResource<ISnomedConcept> getAncestors(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit,

			@ApiParam(value="Return direct descendants only")
			@RequestParam(value="direct", defaultValue="false", required=false) 
			final boolean direct) {

		return getAncestorsOnTask(version, null, conceptId, offset, limit, direct);
	}

	@ApiOperation(
			value = "Retrieve ancestors of a concept",
			notes = "Returns a list of ancestor concepts of the specified concept on a task",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version, task or concept not found")
	})
	@RequestMapping(
			value="/tasks/{taskId}/concepts/{conceptId}/ancestors",
			method = RequestMethod.GET)
	public PageableCollectionResource<ISnomedConcept> getAncestorsOnTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false) 
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false) 
			final int limit,

			@ApiParam(value="Return direct descendants only")
			@RequestParam(value="direct", defaultValue="false", required=false) 
			final boolean direct) {

		final IComponentRef ref = createComponentRef(version, taskId, conceptId);
		final IComponentList<ISnomedConcept> ancestors = terminology.getAncestors(ref, direct, offset, limit);
		return toPageable(ancestors, offset, limit);
	}

	private PageableCollectionResource<ISnomedConcept> toPageable(final IComponentList<ISnomedConcept> concepts, final int offset, final int limit) {
		return PageableCollectionResource.of(concepts.getMembers(), offset, limit, concepts.getTotalMembers());
	}

	@ApiOperation(
			value = "Retrieve the preferred term of a concept",
			notes = "Returns the preferred term of the specified concept on a version based on the defined language preferences in the header",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version, concept or preferred term not found")
	})
	@RequestMapping(
			value="/concepts/{conceptId}/pt",
			method = RequestMethod.GET)
	public ISnomedDescription getPreferredTerm(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,
			
			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false) 
			final String languageSetting,

			final HttpServletRequest request) {

		return getPreferredTermOnTask(version, null, conceptId, languageSetting, request);
	}

	@ApiOperation(
			value = "Retrieve the preferred term of a concept",
			notes = "Returns the preferred term of the specified concept on a task based on the defined language preferences in the header",
			response=Void.class)
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Code system version, task, concept or preferred term not found")
	})
	@RequestMapping(
			value="/tasks/{taskId}/concepts/{conceptId}/pt",
			method = RequestMethod.GET)
	public ISnomedDescription getPreferredTermOnTask(
			@ApiParam(value="The code system version")
			@PathVariable(value="version")
			final String version,

			@ApiParam(value="The task")
			@PathVariable(value="taskId")
			final String taskId,

			@ApiParam(value="The concept identifier")
			@PathVariable(value="conceptId")
			final String conceptId,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false) 
			final String languageSetting,

			final HttpServletRequest request) {

		final IComponentRef conceptRef = createComponentRef(version, taskId, conceptId);
		return descriptions.getPreferredTerm(conceptRef, request.getLocales());
	}
}
