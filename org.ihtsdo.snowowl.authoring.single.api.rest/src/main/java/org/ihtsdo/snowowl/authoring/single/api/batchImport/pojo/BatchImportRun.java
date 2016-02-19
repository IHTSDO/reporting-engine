package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportFormat;

public class BatchImportRun {
	
	Map <CSVRecord, BatchImportDetail> allRows = new LinkedHashMap<CSVRecord, BatchImportDetail>();
	Map <String, BatchImportConcept> allValidConcepts = new HashMap<String, BatchImportConcept>();
	BatchImportConcept rootConcept = BatchImportConcept.createRootConcept();
	BatchImportRequest importRequest;
	BatchImportFormat format;
	UUID id;
	
	public static BatchImportRun createRun (UUID batchImportId, BatchImportRequest importRequest) throws BusinessServiceException {
		BatchImportRun run = new BatchImportRun(importRequest);
		BatchImportFormat format = BatchImportFormat.create(importRequest.getFormat());
		run.format = format;
		run.id = batchImportId;
		return run;
	}
	
	private BatchImportRun(BatchImportRequest importRequest){
		this.importRequest = importRequest;
	}
	
	public BatchImportFormat getFormatter() {
		return format;
	}

	public void fail(CSVRecord row, String failureReason) {
		BatchImportDetail failureDetail = new BatchImportDetail(false, failureReason);
		allRows.put(row, failureDetail);
	}
	
	public void succeed(CSVRecord row, String additionalInfo) {
		BatchImportDetail successDetail = new BatchImportDetail(true, additionalInfo);
		allRows.put(row, successDetail);
	}

	public void insertIntoLoadHierarchy(BatchImportConcept thisConcept) throws BusinessServiceException {
		allValidConcepts.put(thisConcept.getSctid(), thisConcept);
		
		//Are we loading the parent of this concept? Add as a child if so
		if (allValidConcepts.containsKey(thisConcept.getParent())) {
			allValidConcepts.get(thisConcept.getParent()).addChild(thisConcept);
		} else {
			//otherwise add as a child of the root concept
			rootConcept.addChild(thisConcept);
		}
		
		//Is this concept a parent of existing known children?  Remove children 
		//from the root concept and add under this concept if so
		for (BatchImportConcept existingConcept : allValidConcepts.values()) {
			if (existingConcept.getParent().equals(thisConcept.getSctid())) {
				rootConcept.removeChild(existingConcept);
				thisConcept.addChild(existingConcept);
			}
		}
		
	}

	public BatchImportConcept getRootConcept() {
		return rootConcept;
	}

	public BatchImportRequest getImportRequest() {
		return importRequest;
	}

	public void abortLoad(List<CSVRecord> rows) {
		//Any rows that haven't been loaded, we'll mark as not loaded
		for (CSVRecord thisRow : rows) {
			if (!allRows.containsKey(thisRow)) {
				fail(thisRow, null);
			}
		}
	}

	public UUID getId() {
		return id;
	}

	public void setId(UUID id) {
		this.id = id;
	}

	public String resultsAsCSV() throws IOException, BusinessServiceException {
		StringBuilder buff = new StringBuilder();
		CSVPrinter out = new CSVPrinter(buff, CSVFormat.EXCEL);
		//First add the header row
		buff.append(BatchImportFormat.ADDITIONAL_RESULTS_HEADER);
		for (String thisHeaderItem : format.getHeaders()) {
			buff.append(",")
				.append(thisHeaderItem);
		}
		//Now loop through all records and output the status, followed by the original line
		for (Map.Entry<CSVRecord, BatchImportDetail> entry : allRows.entrySet()) {
			BatchImportDetail detail = entry.getValue();
			buff.append(detail.isLoaded())
				.append(",\"")
				.append(detail.getFailureReason())
				.append("\",");
			out.printRecord(entry.getKey());
		}
		return buff.toString();
	}
}
