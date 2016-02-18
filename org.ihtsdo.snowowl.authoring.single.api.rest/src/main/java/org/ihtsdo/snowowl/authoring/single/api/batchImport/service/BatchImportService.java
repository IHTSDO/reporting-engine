package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.io.IOException;
import java.io.StringReader;
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
import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportFormat.FIELD;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.ihtsdo.snowowl.authoring.single.api.service.UiStateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.b2international.commons.VerhoeffCheck;
import com.b2international.commons.http.AcceptHeader;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserConcept;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserDescription;
import com.b2international.snowowl.snomed.api.domain.browser.SnomedBrowserDescriptionType;
import com.b2international.snowowl.snomed.api.impl.SnomedBrowserService;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserConcept;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserDescription;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationship;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationshipType;
import com.b2international.snowowl.snomed.core.domain.Acceptability;
import com.b2international.snowowl.snomed.core.domain.CharacteristicType;

@Service
public class BatchImportService {
	
	@Autowired
	private TaskService taskService;
	
	@Autowired
	SnomedBrowserService browserService;
	
	@Autowired
	UiStateService uiStateService;
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private static final int ROW_UNKNOWN = -1;
	private static final String NEW_LINE = "\n";
	private static final String BULLET = "  *";
	private static final String SCTID_ISA = "1";
	
	private static final String EDIT_PANEL = "edit-panel";
	private static final String SAVE_LIST = "save-list";	
	
	private List<ExtendedLocale> defaultLocales;
	private static final String defaultLocaleStr = "en-US;q=0.8,en-GB;q=0.6";
	public BatchImportService () {
		try {
			defaultLocales = AcceptHeader.parseExtendedLocales(new StringReader(defaultLocaleStr));
		} catch (IOException e) {
			throw new BadRequestException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new BadRequestException(e.getMessage());
		}
	}
	
	public static final Map<String, Acceptability> DEFAULT_ACCEPTABILIY = new HashMap<String, Acceptability>();
	
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
	
	private void loadConceptsOntoTasks(BatchImportRun run) throws JiraException, ServiceException, BusinessServiceException {
		List<List<BatchImportConcept>> batches = collectIntoBatches(run);
		for (List<BatchImportConcept> thisBatch : batches) {
			AuthoringTask task = createTask(run, thisBatch);
			String conceptsLoaded = loadConcepts(run, task, thisBatch);
			primeUserInterface(task, run, conceptsLoaded);
		}
	}
	

	private void primeUserInterface(AuthoringTask task, BatchImportRun run, String conceptsLoaded) {
		try {
			String json = new StringBuilder("[").append(conceptsLoaded).append("]").toString();
			String user = run.getImportRequest().getCreateForAuthor();
			uiStateService.persistPanelState(user, EDIT_PANEL, json);
			uiStateService.persistPanelState(user, SAVE_LIST, json);
		} catch (IOException e) {
			logger.warn("Failed to prime user Interface for task " + task.getKey(), e );
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
			List<BatchImportConcept> thisBatch) throws JiraException, ServiceException, BusinessServiceException {
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
			List<BatchImportConcept> thisBatch) throws BusinessServiceException {
		StringBuilder str = new StringBuilder();
		for (BatchImportConcept thisConcept : thisBatch) {
			str.append(thisConcept.getSctid()).append(":").append(NEW_LINE);
			List<String> notes = run.getFormatter().getAllNotes(thisConcept);
			for (String thisNote: notes) {
				str.append(BULLET)
					.append(thisNote)
					.append(NEW_LINE);
			}
		}
		return str.toString();
	}



	private String loadConcepts(BatchImportRun run, AuthoringTask task,
			List<BatchImportConcept> thisBatch) throws BusinessServiceException {
		String branchPath = "MAIN/" + run.getImportRequest().getProjectKey() + "/" + task.getKey();
		StringBuilder conceptsLoaded = new StringBuilder();
		boolean isFirst = true;
		for (BatchImportConcept thisConcept : thisBatch) {
			try{
				ISnomedBrowserConcept newConcept = createBrowserConcept(thisConcept, run.getFormatter());
				browserService.create(branchPath, newConcept, run.getImportRequest().getCreateForAuthor(), defaultLocales);
				if (isFirst){
					isFirst = false;
				} else {
					conceptsLoaded.append(",");
				}
				conceptsLoaded.append("\"").append(thisConcept.getSctid()).append("\"");
			} catch (Exception e) {
				run.fail(thisConcept.getRow(), e.getMessage());
			}
		}
		return conceptsLoaded.toString();
	}
	
	private ISnomedBrowserConcept createBrowserConcept(
			BatchImportConcept thisConcept, BatchImportFormat formatter) throws BusinessServiceException {
		SnomedBrowserConcept newConcept = new SnomedBrowserConcept();
		newConcept.setConceptId(thisConcept.getSctid());
		newConcept.setActive(true);
		
		//Set the Parent
		SnomedBrowserRelationship isA = new SnomedBrowserRelationship();
		isA.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		isA.setSourceId(thisConcept.getSctid());
		isA.setType(new SnomedBrowserRelationshipType(SCTID_ISA));
		
		//Add Descriptions FSN, then Preferred Term
		List<ISnomedBrowserDescription> descriptions = new ArrayList<ISnomedBrowserDescription>();
		String prefTerm = thisConcept.get(formatter.getIndex(FIELD.FSN_ROOT));
		String fsnTerm = prefTerm + " (" + thisConcept.get(formatter.getIndex(FIELD.SEMANTIC_TAG)) +")";
		SnomedBrowserDescription fsn = new SnomedBrowserDescription();
		fsn.setTerm(fsnTerm);
		fsn.setType(SnomedBrowserDescriptionType.FSN);
		fsn.setAcceptabilityMap(DEFAULT_ACCEPTABILIY);
		descriptions.add(fsn);
		
		SnomedBrowserDescription pref = new SnomedBrowserDescription();
		pref.setTerm(prefTerm);
		pref.setType(SnomedBrowserDescriptionType.SYNONYM);
		pref.setAcceptabilityMap(DEFAULT_ACCEPTABILIY);
		descriptions.add(pref);
		
		newConcept.setDescriptions(descriptions);
		return newConcept;
	}



	public BatchImportStatus getImportStatus(UUID batchImportId) {
		return currentImports.get(batchImportId);
	}

	public List<CSVRecord> getImportResults(UUID batchImportId) {
		// TODO Auto-generated method stub
		return null;
	}

}
