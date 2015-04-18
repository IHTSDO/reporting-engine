package org.ihtsdo.snowowl.authoring.api.rest;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.wordnik.swagger.annotations.*;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.api.Constants;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContent;
import org.ihtsdo.snowowl.authoring.api.model.AuthoringContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.services.AuthoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Api("Authoring Service")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class AuthoringController extends AbstractSnomedRestService {

	@Autowired
	private AuthoringService service;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@ApiOperation(value="Create / update a logical model.", notes="")
	@ApiResponses({
			@ApiResponse(code = 201, message = "CREATED")
	})
	@RequestMapping(value="/models/logical", method= RequestMethod.POST)
	public ResponseEntity<LogicalModel> saveLogicalModel(@RequestBody final LogicalModel logicalModel) throws IOException {
		logger.info("Create/update logicalModel {}", logicalModel);
		service.saveLogicalModel(logicalModel);
		return new ResponseEntity<>(logicalModel, HttpStatus.CREATED);
	}

	@ApiOperation(value="List logical model names.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = LogicalModel.class)
	})
	@RequestMapping(value="/models/logical", method= RequestMethod.GET)
	public List<String> listLogicalModelNames() throws IOException {
		return service.listLogicalModelNames();
	}

	@ApiOperation(value="Retrieve a logical model.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = LogicalModel.class),
			@ApiResponse(code = 404, message = "Logical model not found")

	})
	@RequestMapping(value="/models/logical/{logicalModelName}", method= RequestMethod.GET)
	public LogicalModel loadLogicalModel(@PathVariable final String logicalModelName) throws IOException {
		return service.loadLogicalModel(logicalModelName);
	}

	@ApiOperation(value="Validate content.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "Content is valid", response = AuthoringContentValidationResult.class),
			@ApiResponse(code = 406, message = "Content is not valid", response = AuthoringContentValidationResult.class),
			@ApiResponse(code = 404, message = "Logical model not found")
	})
	@RequestMapping(value="/models/logical/{logicalModelName}/valid-content", method= RequestMethod.POST)
	public ResponseEntity<List<AuthoringContentValidationResult>> validateContent(@PathVariable final String logicalModelName, @RequestBody List<AuthoringContent> content) throws IOException {
		List<AuthoringContentValidationResult> results = service.validateContent(logicalModelName, content);
		return new ResponseEntity<>(results, isAnyErrors(results) ? HttpStatus.NOT_ACCEPTABLE : HttpStatus.OK);
	}

	private boolean isAnyErrors(List<AuthoringContentValidationResult> results) {
		for (AuthoringContentValidationResult result : results) {
			if (result.isAnyErrors()) {
				return true;
			}
		}
		return false;
	}

	@ApiOperation(value="Retrieve descendant concept ids.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Set.class),
			@ApiResponse(code = 404, message = "Concept not found")
	})
	@RequestMapping(value="/descendants/{conceptId}", method= RequestMethod.GET)
	public Set<String> getConcepts(
			@ApiParam(value = "The concept identifier")
			@PathVariable(value = "conceptId")
			final String conceptId) {

		final IComponentRef ref = createComponentRef(Constants.MAIN, null, conceptId);
		return service.getDescendantIds(ref);
	}

}
