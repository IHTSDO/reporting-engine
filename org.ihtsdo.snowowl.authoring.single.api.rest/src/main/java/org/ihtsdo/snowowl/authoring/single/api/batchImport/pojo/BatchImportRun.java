package org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportFormat;

public class BatchImportRun {
	
	Map <CSVRecord, BatchImportDetail> allRows = new LinkedHashMap<CSVRecord, BatchImportDetail>();
	Map <String, BatchImportConcept> allValidConcepts = new HashMap<String, BatchImportConcept>();
	BatchImportConcept rootConcept = BatchImportConcept.createRootConcept();
	BatchImportRequest importRequest;
	BatchImportFormat format;
	
	public static BatchImportRun createRun (BatchImportRequest importRequest) throws BusinessServiceException {
		BatchImportRun run = new BatchImportRun(importRequest);
		BatchImportFormat format = BatchImportFormat.create(importRequest.getFormat());
		run.format = format;
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
}
