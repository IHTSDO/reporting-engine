package org.ihtsdo.snowowl.authoring.single.api.batchImport.service;

import java.io.File;
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
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportState;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.pojo.BatchImportStatus;
import org.ihtsdo.snowowl.authoring.single.api.batchImport.service.BatchImportFormat.FIELD;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;
import org.ihtsdo.snowowl.authoring.single.api.service.ServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.TaskService;
import org.ihtsdo.snowowl.authoring.single.api.service.UiStateService;
import org.ihtsdo.snowowl.authoring.single.api.service.dao.ArbitraryTempFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.b2international.commons.VerhoeffCheck;
import com.b2international.commons.http.AcceptHeader;
import com.b2international.commons.http.ExtendedLocale;
import com.b2international.snowowl.core.exceptions.ApiError;
import com.b2international.snowowl.core.exceptions.BadRequestException;
import com.b2international.snowowl.core.exceptions.ValidationException;
import com.b2international.snowowl.eventbus.IEventBus;
import com.b2international.snowowl.snomed.SnomedConstants;
import com.b2international.snowowl.snomed.SnomedConstants.Concepts;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserConcept;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserDescription;
import com.b2international.snowowl.snomed.api.domain.browser.ISnomedBrowserRelationship;
import com.b2international.snowowl.snomed.api.domain.browser.SnomedBrowserDescriptionType;
import com.b2international.snowowl.snomed.api.impl.SnomedBrowserService;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserConcept;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserDescription;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationship;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationshipTarget;
import com.b2international.snowowl.snomed.api.impl.domain.browser.SnomedBrowserRelationshipType;
import com.b2international.snowowl.snomed.api.validation.ISnomedBrowserValidationService;
import com.b2international.snowowl.snomed.api.validation.ISnomedInvalidContent;
import com.b2international.snowowl.snomed.core.domain.Acceptability;
import com.b2international.snowowl.snomed.core.domain.CaseSignificance;
import com.b2international.snowowl.snomed.core.domain.CharacteristicType;
import com.b2international.snowowl.snomed.core.domain.DefinitionStatus;

@Service
public class BatchImportService {
	
	@Autowired
	private TaskService taskService;
	
	@Autowired
	SnomedBrowserService browserService;
	
	@Autowired
	UiStateService uiStateService;
	
	@Autowired
	private IEventBus eventBus;
	
	@Autowired
	private ISnomedBrowserValidationService validationService;
	
	ArbitraryTempFileService fileService = new ArbitraryTempFileService("batch_import");
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	private static final int ROW_UNKNOWN = -1;
	private static final String BULLET = "* ";
	private static final String SCTID_ISA = Concepts.IS_A;
	private static final String SCTID_EN_GB = Concepts.REFSET_LANGUAGE_TYPE_UK;
	private static final String SCTID_EN_US = Concepts.REFSET_LANGUAGE_TYPE_US;
	private static final String JIRA_HEADING5 = "h5. ";
	private static final String VALIDATION_ERROR = "ERROR";
	private static final int MIN_VIABLE_COLUMNS = 9;
	
	private static final String EDIT_PANEL = "edit-panel";
	private static final String SAVE_LIST = "saved-list";	
	
	private List<ExtendedLocale> defaultLocales;
	private static final String defaultLocaleStr = "en-US;q=0.8,en-GB;q=0.6";
	public static final Map<String, Acceptability> DEFAULT_ACCEPTABILIY = new HashMap<String, Acceptability>();
	static {
		DEFAULT_ACCEPTABILIY.put(SCTID_EN_GB, Acceptability.PREFERRED);
		DEFAULT_ACCEPTABILIY.put(SCTID_EN_US, Acceptability.PREFERRED);
	}
	
	Map<UUID, BatchImportStatus> currentImports = new HashMap<UUID, BatchImportStatus>();
	
	public BatchImportService () {
		try {
			defaultLocales = AcceptHeader.parseExtendedLocales(new StringReader(defaultLocaleStr));
		} catch (IOException e) {
			throw new BadRequestException(e.getMessage());
		} catch (IllegalArgumentException e) {
			throw new BadRequestException(e.getMessage());
		}
	}
	
	public void startImport(UUID batchImportId, BatchImportRequest importRequest, List<CSVRecord> rows, String currentUser) throws BusinessServiceException, JiraException, ServiceException {
		
		BatchImportRun run = BatchImportRun.createRun(batchImportId, importRequest);
		currentImports.put(batchImportId, new BatchImportStatus(BatchImportState.RUNNING));
		boolean completed = false;
		try { 
			prepareConcepts(run, rows);
			int rowsToProcess = run.getRootConcept().childrenCount();
			setTarget(run.getId(), rowsToProcess);
			logger.info("Batch Importing {} concepts onto new tasks in project {} - batch import id {} ",rowsToProcess, run.getImportRequest().getProjectKey(), run.getId().toString());
			
			if (validateLoadHierarchy(run)) {
				loadConceptsOntoTasks(run);
				completed = true;
			} else {
				run.abortLoad(rows);
			}
		} finally {
			BatchImportState finalState = completed ? BatchImportState.COMPLETED : BatchImportState.FAILED;
			getBatchImportStatus(run.getId()).setState(finalState);
			try {
				fileService.write(getFilePath(run), run.resultsAsCSV());
			} catch (Exception e) {
				logger.error("Failed to save results of batch import",e);
			}
			logger.info("Batch Importing completed in project {} - batch import id {} ",run.getImportRequest().getProjectKey(), run.getId().toString());
		}
	}

	private void prepareConcepts(BatchImportRun run, List<CSVRecord> rows) throws BusinessServiceException {
		// Loop through concepts and form them into a hierarchy to be loaded, if valid
		for (CSVRecord thisRow : rows) {
			if (thisRow.size() > MIN_VIABLE_COLUMNS) {
				BatchImportConcept thisConcept = run.getFormatter().createConcept(thisRow);
				if (validate(run, thisConcept)) {
					run.insertIntoLoadHierarchy(thisConcept);
				}
			} else {
				run.fail(thisRow, "Blank row detected");
			}
		}
	}

	private boolean validateLoadHierarchy(BatchImportRun run) {
		//Parents and children have to exist in the same task, so 
		//check that we're not going to exceed "concepts per task"
		//in doing so.
		boolean valid = true;
		for (BatchImportConcept thisConcept : run.getRootConcept().getChildren()) {
			if (thisConcept.childrenCount() > run.getImportRequest().getConceptsPerTask()) {
				String failureMessage = "Concept " + thisConcept.getSctid() + " at row " + thisConcept.getRow().getRecordNumber() + " has more children than specified for a single task";
				run.fail(thisConcept.getRow(), failureMessage);
				logger.error(failureMessage + " Aborting batch import.");
				valid = false;
			}
		}
		return valid;
	}

	
	private boolean validate (BatchImportRun run, BatchImportConcept concept) {
		if (!validateSCTID(concept.getSctid())) {
			run.fail(concept.getRow(), concept.getSctid() + " is not a valid sctid.");
			return false;
		}
		
		if (!validateSCTID(concept.getParent())) {
			run.fail(concept.getRow(), concept.getParent() + " is not a valid parent identifier.");
			return false;
		}
		
		return true;
	}
	
	private boolean validateSCTID(String sctid) {
		try{
			return VerhoeffCheck.validateLastChecksumDigit(sctid);
		} catch (Exception e) {
			//It's wrong, that's all we need to know.
		}
		return false;
	}



	private void loadConceptsOntoTasks(BatchImportRun run) throws JiraException, ServiceException, BusinessServiceException {
		List<List<BatchImportConcept>> batches = collectIntoBatches(run);
		for (List<BatchImportConcept> thisBatch : batches) {
			AuthoringTask task = createTask(run, thisBatch);
			List<ISnomedBrowserConcept> conceptsLoaded = loadConcepts(run, task, thisBatch);
			logger.info("Loaded concepts onto task {}: {}",task.getKey(),conceptsLoaded);
			primeEditPanel(task, run, conceptsLoaded);
			primeSavedList(task, run, conceptsLoaded);
		}
	}
	

	private void primeEditPanel(AuthoringTask task, BatchImportRun run, List<ISnomedBrowserConcept> conceptsLoaded) {
		try {
			StringBuilder json = new StringBuilder("[");
			boolean isFirst = true;
			for (ISnomedBrowserConcept thisConcept : conceptsLoaded) {
				json.append( isFirst? "" : ",");
				json.append("\"").append(thisConcept.getConceptId()).append("\"");
				isFirst = false;
			}
			json.append("]");
			String user = run.getImportRequest().getCreateForAuthor();
			uiStateService.persistTaskPanelState(task.getProjectKey(), task.getKey(), user, EDIT_PANEL, json.toString());
		} catch (IOException e) {
			logger.warn("Failed to prime edit panel for task " + task.getKey(), e );
		}
	}
	
	private void primeSavedList(AuthoringTask task, BatchImportRun run, List<ISnomedBrowserConcept> conceptsLoaded) {
		try {
			String user = run.getImportRequest().getCreateForAuthor();
			StringBuilder json = new StringBuilder("{\"items\":[");
			boolean isFirst = true;
			for (ISnomedBrowserConcept thisConcept : conceptsLoaded) {
				json.append( isFirst? "" : ",");
				json.append(toSavedListJson(thisConcept));
				isFirst = false;
			}
			json.append("]}");
			uiStateService.persistTaskPanelState(task.getProjectKey(), task.getKey(), user, SAVE_LIST, json.toString());
		} catch (IOException e) {
			logger.warn("Failed to prime saved list for task " + task.getKey(), e );
		}
	}



	private StringBuilder toSavedListJson(ISnomedBrowserConcept thisConcept) {
		StringBuilder buff = new StringBuilder("{\"concept\":");
		buff.append("{\"conceptId\":\"")
			.append(thisConcept.getConceptId())
			.append("\",\"fsn\":\"")
			.append(thisConcept.getFsn())
			.append("\"}}");
		return buff;
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
		str.append(minRow).append(" - ").append(maxRow);
		return str.toString();
	}

	/*private String getAllNotes(BatchImportRun run,
			List<BatchImportConcept> thisBatch) throws BusinessServiceException {
		StringBuilder str = new StringBuilder();
		for (BatchImportConcept thisConcept : thisBatch) {
			str.append(JIRA_HEADING5)
			.append(thisConcept.getSctid())
			.append(":")
			.append(NEW_LINE);
			List<String> notes = run.getFormatter().getAllNotes(thisConcept);
			for (String thisNote: notes) {
				str.append(BULLET)
					.append(thisNote)
					.append(NEW_LINE);
			}
			str.append(NEW_LINE);
		}
		return str.toString();
	}*/
	// Temporary version using html formatting until WRP-2372 gets done
	private String getAllNotes(BatchImportRun run,
			List<BatchImportConcept> thisBatch) throws BusinessServiceException {
		StringBuilder str = new StringBuilder();
		for (BatchImportConcept thisConcept : thisBatch) {
			str.append("<h5>")
			.append(thisConcept.getSctid())
			.append(":</h5>")
			.append("<ul>");
			List<String> notes = run.getFormatter().getAllNotes(thisConcept);
			for (String thisNote: notes) {
				str.append("<li>")
					.append(thisNote)
					.append("</li>");
			}
			str.append("</ul>");
		}
		return str.toString();
	}

	private List<ISnomedBrowserConcept> loadConcepts(BatchImportRun run, AuthoringTask task,
			List<BatchImportConcept> thisBatch) throws BusinessServiceException {
		String branchPath = "MAIN/" + run.getImportRequest().getProjectKey() + "/" + task.getKey();
		List<ISnomedBrowserConcept> conceptsLoaded = new ArrayList<ISnomedBrowserConcept>();
		for (BatchImportConcept thisConcept : thisBatch) {
			boolean loadedOK = false;
			try{
				ISnomedBrowserConcept newConcept = createBrowserConcept(thisConcept, run.getFormatter());
				String warnings = ""; // validateConcept(task, newConcept);
				removeTemporaryIds(newConcept);
				browserService.create(branchPath, newConcept, run.getImportRequest().getCreateForAuthor(), defaultLocales);
				run.succeed(thisConcept.getRow(), "Loaded onto " + task.getKey() + " " + warnings);
				loadedOK = true;
				conceptsLoaded.add(newConcept);
			} catch (ValidationException v) {
				run.fail(thisConcept.getRow(), prettyPrint(v.toApiError()));
			} catch (BusinessServiceException b) {
				//Somewhat expected error, no need for full stack trace
				run.fail(thisConcept.getRow(), b.getMessage());
			} catch (Exception e) {
				run.fail(thisConcept.getRow(), e.getMessage());
				logger.error("Exception during Batch Import at line {}", thisConcept.getRow().getRecordNumber(), e);
			}
			incrementProgress(run.getId(), loadedOK);
		}
		return conceptsLoaded;
	}
	
	/**
	 * We assigned temporary text ids so that we could tell the user which components failed validation
	 * but we don't want to save those, so remove.
	 * @param newConcept
	 */
	private void removeTemporaryIds(ISnomedBrowserConcept newConcept) {
		//Casting is quicker than recreating the lists and replacing
		for (ISnomedBrowserDescription thisDesc : newConcept.getDescriptions()) {
			((SnomedBrowserDescription)thisDesc).setDescriptionId(null);
		}
		
		for (ISnomedBrowserRelationship thisRel : newConcept.getRelationships()) {
			((SnomedBrowserRelationship)thisRel).setRelationshipId(null);
		}
	}

	private String validateConcept(AuthoringTask task,
			ISnomedBrowserConcept newConcept) throws BusinessServiceException {
		StringBuilder warnings = new StringBuilder();
		StringBuilder errors = new StringBuilder();
		List<ISnomedInvalidContent> validationIssues = validationService.validateConcept(getBranchPath(task), newConcept, defaultLocales);
		for (ISnomedInvalidContent thisIssue : validationIssues) {
			if (thisIssue.getSeverity().equals(VALIDATION_ERROR)) {
				errors.append(" ").append(thisIssue.getMessage());
			} else {
				warnings.append(" ").append(thisIssue.getMessage());
			}
		}
		if (errors.length() > 0) {
			throw new BusinessServiceException("Error for concept " + newConcept.getConceptId() + ": " + errors.toString());
		}
		return warnings.toString();
	}

	private String getBranchPath(AuthoringTask task) {
		return "MAIN/" + task.getProjectKey() + "/" + task.getKey();
	}

	private String prettyPrint(ApiError v) {
		StringBuilder buff = new StringBuilder (v.getMessage());
		buff.append(" - ")
			.append(v.getDeveloperMessage())
			.append(": [ ");
		boolean isFirst = true;
		for (Map.Entry<String, Object> thisInfo : v.getAdditionalInfo().entrySet()) {
			if (!isFirst) buff.append (", ");
			else isFirst = false;
			
			buff.append(thisInfo.getKey())
				.append(":")
				.append(thisInfo.getValue());
		}
		buff.append(" ]");
		return buff.toString();
	}

	private ISnomedBrowserConcept createBrowserConcept(
			BatchImportConcept thisConcept, BatchImportFormat formatter) throws BusinessServiceException {
		SnomedBrowserConcept newConcept = new SnomedBrowserConcept();
		newConcept.setConceptId(thisConcept.getSctid());
		newConcept.setActive(true);
		newConcept.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		
		//Set the Parent
		List<ISnomedBrowserRelationship> relationships = new ArrayList<ISnomedBrowserRelationship>();
		ISnomedBrowserRelationship isA = createRelationship(thisConcept.getSctid(), SCTID_ISA, thisConcept.getParent(), CharacteristicType.STATED_RELATIONSHIP);
		relationships.add(isA);
		newConcept.setRelationships(relationships);
		
		//Add Descriptions FSN, then Preferred Term
		List<ISnomedBrowserDescription> descriptions = new ArrayList<ISnomedBrowserDescription>();
		String prefTerm = thisConcept.get(formatter.getIndex(FIELD.FSN_ROOT));
		String fsnTerm = prefTerm + " (" + thisConcept.get(formatter.getIndex(FIELD.SEMANTIC_TAG)) +")";
		
		ISnomedBrowserDescription fsn = createDescription(fsnTerm, SnomedBrowserDescriptionType.FSN);
		descriptions.add(fsn);
		newConcept.setFsn(fsnTerm);
		
		ISnomedBrowserDescription pref = createDescription(prefTerm, SnomedBrowserDescriptionType.SYNONYM);
		descriptions.add(pref);
		
		newConcept.setDescriptions(descriptions);
		return newConcept;
	}
	
	ISnomedBrowserRelationship createRelationship(String sourceSCTID, String type, String destinationSCTID, CharacteristicType characteristic) {
		SnomedBrowserRelationship rel = new SnomedBrowserRelationship();
		rel.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		rel.setSourceId(sourceSCTID);
		rel.setType(new SnomedBrowserRelationshipType(SCTID_ISA));
		//Set a temporary id so the user can tell which item failed validation
		rel.setRelationshipId("rel_isa");
		SnomedBrowserRelationshipTarget destination = new SnomedBrowserRelationshipTarget();
		destination.setConceptId(destinationSCTID);
		rel.setTarget(destination);
		rel.setActive(true);

		return rel;
	}
	
	ISnomedBrowserDescription createDescription(String term, SnomedBrowserDescriptionType type) {
		SnomedBrowserDescription desc = new SnomedBrowserDescription();
		//Set a temporary id so the user can tell which item failed validation
		desc.setDescriptionId("desc_" + type.toString());
		desc.setTerm(term);
		desc.setActive(true);
		desc.setType(type);
		desc.setLang(SnomedConstants.LanguageCodeReferenceSetIdentifierMapping.EN_LANGUAGE_CODE);
		desc.setAcceptabilityMap(DEFAULT_ACCEPTABILIY);
		desc.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
		return desc;
	}

	private String getFilePath(BatchImportRun run) {
		return getFilePath(run.getImportRequest().getProjectKey(), run.getId().toString());
	}
	
	private String getFilePath(String projectKey, String uuid) {
		return projectKey + File.separator + uuid + ".csv";
	}

	public BatchImportStatus getImportStatus(UUID batchImportId) {
		return currentImports.get(batchImportId);
	}

	public String getImportResults(String projectKey, UUID batchImportId) throws IOException {
		return fileService.read(getFilePath(projectKey, batchImportId.toString()));
	}
	
	synchronized private void setTarget(UUID batchImportId, Integer rowsToProcess) {
		BatchImportStatus status = getBatchImportStatus(batchImportId);
		status.setTarget(rowsToProcess);
	}
	
	synchronized private void incrementProgress(UUID batchImportId, boolean loaded) {
		BatchImportStatus status = getBatchImportStatus(batchImportId);
		status.setProcessed(status.getProcessed() == null? 1 : status.getProcessed().intValue() + 1);
		if (loaded) {
			status.setLoaded(status.getLoaded() == null? 1 : status.getLoaded().intValue() + 1);
		}
	}
	
	synchronized private BatchImportStatus getBatchImportStatus(UUID batchImportId) {
		BatchImportStatus status = currentImports.get(batchImportId);
		if (status == null) {
			status = new BatchImportStatus (BatchImportState.RUNNING);
			currentImports.put(batchImportId, status);
		}
		return status;
	}

}
