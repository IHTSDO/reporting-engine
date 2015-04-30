package org.ihtsdo.snowowl.authoring.api.rest;

import com.b2international.snowowl.api.domain.IComponentRef;
import com.wordnik.swagger.annotations.*;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.authoring.api.Constants;
import org.ihtsdo.snowowl.authoring.api.model.work.ContentValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingConcept;
import org.ihtsdo.snowowl.authoring.api.model.work.ConceptValidationResult;
import org.ihtsdo.snowowl.authoring.api.model.Template;
import org.ihtsdo.snowowl.authoring.api.model.lexical.LexicalModel;
import org.ihtsdo.snowowl.authoring.api.model.logical.LogicalModel;
import org.ihtsdo.snowowl.authoring.api.model.work.WorkingContent;
import org.ihtsdo.snowowl.authoring.api.services.AuthoringService;
import org.ihtsdo.snowowl.authoring.api.services.LexicalModelService;
import org.ihtsdo.snowowl.authoring.api.services.LogicalModelService;
import org.ihtsdo.snowowl.authoring.api.services.TemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.List;
import java.util.Set;

@Api("Authoring Service")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class AuthoringController extends AbstractSnomedRestService {

	@Autowired
	private AuthoringService authoringService;

	@Autowired
	private LogicalModelService logicalModelService;

	@Autowired
	private LexicalModelService lexicalModelService;

	@Autowired
	private TemplateService templateService;

	private Logger logger = LoggerFactory.getLogger(getClass());


	// Logical Model Endpoints

	@ApiOperation(value="Create / update a logical model with validation.", notes="")
	@ApiResponses({
			@ApiResponse(code = 201, message = "CREATED", response = LogicalModel.class),
			@ApiResponse(code = 406, message = "Logical model is not valid", response = List.class),
	})
	@RequestMapping(value="/models/logical", method= RequestMethod.POST)
	public ResponseEntity saveLogicalModel(@RequestBody final LogicalModel logicalModel) throws IOException {
		logger.info("Create/update logicalModel {}", logicalModel);
		List<String> errorMessages = logicalModelService.validateLogicalModel(logicalModel);
		if (errorMessages.isEmpty()) {
			logicalModelService.saveLogicalModel(logicalModel);
			return new ResponseEntity<>(logicalModel, HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(errorMessages, HttpStatus.NOT_ACCEPTABLE);
		}
	}

	@ApiOperation(value="List logical model names.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/models/logical", method= RequestMethod.GET)
	public List<String> listLogicalModelNames() throws IOException {
		return logicalModelService.listLogicalModelNames();
	}

	@ApiOperation(value="Retrieve a logical model.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = LogicalModel.class),
			@ApiResponse(code = 404, message = "Logical model not found")

	})
	@RequestMapping(value="/models/logical/{logicalModelName}", method= RequestMethod.GET)
	public LogicalModel loadLogicalModel(@PathVariable final String logicalModelName) throws IOException {
		return logicalModelService.loadLogicalModel(logicalModelName);
	}


	// Lexical Model Endpoints

	@ApiOperation(value="Create / update a lexical model with validation.", notes="")
	@ApiResponses({
			@ApiResponse(code = 201, message = "CREATED", response = LexicalModel.class),
			@ApiResponse(code = 406, message = "Lexical model is not valid", response = List.class),
	})
	@RequestMapping(value="/models/lexical", method= RequestMethod.POST)
	public ResponseEntity saveLexicalModel(@RequestBody final LexicalModel lexicalModel) throws IOException {
		logger.info("Create/update lexicalModel {}", lexicalModel);
		List<String> errorMessages = lexicalModelService.validateModel(lexicalModel);
		if (errorMessages.isEmpty()) {
			lexicalModelService.saveModel(lexicalModel);
			return new ResponseEntity<>(lexicalModel, HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(errorMessages, HttpStatus.NOT_ACCEPTABLE);
		}
	}

	@ApiOperation(value="List lexical model names.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/models/lexical", method= RequestMethod.GET)
	public List<String> listLexicalModelNames() throws IOException {
		return lexicalModelService.listModelNames();
	}

	@ApiOperation(value="Retrieve a lexical model.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = LexicalModel.class),
			@ApiResponse(code = 404, message = "Lexical model not found")

	})
	@RequestMapping(value="/models/lexical/{lexicalModelName}", method= RequestMethod.GET)
	public LexicalModel loadLexicalModel(@PathVariable final String lexicalModelName) throws IOException {
		return lexicalModelService.loadModel(lexicalModelName);
	}


	// Template Endpoints

	@ApiOperation(value="Create / update template.")
	@ApiResponses({
			@ApiResponse(code = 201, message = "CREATED", response = Template.class),
			@ApiResponse(code = 406, message = "Template is not valid", response = String.class)
	})
	@RequestMapping(value="/templates", method= RequestMethod.POST)
	public ResponseEntity validateContent(@RequestBody Template template) throws IOException {
		String errorMessage = templateService.saveTemplate(template);
		if (errorMessage == null || errorMessage.isEmpty()) {
			return new ResponseEntity<>(template, HttpStatus.CREATED);
		} else {
			return new ResponseEntity<>(errorMessage, HttpStatus.NOT_ACCEPTABLE);
		}
	}

	@ApiOperation(value="List template names.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK")
	})
	@RequestMapping(value="/templates", method= RequestMethod.GET)
	public List<String> listTemplateNames() throws IOException {
		return templateService.listModelNames();
	}

	@ApiOperation(value="Retrieve a template.", notes="")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Template.class),
			@ApiResponse(code = 404, message = "Template not found")

	})
	@RequestMapping(value="/templates/{templateName}", method= RequestMethod.GET)
	public Template loadTemplateModel(@PathVariable final String templateName) throws IOException {
		return templateService.loadTemplate(templateName);
	}


	// Content work Endpoints

	@ApiOperation(value="Create content work.")
	@ApiResponses({
			@ApiResponse(code = 201, message = "CREATED", response = WorkingConcept.class),
			@ApiResponse(code = 404, message = "Template not found")
	})
	@RequestMapping(value="/templates/{templateName}/work", method= RequestMethod.POST)
	public ResponseEntity<WorkingContent> saveWork(@PathVariable final String templateName,
			@RequestBody WorkingContent content, UriComponentsBuilder uriBuilder) throws IOException {
		authoringService.persistWork(templateName, content);
		HttpHeaders headers = new HttpHeaders();
		headers.setLocation(uriBuilder.path("/templates/{templateName}/work/{workId}").buildAndExpand(templateName, content.getName()).toUri());
		return new ResponseEntity<>(content, headers, HttpStatus.CREATED);
	}

	@ApiOperation(value="Update content work.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = WorkingConcept.class),
			@ApiResponse(code = 404, message = "Template not found")
	})
	@RequestMapping(value="/templates/{templateName}/work/{workId}", method= RequestMethod.PUT)
	public WorkingContent updateWork(@PathVariable final String templateName,
			@PathVariable final String workId,
			@RequestBody WorkingContent content) throws IOException {
		content.setName(workId);
		authoringService.persistWork(templateName, content);
		return content;
	}

	@ApiOperation(value="Retrieve working content.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = Template.class),
			@ApiResponse(code = 404, message = "Content work not found")

	})
	@RequestMapping(value="/templates/{templateName}/work/{workId}", method= RequestMethod.GET)
	public WorkingContent loadWork(@PathVariable final String templateName, @PathVariable final String workId) throws IOException {
		return authoringService.loadWork(templateName, workId);
	}

	@ApiOperation(value="Validate working content.")
	@ApiResponses({
			@ApiResponse(code = 200, message = "OK", response = WorkingConcept.class),
			@ApiResponse(code = 404, message = "Template or working content not found")
	})
	@RequestMapping(value="/templates/{templateName}/work/{workId}/validation", method= RequestMethod.GET)
	public ContentValidationResult validateContent(@PathVariable final String templateName,
			@PathVariable final String workId) throws IOException {

		return authoringService.validateWorkingContent(templateName, workId);
	}


	// Not currently used

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
		return authoringService.getDescendantIds(ref);
	}

	private boolean isAnyErrors(ContentValidationResult results) {
		for (ConceptValidationResult conceptResult : results.getConceptResults()) {
			if (conceptResult.isAnyErrors()) {
				return true;
			}
		}
		return false;
	}
}
