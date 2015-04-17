package org.ihtsdo.snowowl.authoring.api.rest;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.wordnik.swagger.annotations.*;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api("Authoring Service")
@RestController
@RequestMapping(value = "/authoring/{version}", produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class AuthoringController extends AbstractSnomedRestService {

	@Autowired
	private AuthoringService service;

	@ApiOperation(value="Validates a batch of authoring content against a logical model.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = List.class),
			@ApiResponse(code = 404, message = "Logical model not found")
	})
	@RequestMapping(value="/validate/{conceptId}", method= RequestMethod.GET)
	public List<String> getConcepts(
			@ApiParam(value = "The code system version")
			@PathVariable(value = "version")
			final String version,

			@ApiParam(value = "The concept identifier")
			@PathVariable(value = "conceptId")
			final String conceptId) {

		final IComponentRef ref = createComponentRef(version, null, conceptId);
		return service.getDescendantIds(ref);
	}

}
