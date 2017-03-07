package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

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
import org.ihtsdo.snowowl.authoring.batchimport.api.service.BatchImportFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchImportRun {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Map <CSVRecord, BatchImportDetail> allRows = new LinkedHashMap<>();
	private Map <String, BatchImportConcept> allValidConcepts = new HashMap<>();
	private BatchImportConcept rootConcept = BatchImportConcept.createRootConcept();
	private BatchImportRequest importRequest;
	private BatchImportFormat format;
	private UUID id;
	
	public static BatchImportRun createRun (UUID batchImportId, BatchImportRequest importRequest) throws BusinessServiceException {
		BatchImportRun run = new BatchImportRun(importRequest);
		BatchImportFormat format = importRequest.getFormat();
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
		BatchImportDetail failureDetail = new BatchImportDetail(false, failureReason, null);
		allRows.put(row, failureDetail);
	}
	
	public void succeed(CSVRecord row, String additionalInfo, String sctIdCreated) {
		BatchImportDetail successDetail = new BatchImportDetail(true, additionalInfo, sctIdCreated);
		allRows.put(row, successDetail);
	}

	public void insertIntoLoadHierarchy(BatchImportConcept thisConcept) throws BusinessServiceException {
		allValidConcepts.put(thisConcept.getSctid(), thisConcept);
		
		//Are we loading the parent of this concept? Add as a child if so
		if (allValidConcepts.containsKey(thisConcept.getParent(0))) {
			allValidConcepts.get(thisConcept.getParent(0)).addChild(thisConcept);
		} else {
			//otherwise add as a child of the root concept
			rootConcept.addChild(thisConcept);
		}
		
		//Is this concept a parent of existing known children?  Remove children 
		//from the root concept and add under this concept if so
		for (BatchImportConcept existingConcept : allValidConcepts.values()) {
			if (existingConcept.getParent(0).equals(thisConcept.getSctid())) {
				rootConcept.removeChild(existingConcept);
				thisConcept.addChild(existingConcept);
			}
		}
		
	}

	public BatchImportConcept getRootConcept() {
		return rootConcept;
	}
	
	public BatchImportConcept getConcept (String sctId) {
		return allValidConcepts.get(sctId);
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
		CSVPrinter out = null;
		try {
			out = new CSVPrinter(buff, CSVFormat.EXCEL);
			//First add the header row
			buff.append(BatchImportFormat.ADDITIONAL_RESULTS_HEADER);
			for (String thisHeaderItem : format.getHeaders()) {
				buff.append(",")
					.append(thisHeaderItem);
			}
			buff.append(BatchImportFormat.NEW_LINE);
			//Now loop through all records and output the status, followed by the original line
			for (Map.Entry<CSVRecord, BatchImportDetail> entry : allRows.entrySet()) {
				CSVRecord thisRow = entry.getKey();
				BatchImportDetail detail = entry.getValue();
				buff.append(thisRow.getRecordNumber())
					.append(",")
					.append(detail.isLoaded())
					.append(",\"")
					.append(detail.getFailureReason())
					.append("\",")
					.append(detail.getSctidCreated())
					.append(",");
				out.printRecord(thisRow);
			}
		} catch (Exception e) {
			logger.error("Exception while outputting Batch Import results as CSV",e);
		} finally {
			if (out != null)
				out.close();
		}
		
		return buff.toString();
	}
}
