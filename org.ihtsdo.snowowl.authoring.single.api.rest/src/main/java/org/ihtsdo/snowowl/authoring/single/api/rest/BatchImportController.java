package org.ihtsdo.snowowl.authoring.single.api.rest;

import static java.util.UUID.randomUUID;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.HttpServletResponse;

import com.wordnik.swagger.annotations.Api;
import com.wordnik.swagger.annotations.ApiOperation;
import com.wordnik.swagger.annotations.ApiParam;
import com.wordnik.swagger.annotations.ApiResponse;
import com.wordnik.swagger.annotations.ApiResponses;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.AbstractRestService;
import org.ihtsdo.snowowl.api.rest.common.AbstractSnomedRestService;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportStatus;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@Api("Authoring Projects")
@RestController
@RequestMapping(produces={AbstractRestService.V1_MEDIA_TYPE, MediaType.APPLICATION_JSON_VALUE})
public class BatchImportController extends AbstractSnomedRestService {

	@Autowired
	private BatchImportService batchImportService;

	@ApiOperation(value="Import 3rd Party Concept file eg SIRS")
	@ApiResponses({
		@ApiResponse(code = 201, message = "Created"),
		@ApiResponse(code = 404, message = "Task not found"),
	})
	@RequestMapping(value="/projects/{projectKey}/batchImport", method= RequestMethod.POST)
	public void startBatchImport(@PathVariable final String projectKey,
			@ApiParam(value="3rd Party import csv file")
			@RequestPart("file") 
			final MultipartFile file,
			HttpServletResponse response ) throws BusinessServiceException {
		
		try {
			final UUID batchImportId = randomUUID();
			
			Reader in = new InputStreamReader(file.getInputStream());
			CSVParser parser = new CSVParser(in, CSVFormat.EXCEL);
			List<CSVRecord> rows = parser.getRecords();
			parser.close();
			
			batchImportService.startImport(projectKey, rows, ControllerHelper.getUsername());
			response.setHeader("Location", "/" + batchImportId.toString());
		} catch (IOException e) {
			throw new BusinessServiceException ("Unable to import batch file",e);
		}
	}
	
	@ApiOperation(
			value="Retrieve import run details", 
			notes="Returns the specified batch import run's status.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Batch import not found"),
	})
	@RequestMapping(value="/projects/{projectKey}/batchImport/{batchImportId}", method=RequestMethod.GET)
	public BatchImportStatus getBatchImportStatus(
			@ApiParam(value="The batch import identifier")
			@PathVariable(value="batchImportId") 
			final UUID batchImportId,
			HttpServletResponse response) {

		return batchImportService.getImportStatus(batchImportId);
	}
	
	@ApiOperation(
			value="Retrieve import run details", 
			notes="Returns the specified import run's configuration and status.")
	@ApiResponses({
		@ApiResponse(code = 200, message = "OK"),
		@ApiResponse(code = 404, message = "Batch import not found"),
	})
	@RequestMapping(value="/projects/{projectKey}/batchImport/{batchImportId}/results", method=RequestMethod.GET)
	public void getBatchImportResults(
			@ApiParam(value="The import identifier")
			@PathVariable(value="importId") 
			final UUID batchImportId,
			HttpServletResponse response) throws BusinessServiceException {

		String csvFileName = "BatchImportResults.csv";
		response.setContentType("text/csv");

		String headerKey = "Content-Disposition";
		String headerValue = String.format("attachment; filename=\"%s\"", csvFileName);
		response.setHeader(headerKey, headerValue);
		try {
			Appendable writer = new OutputStreamWriter(response.getOutputStream(), StandardCharsets.UTF_8);
			CSVPrinter csvPrinter = new CSVPrinter(writer,CSVFormat.DEFAULT);
			List<CSVRecord> results =  batchImportService.getImportResults(batchImportId);
			csvPrinter.printRecords(results);
			csvPrinter.close();
		} catch (IOException e) {
			throw new BusinessServiceException ("Unable to recover batch import results",e);
		}
	}

}
