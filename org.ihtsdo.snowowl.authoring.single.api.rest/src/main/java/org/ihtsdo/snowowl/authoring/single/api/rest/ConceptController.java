package org.ihtsdo.snowowl.authoring.single.api.rest;

import com.b2international.snowowl.core.domain.IComponentRef;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.datastore.server.domain.StorageRef;
import com.b2international.snowowl.snomed.api.browser.ISnomedBrowserService;
import com.b2international.snowowl.snomed.api.domain.browser.*;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserConcept;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserConceptUpdate;
import com.wordnik.swagger.annotations.*;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.single.api.service.PathHelper;
import org.ihtsdo.snowowl.authoring.single.api.service.ts.SnomedServiceHelper;
import org.ihtsdo.snowowl.authoring.single.api.validation.SnomedBrowserValidationService;
import org.ihtsdo.snowowl.authoring.single.api.validation.SnomedInvalidContent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Api("Concepts")
@Controller
@RequestMapping(produces=MediaType.APPLICATION_JSON_VALUE)
public class ConceptController extends AbstractSnomedRestService {

	@Autowired
	protected ISnomedBrowserService browserService;

	@Autowired
	private SnomedBrowserValidationService validationService;

	@ApiOperation(
			value="Retrieve single concept properties",
			notes="Retrieves a single concept and related information on a task branch.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Void.class),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/concepts/{conceptId}", method=RequestMethod.GET)
	public @ResponseBody ISnomedBrowserConcept getTaskConceptDetails(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@PathVariable
			final String conceptId,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) {

		final IComponentRef conceptRef = SnomedServiceHelper.createComponentRef(PathHelper.getPath(projectKey, taskKey), conceptId);
		return browserService.getConceptDetails(conceptRef, getExtendedLocales(languageSetting));
	}

	@ApiOperation(
			value="Retrieve single concept properties",
			notes="Retrieves a single concept and related information on a project branch.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Void.class),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/projects/{projectKey}/concepts/{conceptId}", method=RequestMethod.GET)
	public @ResponseBody ISnomedBrowserConcept getProjectConceptDetails(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String conceptId,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) {

		final IComponentRef conceptRef = SnomedServiceHelper.createComponentRef(PathHelper.getPath(projectKey), conceptId);
		return browserService.getConceptDetails(conceptRef, getExtendedLocales(languageSetting));
	}

	@ApiOperation(
			value="Create a concept",
			notes="Creates a new Concept on a task branch.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Void.class),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/concepts", method=RequestMethod.POST)
	public @ResponseBody ISnomedBrowserConcept createTaskConcept(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@RequestBody
			final SnomedBrowserConcept concept,

			final Principal principal,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) {

		final String userId = principal.getName();
		return browserService.create(PathHelper.getPath(projectKey, taskKey), concept, userId, getExtendedLocales(languageSetting));
	}

	@ApiOperation(
			value="Validate a concept",
			notes="Validates a concept in the context of a task branch, without persisting changes.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Void.class),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/validate/concept", method=RequestMethod.POST)
	public @ResponseBody List<SnomedInvalidContent> validateNewConcept(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@RequestBody
			final SnomedBrowserConcept concept) {

		return validationService.validateConcept(PathHelper.getPath(projectKey, taskKey), concept);
	}

	@ApiOperation(
			value="Update a concept",
			notes="Updates a new Concept on a task branch.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Void.class),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/concepts/{conceptId}", method=RequestMethod.PUT)
	public @ResponseBody ISnomedBrowserConcept updateConcept(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@PathVariable
			final String conceptId,

			@RequestBody
			final SnomedBrowserConceptUpdate concept,

			final Principal principal,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) {

		if (!conceptId.equals(concept.getConceptId())) {
			throw new BadRequestException("The concept ID in the request body does not match the ID in the URL.");
		}

		final String userId = principal.getName();
		return browserService.update(PathHelper.getPath(projectKey, taskKey), concept, userId, getExtendedLocales(languageSetting));
	}

	@ApiOperation(
			value = "Retrieve parents of a concept",
			notes = "Returns a list of parent concepts of the specified concept on a branch.",
			response=Void.class)
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK"),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(
			value="/projects/{projectKey}/tasks/{taskKey}/concepts/{conceptId}/parents",
			method = RequestMethod.GET)
	public @ResponseBody List<ISnomedBrowserParentConcept> getConceptParents(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@PathVariable
			final String conceptId,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) {

		final IComponentRef ref = SnomedServiceHelper.createComponentRef(PathHelper.getPath(projectKey, taskKey), conceptId);
		return browserService.getConceptParents(ref, getExtendedLocales(languageSetting));
	}

	@ApiOperation(
			value = "Retrieve children of a concept",
			notes = "Returns a list of child concepts of the specified concept on a branch.",
			response=Void.class)
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK"),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(
			value="/projects/{projectKey}/tasks/{taskKey}/concepts/{conceptId}/children",
			method = RequestMethod.GET)
	public @ResponseBody List<ISnomedBrowserChildConcept> getConceptChildren(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@PathVariable
			final String conceptId,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting,

			@ApiParam(value="Stated or inferred form", allowableValues="stated, inferred")
			@RequestParam(value="form", defaultValue="inferred")
			final String form) {
		if ("stated".equals(form) || "inferred".equals(form)) {
			final IComponentRef ref = SnomedServiceHelper.createComponentRef(PathHelper.getPath(projectKey, taskKey), conceptId);
			return browserService.getConceptChildren(ref, getExtendedLocales(languageSetting), "stated".equals(form));
		}
		throw new BadRequestException("Form parameter should be either 'stated' or 'inferred'");
	}

	@ApiOperation(
			value = "Retrieve descriptions matching a query.",
			notes = "Returns a list of descriptions which have a term matching the specified query string on a version.",
			response=Void.class)
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK"),
			@ApiResponse(code = 404, message = "Code system version or concept not found")
	})
	@RequestMapping(
			value="/projects/{projectKey}/tasks/{taskKey}/descriptions",
			method = RequestMethod.GET)
	public @ResponseBody List<ISnomedBrowserDescriptionResult> searchDescriptions(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@RequestParam
			final String query,

			@ApiParam(value="The starting offset in the list")
			@RequestParam(value="offset", defaultValue="0", required=false)
			final int offset,

			@ApiParam(value="The maximum number of items to return")
			@RequestParam(value="limit", defaultValue="50", required=false)
			final int limit,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) {

		final StorageRef ref = SnomedServiceHelper.createStorageRef(PathHelper.getPath(projectKey, taskKey));
		return browserService.getDescriptions(ref, query, getExtendedLocales(languageSetting), offset, limit);
	}

	@ApiOperation(
			value="Retrieve constants and properties",
			notes="Retrieves referenced constants and related concept properties from a task branch.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Void.class)
	})
	@RequestMapping(value="/projects/{projectKey}/tasks/{taskKey}/constants", method= RequestMethod.GET)
	public @ResponseBody
	Map<String, ISnomedBrowserConstant> getConstants(

			@PathVariable
			final String projectKey,

			@PathVariable
			final String taskKey,

			@ApiParam(value="Language codes and reference sets, in order of preference")
			@RequestHeader(value="Accept-Language", defaultValue="en-US;q=0.8,en-GB;q=0.6", required=false)
			final String languageSetting) {
		final String branchPath = PathHelper.getPath(projectKey, taskKey);
		return browserService.getConstants(branchPath, getExtendedLocales(languageSetting));
	}

}
