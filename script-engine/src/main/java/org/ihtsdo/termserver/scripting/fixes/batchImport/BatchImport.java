package org.ihtsdo.termserver.scripting.fixes.batchImport;

import org.apache.commons.csv.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.termserver.scripting.BatchJobClass;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.*;
import org.snomed.otf.scheduler.domain.Job.ProductionStatus;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.*;
import java.util.*;

public class BatchImport extends BatchFix implements BatchJobClass {
	
	private static final String[] LATERALITY = new String[] { "left", "right"};
	Map<String, Concept> conceptsLoaded;
	private BatchImportFormat format;
	private String moduleId;
	private Boolean allowLateralizedContent = true;
	private BatchImportConcept rootConcept = BatchImportConcept.createRootConcept();
	private Map <String, BatchImportConcept> allValidConcepts = new HashMap<>();
	CharacteristicType charType = CharacteristicType.STATED_RELATIONSHIP;
	
	public BatchImport() {
		super(null);
	}

	protected BatchImport(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
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
	
	protected void init(JobRun jobRun) throws TermServerScriptException {
		ReportSheetManager.targetFolderId = "1bO3v1PApVCEc3BWWrKwc525vla7ZMPoE"; // Batch Import
		selfDetermining = true;
		populateEditPanel = true;
		populateTaskDescription = true;
		conceptsLoaded = new HashMap<>();
		moduleId = SCTID_CORE_MODULE;
		maxFailures = 1500;
		super.init(jobRun);
	}
	
	@Override
	public List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		if (inputFile == null) {
			throw new TermServerScriptException("Unable to identify components to process, no input file specified.");
		}
		
		try {
			Reader in = new InputStreamReader(new FileInputStream(inputFile));
			//SIRS files contain duplicate headers (eg multiple Notes columns) 
			//So read 1st row as a record instead.
			CSVParser parser = CSVFormat.EXCEL.parse(in);
			CSVRecord header = parser.iterator().next();
			format = BatchImportFormat.determineFormat(header);
			//And load the remaining records into memory
			prepareConcepts(parser.getRecords());
			allowLateralizedContent(allowLateralizedContent);
			parser.close();
			validateLoadHierarchy();
			return null;
		} catch (Exception e) {
			throw new TermServerScriptException("Failure while reading " + inputFile.getAbsolutePath(), e);
		}
	}
	
	private void prepareConcepts(List<CSVRecord> rows) throws TermServerScriptException {
		// Loop through concepts and form them into a hierarchy to be loaded, if valid
		int minViableColumns = format.getHeaders().length;
		for (CSVRecord thisRow : rows) {
			if (thisRow.getRecordNumber() % 50 == 0) {
				info("Row " + thisRow.getRecordNumber());
			}
			if (thisRow.size() >= minViableColumns) {
				try {
					BatchImportConcept thisConcept = format.createConcept(thisRow, moduleId);
					if (validate(thisConcept)) {
						insertIntoLoadHierarchy(thisConcept);
					}
				} catch (Exception e) {
					throw new TermServerScriptException(thisRow.toString(), e);
				}
			} else {
				throw new TermServerScriptException("Blank row detected: "  + thisRow.toString());
			}
		}
	}
	
	public void insertIntoLoadHierarchy(BatchImportConcept thisConcept) throws TermServerScriptException {
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
			if (existingConcept.getFirstParent().equals((Concept)thisConcept)) {
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
			report ((Task)null, concept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Concept with this FSN already exists", alreadyExists);
			return false;
		}
		
		return true;
	}

	@Override
	protected Batch formIntoBatch (List<Component> allComponents) {
		Batch batch = new Batch(inputFile.getAbsolutePath(), GraphLoader.getGraphLoader());
		//Loop through all the children of root, starting a new task every "concepts per task"
		Task thisTask = null;
		for (Concept thisChild : getRootConcept().getChildren(CharacteristicType.STATED_RELATIONSHIP)) {
			BatchImportConcept child = (BatchImportConcept) thisChild;
			if (thisTask == null || thisTask.size() >= taskSize) {
				thisTask = batch.addNewTask(author_reviewer);
			}
			//We can be sure that all descendants will not exceed our batch limit, having already validated
			thisTask.add(child);
			child.addDescendants(thisTask);
		}
		return batch;
	}
	

	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException {
		BatchImportConcept concept = (BatchImportConcept)c;
		String statedForm = SnomedUtils.getModel(concept, CharacteristicType.STATED_RELATIONSHIP, true);
		try{
			validateConcept(t, c);
			removeTemporaryIds(c);
			Concept createdConcept = createConcept(t, c, null);
			String origRef = "";
			if (format.getIndex(BatchImportFormat.FIELD.ORIG_REF) != BatchImportFormat.FIELD_NOT_FOUND) {
				origRef = concept.get(format.getIndex(BatchImportFormat.FIELD.ORIG_REF));
			}
			report (t, createdConcept, Severity.NONE, ReportActionType.CONCEPT_ADDED, origRef, statedForm);
			conceptsLoaded.put(c.getId(),createdConcept);
			countIssue(createdConcept);
		} catch (Exception e) {
			throw new TermServerScriptException(concept.getRow().getRecordNumber() + ": " + concept.getRow(),e);
		}
		return CHANGE_MADE;
	}

	// Temporary version using html formatting until WRP-2372 gets done
	protected String getAllNotes(Task task, Map<String, Concept> conceptsLoaded) throws TermServerScriptException {
		StringBuilder str = new StringBuilder();
		for (Map.Entry<String, Concept> thisEntry: conceptsLoaded.entrySet()) {
			String thisOriginalSCTID = thisEntry.getKey();
			BatchImportConcept biConcept = getConcept(thisOriginalSCTID);
			Concept thisConcept = thisEntry.getValue();
			str.append("<h5>")
			.append(thisConcept.getConceptId())
			.append(" - ")
			.append(thisConcept.getFsn())
			.append(":</h5>")
			.append("<ul>");
			
			if (format.getIndex(BatchImportFormat.FIELD.ORIG_REF) != BatchImportFormat.FIELD_NOT_FOUND) {
				String origRef = biConcept.get(format.getIndex(BatchImportFormat.FIELD.ORIG_REF));
				if (origRef != null && !origRef.isEmpty()) {
					str.append("<li>Originating Reference: ")
					.append(origRef)
					.append("</li>");
				}
			}
			
			for (int docIdx : format.getDocumentationFields()) {
				String docStr = biConcept.get(docIdx);
				if (docStr != null && !docStr.isEmpty()) {
					str.append("<li>")
					.append(format.getHeaders()[docIdx])
					.append(": ")
					.append(docStr)
					.append("</li>");
				}
			}
			
			List<String> notes = format.getAllNotes(biConcept);
			for (String thisNote: notes) {
				str.append("<li>")
					.append(thisNote)
					.append("</li>");
			}
			str.append("</ul>");
		}
		return str.toString();
	}
	
	/**
	 * We assigned temporary text ids so that we could tell the user which components failed validation
	 * but we don't want to save those, so remove.
	 * @param newConcept
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
	
	private String validateConcept(Task task, Concept newConcept) throws TermServerScriptException {
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
