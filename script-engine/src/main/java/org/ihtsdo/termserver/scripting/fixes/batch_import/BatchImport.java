package org.ihtsdo.termserver.scripting.fixes.batch_import;

import org.apache.commons.csv.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.termserver.scripting.BatchJobClass;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.LanguageHelper;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.*;
import java.util.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BatchImport extends BatchFix implements BatchJobClass {

	private static final Logger LOGGER = LoggerFactory.getLogger(BatchImport.class);
	private static final char DELIMITER = ';';
	private static final String LIST_ITEM_START = "<li>";
	private static final String LIST_ITEM_END = "</li>";

	private static final String[] LATERALITY = new String[] { "left", "right"};
	private Map<String, Concept> conceptsLoaded;
	private BatchImportFormat fileFormat;
	private String moduleId;
	private Boolean allowLateralizedContent = true;
	private BatchImportConcept rootConcept = BatchImportConcept.createRootConcept();
	private Map <String, BatchImportConcept> allValidConcepts = new HashMap<>();
	private CharacteristicType charType = CharacteristicType.STATED_RELATIONSHIP;
	
	public BatchImport() {
		super(null);
	}

	protected BatchImport(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		Map<String, String> params = new HashMap<>();
		TermServerScript.run(BatchImport.class, args, params);
	}
	
	@Override
	public Job getJob() {
		JobParameters params = new JobParameters();
		setStandardParameters(params);
		return new Job()
				.withCategory(new JobCategory(JobType.REPORT, JobCategory.QI))
				.withName("Batch Import")
				.withDescription("")
				.withProductionStatus(ProductionStatus.HIDEME)
				.withParameters(params)
				.withTag(INT)
				.build();
	}

	@Override
	protected void init(JobRun jobRun) throws TermServerScriptException {
		ReportSheetManager.setTargetFolderId("1bO3v1PApVCEc3BWWrKwc525vla7ZMPoE"); // Batch Import
		selfDetermining = true;
		populateEditPanel = true;
		populateTaskDescription = true;
		conceptsLoaded = new HashMap<>();
		moduleId = SCTID_CORE_MODULE;
		maxFailures = 1500;
		headers = "TaskKey, TaskDesc, SCTID, Descriptions, ConceptType, Severity, ActionType, ";
		super.init(jobRun);
	}
	
	@Override
	public List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		if (getInputFile() == null) {
			throw new TermServerScriptException("Unable to identify components to process, no input file specified.");
		}
		
		try {
			Reader in = new InputStreamReader(new FileInputStream(getInputFile()));
			//SIRS files contain duplicate headers (eg multiple Notes columns) 
			//So read 1st row as a record instead.
			CSVFormat csvFormat = CSVFormat.EXCEL.builder().setDelimiter(DELIMITER).build();
			CSVParser parser = csvFormat.parse(in);
			CSVRecord header = parser.iterator().next();
			fileFormat = BatchImportFormat.determineFormat(header);

			if (fileFormat.isFormat(BatchImportFormat.FORMAT.PHAST)) {
				moduleId = "11000188109";
				LanguageHelper.setLangResetOverride("fr", "21000188104" ); //PHAST French language reference set (foundation metadata concept)
			}
			//And load the remaining records into memory
			prepareConcepts(parser.getRecords());
			allowLateralizedContent(allowLateralizedContent);
			parser.close();
			validateLoadHierarchy();
			return null;
		} catch (Exception e) {
			throw new TermServerScriptException("Failure while reading " + getInputFile().getAbsolutePath(), e);
		}
	}
	
	private void prepareConcepts(List<CSVRecord> rows) throws TermServerScriptException {
		// Loop through concepts and form them into a hierarchy to be loaded, if valid
		int minViableColumns = fileFormat.getHeaders().length;
		for (CSVRecord thisRow : rows) {
			if (thisRow.getRecordNumber() % 50 == 0) {
				LOGGER.info("Row {}", thisRow.getRecordNumber());
			}

			if (thisRow.size() >= minViableColumns) {
				try {
					BatchImportConcept thisConcept = fileFormat.createConcept(thisRow, moduleId);
					if (validate(thisConcept)) {
						insertIntoLoadHierarchy(thisConcept);
					}
				} catch (Exception e) {
					throw new TermServerScriptException(thisRow.toString(), e);
				}
			} else {
				throw new TermServerScriptException("Blank row detected: "  + thisRow);
			}
		}
	}
	
	public void insertIntoLoadHierarchy(BatchImportConcept thisConcept) {
		allValidConcepts.put(thisConcept.getId(), thisConcept);
		
		//Are we loading the parent of this concept? Add as a child if so
		if (allValidConcepts.containsKey(thisConcept.getFirstParent().getId())) {
			allValidConcepts.get(thisConcept.getFirstParent().getId()).addChild(charType, thisConcept);
		} else {
			//otherwise add as a child of the root concept
			rootConcept.addChild(charType,thisConcept);
		}
		
		//Is this concept a parent of existing known children?  Remove children 
		//from the root concept and add under this concept if so
		for (BatchImportConcept existingConcept : allValidConcepts.values()) {
			if (existingConcept.getFirstParent().equals(thisConcept)) {
				rootConcept.removeChild(charType, existingConcept);
				thisConcept.addChild(charType, existingConcept);
			}
		}
	}

	private boolean validateLoadHierarchy() throws TermServerScriptException {
		//Parents and children have to exist in the same task, so 
		//check that we're not going to exceed "concepts per task"
		//in doing so.
		boolean valid = true;
		for (Concept thisChild : getRootConcept().getChildren(CharacteristicType.STATED_RELATIONSHIP)) {
			BatchImportConcept child = (BatchImportConcept) thisChild;
			if (thisChild.getChildren(CharacteristicType.STATED_RELATIONSHIP).size() >= taskSize) {
				String failureMsg = "Concept " + thisChild.getId() + " at row " + child.getRow().getRecordNumber() + " has more children than allowed for a single task";
				throw new TermServerScriptException(failureMsg);
			}
		}
		return valid;
	}
	
	private boolean validate(BatchImportConcept concept) throws TermServerScriptException {
		if (!concept.requiresNewSCTID()) {
			SnomedUtils.isValid(concept.getId(), PartitionIdentifier.CONCEPT, true);
		}
		SnomedUtils.isValid(concept.getFirstParent().getId(), PartitionIdentifier.CONCEPT, true);
		
		//Do we already have a concept with this FSN?
		Concept alreadyExists = gl.findConcept(concept.getFsn());
		if (alreadyExists != null) {
			report((Task)null, concept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept with this FSN already exists ", alreadyExists);
			return false;
		}
		
		return true;
	}

	@Override
	protected Batch formIntoBatch (List<Component> allComponents) {
		Batch batch = new Batch(getInputFile().getAbsolutePath(), GraphLoader.getGraphLoader());
		//Loop through all the children of root, starting a new task every "concepts per task"
		Task thisTask = null;
		for (Concept thisChild : getRootConcept().getChildren(CharacteristicType.STATED_RELATIONSHIP)) {
			BatchImportConcept child = (BatchImportConcept) thisChild;
			if (thisTask == null || thisTask.size() >= taskSize) {
				thisTask = batch.addNewTask(getNextAuthor(), null);
			}
			//We can be sure that all descendants will not exceed our batch limit, having already validated
			thisTask.add(child);
			child.addDescendants(thisTask);
		}
		return batch;
	}
	
	@Override
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		BatchImportConcept concept = (BatchImportConcept)c;
		String statedForm = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		try{
			validateConcept(c);
			removeTemporaryIds(c);
			Concept createdConcept = createConcept(t, c, null);
			String origRef = "";
			if (fileFormat.getIndex(BatchImportFormat.FIELD.ORIG_REF) != BatchImportFormat.FIELD_NOT_FOUND) {
				origRef = concept.get(fileFormat.getIndex(BatchImportFormat.FIELD.ORIG_REF));
			}
			report(t, createdConcept, Severity.NONE, ReportActionType.CONCEPT_ADDED, origRef, statedForm);
			conceptsLoaded.put(createdConcept.getId(), createdConcept);
			c.setId(createdConcept.getId());
			countIssue(createdConcept);
		} catch (Exception e) {
			throw new TermServerScriptException(concept.getRow().getRecordNumber() + ": " + concept.getRow(),e);
		}
		return CHANGE_MADE;
	}

	public String getAllNotes(Task task) throws TermServerScriptException {
		StringBuilder str = new StringBuilder();
		for (Component thisComponent : task.getComponents()) {
			BatchImportConcept biConcept = (BatchImportConcept)thisComponent;
			if (thisComponent.getId() == null) {
				LOGGER.warn(biConcept + " did not get assigned an SCTID - load failed?");
				continue;
			}
			Concept thisConcept = conceptsLoaded.get(thisComponent.getId());
			str.append("<h5>")
			.append(thisConcept)
			.append(":</h5>")
			.append("<ul>");
			
			if (fileFormat.getIndex(BatchImportFormat.FIELD.ORIG_REF) != BatchImportFormat.FIELD_NOT_FOUND) {
				String origRef = biConcept.get(fileFormat.getIndex(BatchImportFormat.FIELD.ORIG_REF));
				if (origRef != null && !origRef.isEmpty()) {
					str.append(LIST_ITEM_START)
							.append("Originating Reference: ")
							.append(origRef)
							.append(LIST_ITEM_END);
				}
			}
			
			for (int docIdx : fileFormat.getDocumentationFields()) {
				String docStr = biConcept.get(docIdx);
				if (docStr != null && !docStr.isEmpty()) {
					str.append(LIST_ITEM_START)
					.append(fileFormat.getHeaders()[docIdx])
					.append(": ")
					.append(docStr)
					.append(LIST_ITEM_END);
				}
			}
			
			List<String> notes = fileFormat.getAllNotes(biConcept);
			for (String thisNote: notes) {
				str.append(LIST_ITEM_START)
					.append(thisNote)
					.append(LIST_ITEM_END);
			}
			str.append("</ul>");
		}
		return str.toString();
	}
	
	/**
	 * We assigned temporary text ids so that we could tell the user which components failed validation
	 * but we don't want to save those, so remove.
	 */
	private void removeTemporaryIds(Concept c) {
		c.setId(null);
		for (Description d : c.getDescriptions()) {
			d.setDescriptionId(null);
		}
		
		for (Relationship r : c.getRelationships()) {
			r.setRelationshipId(null);
		}
	}
	
	private String validateConcept(Concept newConcept) throws TermServerScriptException {
		if (!isLateralizedContentAllowed()) {
			//Check for lateralized content
			for (String thisBadWord : LATERALITY) {
				if (newConcept.getFsn().toLowerCase().contains(thisBadWord)) {
					throw new TermServerScriptException ("Lateralized content detected");
				}
			}
		}
		return null;
	}

	public Boolean isLateralizedContentAllowed() {
		return allowLateralizedContent;
	}

	public void allowLateralizedContent(Boolean allowLateralizedContent) {
		this.allowLateralizedContent = allowLateralizedContent;
	}
	
	public BatchImportConcept getRootConcept() {
		return rootConcept;
	}
	
	public BatchImportConcept getConcept (String sctId) {
		return allValidConcepts.get(sctId);
	}
	
}
