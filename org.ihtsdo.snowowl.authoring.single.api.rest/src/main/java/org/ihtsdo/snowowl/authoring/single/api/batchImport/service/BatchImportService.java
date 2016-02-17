package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.rcarz.jiraclient.JiraException;

import org.apache.commons.csv.CSVRecord;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportConcept;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportRequest;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportRun;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportStatus;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.b2international.commons.VerhoeffCheck;

@Service
public class BatchImportService {
	
	@Autowired
	private TaskService taskService;
	
	private static final int ROW_UNKNOWN = -1;
	
	Map<UUID, BatchImportStatus> currentImports = new HashMap<UUID, BatchImportStatus>();
	
	public void startImport(BatchImportRequest importRequest, List<CSVRecord> rows, String currentUser) throws BusinessServiceException, JiraException, ServiceException {
		
		BatchImportRun run = BatchImportRun.createRun(importRequest);
		
		prepareConcepts(run, rows);
		
		if (validateLoadHierarchy(run)) {
			loadConceptsOntoTasks(run);
		} else {
			run.abortLoad(rows);
		}
	}



	private void prepareConcepts(BatchImportRun run, List<CSVRecord> rows) throws BusinessServiceException {
		// Loop through concepts and form them into a hierarchy to be loaded, if valid
		for (CSVRecord thisRow : rows) {
			BatchImportConcept thisConcept = run.getFormatter().createConcept(thisRow);
			if (validate(run, thisConcept)) {
				run.insertIntoLoadHierarchy(thisConcept);
			}
		}
	}

	private boolean validateLoadHierarchy(BatchImportRun run) {
		//Parents and children have to exist in the same task, so 
		//check that we're not going to exceed "concepts per task"
		//in doing so.
		boolean failureDetected = false;
		for (BatchImportConcept thisConcept : run.getRootConcept().getChildren()) {
			if (thisConcept.childrenCount() > run.getImportRequest().getConceptsPerTask()) {
				run.fail(thisConcept.getRow(), "Concept has more children than specified for a single task");
				failureDetected = true;
			}
		}
		return failureDetected;
	}

	
	private boolean validate (BatchImportRun run, BatchImportConcept concept) {
		if (VerhoeffCheck.validateLastChecksumDigit(concept.getSctid())) {
			run.fail(concept.getRow(), concept.getParent() + " is not a valid sctid.");
			return false;
		}
		
		if (VerhoeffCheck.validateLastChecksumDigit(concept.getParent())) {
			run.fail(concept.getRow(), concept.getParent() + " is not a valid parent identifier.");
			return false;
		}
		
		return true;
	}
	
	private void loadConceptsOntoTasks(BatchImportRun run) throws JiraException, ServiceException {
		List<List<BatchImportConcept>> batches = collectIntoBatches(run);
		for (List<BatchImportConcept> thisBatch : batches) {
			AuthoringTask task = createTask(run, thisBatch);
			loadConcepts(run, task, thisBatch);
		}
	}
	

	private List<List<BatchImportConcept>> collectIntoBatches(BatchImportRun run) {
		List<List<BatchImportConcept>> batches = new ArrayList<List<BatchImportConcept>>();
	
		//Loop through all the children of root, starting a new batch every "concepts per task"
		List<BatchImportConcept> thisBatch = null;
		for (BatchImportConcept thisChild : run.getRootConcept().getChildren()) {
			if (thisBatch == null || thisBatch.size() > run.getImportRequest().getConceptsPerTask() + 1) {
				thisBatch = new ArrayList<BatchImportConcept>();
				batches.add(thisBatch);
			}
			//We can be sure that all descendants will not exceed our batch limit, having already validated
			thisBatch.add(thisChild);
			thisChild.addDescendants(thisBatch);
		}
		return batches;
	}

	private AuthoringTask createTask(BatchImportRun run,
			List<BatchImportConcept> thisBatch) throws JiraException, ServiceException {
		AuthoringTaskCreateRequest taskCreateRequest = new AuthoringTask();
		String allNotes = getAllNotes(run, thisBatch);
		taskCreateRequest.setDescription(allNotes);
		taskCreateRequest.setSummary(getRowRange(thisBatch));
		return taskService.createTask(run.getImportRequest().getProjectKey(), 
				run.getImportRequest().getCreateForAuthor(),
				taskCreateRequest);
	}

	private String getRowRange(List<BatchImportConcept> thisBatch) {
		StringBuilder str = new StringBuilder ("Rows ");
		long minRow = ROW_UNKNOWN;
		long maxRow = ROW_UNKNOWN;
		
		for (BatchImportConcept thisConcept : thisBatch) {
			long thisRowNum = thisConcept.getRow().getRecordNumber();
			if (minRow == ROW_UNKNOWN || thisRowNum < minRow) {
				minRow = thisRowNum;
			}
			
			if (maxRow == ROW_UNKNOWN || thisRowNum > maxRow) {
				maxRow = thisRowNum;
			}
		}
		str.append(minRow).append(":").append(maxRow);
		return str.toString();
	}



	private String getAllNotes(BatchImportRun run,
			List<BatchImportConcept> thisBatch) {
		// TODO Auto-generated method stub
		return null;
	}



	private void loadConcepts(BatchImportRun run, AuthoringTask task,
			List<BatchImportConcept> thisBatch) {
		// TODO Auto-generated method stub
		
	}
	
	public BatchImportStatus getImportStatus(UUID batchImportId) {
		return currentImports.get(batchImportId);
	}

	public List<CSVRecord> getImportResults(UUID batchImportId) {
		// TODO Auto-generated method stub
		return null;
	}

}
