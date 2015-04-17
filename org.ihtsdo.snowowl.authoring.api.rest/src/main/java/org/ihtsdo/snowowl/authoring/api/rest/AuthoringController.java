package org.ihtsdo.snowowl.authoring.api.rest;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.wordnik.swagger.annotations.*;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.services.AuthoringService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@Api("Authoring Service")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class AuthoringController extends AbstractSnomedRestService {

	public static final String MAIN = "MAIN";

	@Autowired
	private AuthoringService service;

	private Logger logger = LoggerFactory.getLogger(getClass());

	@ApiOperation(value="Create / update a logical model.", notes="")
	@ApiResponses({
			@ApiResponse(code = 201, message = "CREATED")
	})
	@RequestMapping(value="/models/logical/{name}", method= RequestMethod.POST)
	public void saveLogicalModel(@PathVariable final String name,
			@RequestBody final LogicalModel logicalModel) throws IOException {

		logger.info("Create/update logicalModel {}", logicalModel);
		service.saveLogicalModel(name, logicalModel);
	}

	@ApiOperation(value="Retrieve a logical model.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = LogicalModel.class),
			@ApiResponse(code = 404, message = "Logical model not found")

	})
	@RequestMapping(value="/models/logical/{name}", method= RequestMethod.GET)
	public LogicalModel loadLogicalModel(@PathVariable final String name) throws IOException {
		return service.loadLogicalModel(name);
	}

	@ApiOperation(value="List descendant concept ids.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = List.class),
			@ApiResponse(code = 404, message = "Concept not found")
	})
	@RequestMapping(value="/descendants/{conceptId}", method= RequestMethod.GET)
	public List<String> getConcepts(
			@ApiParam(value = "The concept identifier")
			@PathVariable(value = "conceptId")
			final String conceptId) {

		final IComponentRef ref = createComponentRef(MAIN, null, conceptId);
		return service.getDescendantIds(ref);
	}

}
