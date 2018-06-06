package org.ihtsdo.termserver.scripting.fixes;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.mail.*;
import javax.mail.internet.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.*;

/**
 * Reads in a file containing a list of concept SCTIDs and processes them in batches
 * in tasks created on the specified project
 */
public abstract class BatchFix extends TermServerScript implements RF2Constants {
	
	protected int taskSize = 10;
	protected int wiggleRoom = 5;
	protected int failureCount = 0;
	protected int taskThrottle = 30;
	protected int restartFromTask = NOT_SET;
	protected int conceptThrottle = 5;
	protected String targetAuthor;
	protected String targetReviewer;
	protected String[] author_reviewer;
	protected String[] emailDetails;
	protected boolean selfDetermining = false; //Set to true if the batch fix calculates its own data to process
	protected boolean populateEditPanel = true;
	protected boolean populateTaskDescription = true;
	protected boolean reportNoChange = true;
	protected boolean putTaskIntoReview = false;
	protected boolean worksWithConcepts = true;
	protected boolean classifyTasks = false;
	protected boolean validateTasks = false;
	protected boolean groupByIssue = false;
	protected List<Component> allComponentsToProcess = new ArrayList<>();
	protected List<Component> priorityComponents = new ArrayList<>();
	private boolean firstTaskCreated = false;
	
	protected BatchFix (BatchFix clone) {
		if (clone != null) {
			this.inputFile = clone.inputFile;
			this.reportFiles = clone.reportFiles;
			this.project = clone.project;
			this.tsClient = clone.tsClient;
			this.scaClient = clone.scaClient;
		}
	}
	
	protected List<Component> processFile() throws TermServerScriptException {
		Batch batch;
		startTimer();
		if (selfDetermining) {
			batch = formIntoBatch();
		} else {
			allComponentsToProcess = super.processFile();
			batch = formIntoBatch(allComponentsToProcess);
		}
		batchProcess(batch);
		if (emailDetails != null) {
			String msg = "Batch Scripting has completed successfully." + getSummaryText();
			sendEmail(msg, reportFiles[0]);
		}
		return allComponentsToProcess;
	}
	
	
	abstract protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure;

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Default implementation identifies no concepts.  Override if required.
		return new ArrayList<Component>();
	}
	
	protected Batch formIntoBatch (List<Component> allComponents) throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask(author_reviewer);
		
		
		
		//Do we need to prioritize some components?
		List<Component> unprioritized = new ArrayList<> (allComponents);
		unprioritized.removeAll(priorityComponents);
		allComponents = priorityComponents;
		allComponents.addAll(unprioritized);
		
		if (allComponents.size() > 0) {
			String lastIssue = allComponents.get(0).getIssues();
			for (Component thisComponent : allComponents) {
				if (task.size() >= taskSize ||
						(groupByIssue && !lastIssue.equals(thisComponent.getIssues()))) {
					task = batch.addNewTask(author_reviewer);
				}
				task.add(thisComponent);
				lastIssue = thisComponent.getIssues();
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allComponents.size());
		return batch;
	}

	/*
	 * Default batching strategy is just to allocate the concepts in order
	 */
	protected Batch formIntoBatch() throws TermServerScriptException {
		List<Component> allComponentsBeingProcessed = identifyComponentsToProcess();
		return formIntoBatch(allComponentsBeingProcessed);
	}

	protected void saveConcept(Task t, Concept c, String info) {
		try {
			String conceptSerialised = gson.toJson(c);
			debug ((dryRun?"Skipping update":"Updating state") + " of " + c + info);
			if (!dryRun) {
				tsClient.updateConcept(new JSONObject(conceptSerialised), t.getBranchPath());
			}
		} catch (Exception e) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
	}
	
	protected void batchProcess(Batch batch) throws TermServerScriptException {
		int currentTaskNum = 0;
		for (Task task : batch.getTasks()) {
			try {
				currentTaskNum++;
				//If we don't have any concepts in this task eg this is 100% ME file, then skip
				if (task.size() == 0) {
					info ("Skipping Task " + task.getSummary() + " - no concepts to process");
					continue;
				} else if (selfDetermining && restartPosition > 1 && currentTaskNum < restartPosition) {
					//For self determining projects we'll restart based on a task count, rather than the line number in the input file
					info ("Skipping Task " + task.getSummary() + " - restarting from task " + restartPosition);
					continue;
				} else if (restartFromTask != NOT_SET && currentTaskNum < restartFromTask) {
					//For file driven batches, we'll use the r2 restartFromTask setting
					info ("Skipping Task " + task.getSummary() + " - restarting from task " + restartFromTask);
					continue;
				} else if (task.size() > (taskSize + wiggleRoom)) {
					warn (task + " contains " + task.size() + " concepts");
				}
				
				//Create a task for this batch of concepts
				createTask(task);
				String xOfY =  (currentTaskNum) + " of " + batch.getTasks().size();
				info ( (dryRun?"Dry Run " : "Created ") + "task (" + xOfY + "): " + task.getBranchPath());
				incrementSummaryInformation("Tasks created",1);
				
				//Process each component
				int conceptInTask = 0;
				
				//The components in the task might change during processing, so we'll drive this loop with a copy of the initial list
				List<Component> components = new ArrayList<>(task.getComponents());
				for (Component component : components) {
					conceptInTask++;
					processComponent(task, component, conceptInTask, xOfY);
					flushFiles(false); //Update file after each component processed.
				}
				
				if (!dryRun) {
					populateEditAndDescription(task);
					assignTaskToAuthor(task);
					
					Classification classification = null;
					if (classifyTasks) {
						info ("Classifying " + task);
						classification = scaClient.classify(task.getKey());
						debug(classification);
					}
					if (validateTasks) {
						info ("Validating " + task);
						Status status = scaClient.validate(task.getKey());
						debug(status);
					}
					
					if (classification != null) {
						tsClient.waitForCompletion(task.getBranchPath(), classification);
					}
				}
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to process batch " + task.getSummary() + " on task " + task.getKey(), e);
			}
			
			if (processingLimit > NOT_SET && currentTaskNum >= processingLimit) {
				info ("Processing limit of " + processingLimit + " tasks reached.  Stopping");
				break;
			}
		}
	}

	private void createTask(Task task) throws SnowOwlClientException, TermServerScriptException, InterruptedException {
		if (!dryRun) {
			if (firstTaskCreated) {
				debug ("Letting TS catch up - " + taskThrottle + "s nap.");
				Thread.sleep(taskThrottle * 1000);
			} else {
				firstTaskCreated = true;
			}
			boolean taskCreated = false;
			int taskCreationAttempts = 0; 
			while (!taskCreated) {
				try{
					debug ("Creating jira task on project: " + project);
					String taskDescription = populateTaskDescription ? task.getDescriptionHTML() : "Batch Updates - see spreadsheet for details";
					task.setKey(scaClient.createTask(project.getKey(), task.getSummary(), taskDescription));
					debug ("Creating task branch in terminology server: " + task);
					task.setBranchPath(tsClient.createBranch(project.getBranchPath(), task.getKey()));
					taskCreated = true;
				} catch (Exception e) {
					taskCreationAttempts++;
					scaClient.deleteTask(project.getKey(), task.getKey(), true);  //Don't worry if deletion fails
					if (taskCreationAttempts >= 3) {
						throw new TermServerScriptException("Maxed out failure attempts", e);
					}
					warn ("Branch creation failed (" + e.getMessage() + "), retrying...");
				}
			}
		} else {
			task.setKey(project + "-" + getNextDryRunNum());
			task.setBranchPath(project.getBranchPath() + "/" + task.getKey());
		}
	}

	private void processComponent(Task task, Component component, int conceptInTask, String xOfY) throws TermServerScriptException {
		try {
			if (!dryRun && task.getComponents().indexOf(component) != 0) {
				Thread.sleep(conceptThrottle * 1000);
			}
			String info = " Task (" + xOfY + ") Concept (" + conceptInTask + " of " + task.getComponents().size() + ")";
			
			int changesMade = 0;
			if (worksWithConcepts) {
				changesMade = doFix(task, (Concept)component, info);
			} else {
				changesMade = doFix(task, component, info);
			}
			if (changesMade == 0 && reportNoChange) {
				report(task, component, Severity.MEDIUM, ReportActionType.NO_CHANGE, "");
			}
			incrementSummaryInformation("Total changes made", changesMade);
			flushFiles(false);
		} catch (ValidationFailure f) {
			report(task, component, f.getSeverity(), f.getReportActionType(), f.getMessage());
		} catch (InterruptedException | TermServerScriptException e) {
			report(task, component, Severity.CRITICAL, ReportActionType.API_ERROR, getMessage(e));
			if (++failureCount > maxFailures) {
				throw new TermServerScriptException ("Failure count exceeded " + maxFailures);
			}
		}
	}

	private void populateEditAndDescription(Task task) {
		//Prefill the Edit Panel
		try {
			if (populateEditPanel) {
				scaClient.setEditPanelUIState(project.getKey(), task.getKey(), task.toQuotedList());
			}
			scaClient.setSavedListUIState(project.getKey(), task.getKey(), convertToSavedListJson(task));
			String taskDescription = populateTaskDescription ? task.getDescriptionHTML() : "Batch Updates - see spreadsheet for details";
			scaClient.updateTask(project.getKey(), task.getKey(), null, taskDescription, null);
		} catch (Exception e) {
			String msg = "Failed to preload edit-panel ui state: " + e.getMessage();
			warn (msg);
			report(task, null, Severity.LOW, ReportActionType.API_ERROR, msg);
		}
	}
	
	private void assignTaskToAuthor(Task task) throws Exception {
		//Reassign the task to the intended author.  Set at task or processing level
		String taskAuthor = task.getAssignedAuthor();
		if (taskAuthor == null) {
			taskAuthor = targetAuthor;
		}
		if (taskAuthor != null && !taskAuthor.isEmpty()) {
			debug("Assigning " + task + " to " + taskAuthor);
			scaClient.updateTask(project.getKey(), task.getKey(), null, null, taskAuthor);
		}
		
		String taskReviewer = task.getReviewer();
		if (taskReviewer != null && !taskReviewer.isEmpty()) {
			debug("Assigning " + task + " to reviewer " + taskReviewer);
			scaClient.putTaskIntoReview(project.getKey(), task.getKey(), taskReviewer);
		} else if (putTaskIntoReview) {
			debug("Putting " + task + " into review");
			scaClient.putTaskIntoReview(project.getKey(), task.getKey(), null);
		}
	}

	//Override if working with Refsets or Descriptions directly
	protected int doFix(Task task, Component component, String info) {
		return 0;
	}

	protected int ensureDefinitionStatus(Task t, Concept c, DefinitionStatus targetDefStat) {
		int changesMade = 0;
		if (!c.getDefinitionStatus().equals(targetDefStat)) {
			report (t, c, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Definition status changed to " + targetDefStat);
			c.setDefinitionStatus(targetDefStat);
			changesMade++;
		}
		return changesMade;
	}

	protected void init (String[] args) throws TermServerScriptException, IOException {
		if (args.length < 3) {
			info("Usage: java <FixClass> [-a author][-a2 reviewer ][-n <taskSize>] [-r <restart position in file>] [-r2 <restart from task #>] [-l <limit> ] [-t taskCreationDelay] -c <authenticatedCookie> [-d <Y/N>] [-p <projectName>] -f <batch file Location>");
			info(" d - dry run");
			System.exit(-1);
		}
		boolean isTaskSize = false;
		boolean isAuthor = false;
		boolean isReviewer = false;
		boolean isLimit = false;
		boolean isTaskThrottle = false;
		boolean isConceptThrottle = false;
		boolean isRestartFromTask = false;
	
		for (String thisArg : args) {
			if (thisArg.equals("-a")) {
				isAuthor = true;
			} else if (thisArg.equals("-a2")) {
				isReviewer = true;
			} else if (thisArg.equals("-n")) {
				isTaskSize = true;
			} else if (thisArg.equals("-l")) {
				isLimit = true;
			} else if (thisArg.equals("-r2")) {
				isRestartFromTask = true;
			} else if (thisArg.equals("-t") || thisArg.equals("-t1")) {
				isTaskThrottle = true;
			} else if (thisArg.equals("-t2")) {
				isConceptThrottle = true;
			} else if (isTaskThrottle) {
				taskThrottle = Integer.parseInt(thisArg);
				isTaskThrottle = false;
			} else if (isConceptThrottle) {
				conceptThrottle = Integer.parseInt(thisArg);
				isConceptThrottle = false;
			} else if (isRestartFromTask) {
				restartFromTask = Integer.parseInt(thisArg);
				isRestartFromTask = false;
			}else if (isAuthor) {
				targetAuthor = thisArg.toLowerCase();
				isAuthor = false;
			} else if (isReviewer) {
				targetReviewer = thisArg.toLowerCase();
				isReviewer = false;
			} else if (isTaskSize) {
				taskSize = Integer.parseInt(thisArg);
				isTaskSize = false;
			} else if (isLimit) {
				processingLimit = Integer.parseInt(thisArg);
				info ("Limiting number of tasks being created to " + processingLimit);
				isLimit = false;
			}
		}
		
		print ("Number of concepts per task [" + taskSize + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			taskSize = Integer.parseInt(response);
		}
		
		
		if (taskThrottle > 0) {
			print ("Time delay between tasks (throttle) seconds [" +taskThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				taskThrottle = Integer.parseInt(response);
			}
		}
		
		if (conceptThrottle > 0) {
			print ("Time delay between concepts (throttle) seconds [" +conceptThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				conceptThrottle = Integer.parseInt(response);
			}
		}

		try {
			super.init(args);
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to initialise batch fix",e);
		}
		
		if (!selfDetermining && inputFile == null) {
			throw new TermServerScriptException("No valid batch import file detected in command line arguments");
		}
		
		if (targetAuthor == null) {
			throw new TermServerScriptException("No target author detected in command line arguments");
		} else {
			if (targetReviewer != null) {
				author_reviewer = new String[] { targetAuthor, targetReviewer };
			} else {
				author_reviewer = new String[] { targetAuthor };
			}
		}
		
		if (!selfDetermining) {
			info("Reading file from line " + restartPosition + " - " + inputFile.getName());
		}
		
		info ("\nBatching " + taskSize + " concepts per task");
		initialiseReportFiles( new String[] {"TASK_KEY, TASK_DESC, SCTID, FSN, " + (stateComponentType?"CONCEPT_TYPE,":"") + "SEVERITY,ACTION_TYPE," + additionalReportColumns });
	}
	
	protected int ensureAcceptableParent(Task task, Concept c, Concept acceptableParent) {
		List<Relationship> statedParents = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		boolean hasAcceptableParent = false;
		int changesMade = 0;
		for (Relationship thisParent : statedParents) {
			if (!thisParent.getTarget().equals(acceptableParent)) {
				report(task, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_INACTIVATED, "Inactivated unwanted parent: " + thisParent.getTarget());
				thisParent.setActive(false);
				changesMade++;
			} else {
				hasAcceptableParent = true;
			}
		}
		
		if (!hasAcceptableParent) {
			c.addRelationship(IS_A, acceptableParent);
			changesMade++;
			report(task, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, "Added required parent: " + acceptableParent);
		}
		return changesMade;
	}

	
	/**
	 Validate that that any attribute with that attribute type is a descendent of the target Value
	 * @param cardinality 
	 * @throws TermServerScriptException 
	 */
	protected int validateAttributeValues(Task task, Concept concept,
			Concept attributeType, Concept descendentsOfValue, Cardinality cardinality) throws TermServerScriptException {
		
		List<Relationship> attributes = concept.getRelationships(CharacteristicType.ALL, attributeType, ActiveState.ACTIVE);
		Set<Concept> descendents = ClosureCache.getClosureCache().getClosure(descendentsOfValue);
		for (Relationship thisAttribute : attributes) {
			Concept value = thisAttribute.getTarget();
			if (!descendents.contains(value)) {
				Severity severity = thisAttribute.isActive()?Severity.CRITICAL:Severity.LOW;
				String activeStr = thisAttribute.isActive()?"":"inactive ";
				String relType = thisAttribute.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP)?"stated ":"inferred ";
				String msg = "Attribute has " + activeStr + relType + "target which is not a descendent of: " + descendentsOfValue;
				report (task, concept, severity, ReportActionType.VALIDATION_ERROR, msg);
			}
		}
		
		int changesMade = 0;
		
		//Now check cardinality on active stated relationships
		attributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		String msg = null;
		switch (cardinality) {
			case EXACTLY_ONE : if (attributes.size() != 1) {
									msg = "Concept has " + attributes.size() + " active stated attributes of type " + attributeType + " expected exactly one.";
								}
								break;
			case AT_LEAST_ONE : if (attributes.size() < 1) {
									msg = "Concept has " + attributes.size() + " active stated attributes of type " + attributeType + " expected one or more.";
								}
								break;
		}
		
		//If we have no stated attributes of the expected type, attempt to pull from the inferred ones
		if (attributes.size() == 0) {
			changesMade = transferInferredRelationshipsToStated(task, concept, attributeType, cardinality);
		}
		
		//If we have an error message but have not made any changes to the concept, then report that error now
		if (msg != null && changesMade == 0) {
			report (task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
		}
		return changesMade;
	}
	

	private int transferInferredRelationshipsToStated(Task task,
			Concept concept, Concept attributeType, Cardinality cardinality) {
		List<Relationship> replacements = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		int changesMade = 0;
		if (replacements.size() == 0) {
			String msg = "Unable to find any inferred " + attributeType + " relationships to state.";
			report(task, concept, Severity.HIGH, ReportActionType.INFO, msg);
		} else if (cardinality.equals(Cardinality.EXACTLY_ONE) && replacements.size() > 1) {
			String msg = "Found " + replacements.size() + " " + attributeType + " relationships to state but wanted only one!";
			report(task, concept, Severity.HIGH, ReportActionType.INFO, msg);
		} else {
			//Clone the inferred relationships, make them stated and add to concept
			for (Relationship replacement : replacements) {
				Relationship statedClone = replacement.clone(null);
				statedClone.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
				concept.addRelationship(statedClone);
				String msg = "Restated inferred relationship: " + replacement;
				report(task, concept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_ADDED, msg);
				changesMade++;
			}
		}
		return changesMade;
	}
	
	protected int replaceParents(Task t, Concept c, Concept newParent) throws TermServerScriptException {
		Relationship oldParentRel = null;
		Relationship newParentRel = new Relationship (c, IS_A, newParent, UNGROUPED);
		return replaceParents(t, c, oldParentRel, newParentRel, null);
	}
	
	protected int replaceParent(Task t, Concept c, Concept oldParent, Concept newParent) throws TermServerScriptException {
		Relationship oldParentRel = new Relationship (c, IS_A, oldParent, UNGROUPED);
		Relationship newParentRel = new Relationship (c, IS_A, newParent, UNGROUPED);
		return replaceParents(t, c, oldParentRel, newParentRel, null);
	}
	
	protected int replaceParents(Task t, Concept c, Relationship newParentRel, Object[] additionalDetails) throws TermServerScriptException {
		Relationship oldParentRel = null;
		return replaceParents(t, c, oldParentRel, newParentRel, additionalDetails);
	}
	
	protected int replaceParents(Task task, Concept c, Relationship oldParentRel, Relationship newParentRel, Object[] additionalDetails) throws TermServerScriptException {
		int changesMade = 0;
		List<Relationship> parentRels = new ArrayList<Relationship> (c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																	IS_A,
																	ActiveState.ACTIVE));
		for (Relationship parentRel : parentRels) {
			if ((oldParentRel == null || parentRel.equals(oldParentRel)) && !parentRel.equals(newParentRel)) {
				removeParentRelationship (task, parentRel, c, newParentRel.getTarget().toString(), additionalDetails);
				changesMade++;
			} 
		}
		
		//Do we need to add this new relationship?
		if (!c.getParents(CharacteristicType.STATED_RELATIONSHIP).contains(newParentRel.getTarget())) {
			Relationship thisNewParentRel = newParentRel.clone(null);
			thisNewParentRel.setSource(c);
			c.addRelationship(thisNewParentRel);
			changesMade++;
		}
		return changesMade;
	}

	protected int removeParentRelationship(Task t, Relationship r, Concept c, String retained, Object[] additionalDetails) throws TermServerScriptException {
		//Are we inactivating or deleting this relationship?
		String msg;
		ReportActionType action = ReportActionType.UNKNOWN;
		if (!r.isReleased()) {
			c.removeRelationship(r);
			msg = "Deleted parent relationship: " + r.getTarget();
			action = ReportActionType.RELATIONSHIP_DELETED;
		} else {
			r.setEffectiveTime(null);
			r.setActive(false);
			msg = "Inactivated parent relationship: " + r.getTarget();
			action = ReportActionType.RELATIONSHIP_INACTIVATED;
		}
		
		//Also remove this parent from both stated and inferred hierarchies
		c.removeParent(CharacteristicType.STATED_RELATIONSHIP, r.getTarget());
		c.removeParent(CharacteristicType.INFERRED_RELATIONSHIP, r.getTarget());
		
		if (retained != null && !retained.isEmpty()) {
			msg += " in favour of " + retained;
		}
		
		report (t, c, Severity.LOW, action, msg, c.getDefinitionStatus().toString(), additionalDetails);
		return CHANGE_MADE;
	}
	
	protected void removeRelationships (Task t, Concept c, Concept type, int groupId) throws TermServerScriptException {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, groupId)) {
			removeRelationship(t, c, r);
		}
	}
	
	protected void removeRelationship(Task t, Concept c, Relationship r) throws TermServerScriptException {
		//Are we inactivating or deleting this relationship?
		ReportActionType action = ReportActionType.UNKNOWN;
		if (!r.isReleased()) {
			r.setActive(false);
			c.removeRelationship(r);
			action = ReportActionType.RELATIONSHIP_DELETED;
		} else {
			r.setEffectiveTime(null);
			r.setActive(false);
			action = ReportActionType.RELATIONSHIP_INACTIVATED;
		}
		report (t, c, Severity.LOW, action, r);
	}
	
	protected int replaceRelationship(Task t, Concept c, Concept type, Concept value, int groupId, boolean ensureTypeUnique) throws TermServerScriptException {
		int changesMade = 0;
		
		if (type == null || value == null) {
			if (value == null) {
				String msg = "Unable to add relationship of type " + type + " due to lack of a value concept";
				report (t, c, Severity.CRITICAL, ReportActionType.API_ERROR, msg);
			} else if (type == null) {
				String msg = "Unable to add relationship with value " + value + " due to lack of a type concept";
				report (t, c, Severity.CRITICAL, ReportActionType.API_ERROR, msg);
			}
			return NO_CHANGES_MADE;
		}
		//Do we already have this relationship active in the target group?
		List<Relationship> rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
													type,
													value,
													groupId,
													ActiveState.ACTIVE);
		if (rels.size() > 1) {
			report (t, c, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Found two active relationships for " + type + " -> " + value);
			return NO_CHANGES_MADE;
		} else if (rels.size() == 1) {
			return NO_CHANGES_MADE;
		}
		
		//Do we have it inactive?
		rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
				type,
				value,
				groupId,
				ActiveState.INACTIVE);
		if (rels.size() >= 1) {
			Relationship rel = rels.get(0);
			report (t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REACTIVATED, rel);
			rel.setActive(true);
			return CHANGE_MADE;
		}
		
		//Or do we need to create and add?
		//Is this type unique for the concept?  Inactivate any others if so
		//Otherwise just remove other relationships of this type in the target group
		rels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
				type,
				ActiveState.ACTIVE);
		for (Relationship rel : rels) {
			if (ensureTypeUnique || rel.getGroupId() == groupId) {
				removeRelationship(t,c,rel);
				changesMade++;
			}
		}
		
		//Add the new relationship
		Relationship newRel = new Relationship (c, type, value, groupId);
		report (t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, newRel);
		c.addRelationship(newRel);
		changesMade++;
		
		return changesMade;
	}
	
	protected void removeRedundancy(Task t, Concept c, Concept type, int groupNum) throws TermServerScriptException {
		//Remove any redundant attribute in the given group
		List<Concept> allValues = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, groupNum)
									.stream().map(rel -> rel.getTarget()).collect(Collectors.toList());
		for (Concept redundantValue : detectRedundancy(allValues)) {
			Relationship removeMe = new Relationship(c, type, redundantValue, groupNum);
			removeRelationship(t, c, removeMe);
		}
	}
	
	protected List<Concept> detectRedundancy(List<Concept> concepts) throws TermServerScriptException {
		List<Concept> redundant = new ArrayList<>();
		for (Concept thisConcept : concepts) {
			//Is this concept an ancestor of one of the other concepts?  It's redundant if so
			for (Concept otherConcept : concepts) {
				if (!thisConcept.equals(otherConcept) && ancestorsCache.getAncestors(otherConcept).contains(thisConcept)) {
					redundant.add(thisConcept);
				}
			}
		}
		return redundant;
	}

	protected void validatePrefInFSN(Task task, Concept concept) throws TermServerScriptException {
		//Check that the FSN with the semantic tags stripped off is
		//equal to the preferred terms
		List<Description> preferredTerms = concept.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		String trimmedFSN = SnomedUtils.deconstructFSN(concept.getFsn())[0];
		//Special handling for acetaminophen
		if (trimmedFSN.toLowerCase().contains(ACETAMINOPHEN) || trimmedFSN.toLowerCase().contains(PARACETAMOL)) {
			info ("Doing ACETAMINOPHEN processing for " + concept);
		} else {
			for (Description pref : preferredTerms) {
				if (!pref.getTerm().equals(trimmedFSN)) {
					String msg = concept + " has preferred term that does not match FSN: " + pref.getTerm();
					report (task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
				}
			}
		}
	}
	
	protected void sendEmail(String content, File resultsFile)  {
		Properties props = new Properties();
		props.put("mail.transport.protocol", emailDetails[0]);
		props.put("mail.smtp.host", emailDetails[1]); // smtp.gmail.com?
		props.put("mail.smtp.port", emailDetails[2]);
		props.put("mail.smtp.auth", "true");
		Authenticator authenticator = new Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
			return new PasswordAuthentication(emailDetails[3], emailDetails[4]);
			}
		};
		Session session = Session.getDefaultInstance(props, authenticator);
		try {
			Message msg = new MimeMessage(session);
			msg.setFrom(new InternetAddress("techsupport@ihtsdo.org", "IHTSDO Email"));
			msg.addRecipient(Message.RecipientType.TO, new InternetAddress("user@example.com"));
			msg.setSubject("Terminology Server Batch script complete.");
			
			Multipart mp = new MimeMultipart();
			MimeBodyPart htmlPart = new MimeBodyPart();
			htmlPart.setContent(content, "text/html");
			mp.addBodyPart(htmlPart);

			MimeBodyPart attachment = new MimeBodyPart();
			InputStream attachmentDataStream = new FileInputStream(resultsFile);
			attachment.setFileName(resultsFile.getName());
			attachment.setContent(attachmentDataStream, "application/csv");
			mp.addBodyPart(attachment);
			Transport.send(msg);
		} catch (MessagingException | FileNotFoundException | UnsupportedEncodingException e) {
			info ("Failed to send email " + e.getMessage());
		}
	}

	private JSONObject convertToSavedListJson(Task task) throws JSONException, TermServerScriptException {
		JSONObject savedList = new JSONObject();
		JSONArray items = new JSONArray();
		savedList.put("items", items);
		for (Component c : task.getComponents()) {
			items.put(convertToSavedListJson((Concept)c));
		}
		return savedList;
	}

	private JSONObject convertToSavedListJson(Concept concept) throws JSONException, TermServerScriptException {
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("term", concept.getPreferredSynonym());
		jsonObj.put("active", concept.isActive()?"true":"false");
		JSONObject conceptObj = new JSONObject();
		jsonObj.put("concept", conceptObj);
		conceptObj.put("active", concept.isActive()?"true":"false");
		conceptObj.put("conceptId", concept.getConceptId());
		conceptObj.put("fsn", concept.getFsn());
		conceptObj.put("moduleId", concept.getModuleId());
		conceptObj.put("defintionStatus", concept.getDefinitionStatus());
		return jsonObj;
	}
	
	/**
	 * Takes a remodelled concept and saves it as a new concept, inactivates tbe original
	 * and points to it as a replacement
	 * @throws TermServerScriptException 
	 * @throws JSONException 
	 * @throws SnowOwlClientException 
	 */
	protected void cloneAndReplace(Concept concept, Task t) throws TermServerScriptException, SnowOwlClientException, JSONException {
		String originalId = concept.getConceptId();
		Concept original = loadConcept(originalId, t.getBranchPath());
		//Clone the clone to wipe out all identifiers.  It might just 
		Concept clone = concept.clone();
		clone.setActive(true); //Just incase we've cloned an inactive concept
		Concept savedConcept = createConcept(t, clone, ", clone of " + originalId);
		report (t, savedConcept, Severity.LOW, ReportActionType.CONCEPT_ADDED, "Cloned " + original);
		
		//If the original has stated children, they'll all need to be re-pointed to the clone
		for (Concept child : gl.getConcept(originalId).getChildren(CharacteristicType.STATED_RELATIONSHIP)) {
			Concept loadedChild = loadConcept(child.getConceptId(), t.getBranchPath());
			int changesMade = replaceParent(t, loadedChild, original, savedConcept);
			if (changesMade > 0) {
				String conceptSerialised = gson.toJson(loadedChild);
				tsClient.updateConcept(new JSONObject(conceptSerialised), t.getBranchPath());
				report (t, child, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REPLACED, "New Parent: " + savedConcept);
				//Add the child to the task, after the original
				t.addAfter(child, original);
				incrementSummaryInformation("Total children repointed to cloned concepts");
			} else {
				warn ("Locally loaded ontology thought " + concept + " had stated child " + child + " but TS disagreed.");
			}
		}
		
		//Add our clone to the task, after the original
		t.addAfter(savedConcept, original);
		
		//Now inactivate the original 
		original.setActive(false);
		original.setInactivationIndicator(InactivationIndicator.AMBIGUOUS);
		original.setAssociationTargets(AssociationTargets.possEquivTo(savedConcept));
		report (t, original, Severity.LOW, ReportActionType.ASSOCIATION_ADDED, "Possibly Equivalent to " + savedConcept);
		
		checkAndReplaceHistoricalAssociations(t, original, savedConcept, InactivationIndicator.AMBIGUOUS);
		report(t, original, Severity.LOW, ReportActionType.CONCEPT_INACTIVATED);
		
		debug ((dryRun?"Dry run updating":"Updating") + " state of " + original);
		if (!dryRun) {
			String conceptSerialised = gson.toJson(original);
			tsClient.updateConcept(new JSONObject(conceptSerialised), t.getBranchPath());
		}
	}
	
	protected void checkAndReplaceHistoricalAssociations(Task t, Concept inactivating, Concept replacing, InactivationIndicator inactivationIndicator) throws TermServerScriptException {
		List<HistoricalAssociation> histAssocs = gl.usedAsHistoricalAssociationTarget(inactivating);
		if (histAssocs != null && histAssocs.size() > 0) {
			for (HistoricalAssociation histAssoc : histAssocs) {
				Concept source = gl.getConcept(histAssoc.getReferencedComponentId());
				String assocType = gl.getConcept(histAssoc.getRefsetId()).getPreferredSynonym(US_ENG_LANG_REFSET).getTerm().replace("association reference set", "");
				String thisDetail = "Concept was as used as the " + assocType + "target of a historical association for " + source;
				thisDetail += " (since " + (histAssoc.getEffectiveTime().isEmpty()?" prospective release":histAssoc.getEffectiveTime()) + ")";
				report (t, inactivating, Severity.HIGH, ReportActionType.INFO, thisDetail);
				replaceHistoricalAssociation(t, source, inactivating, replacing, inactivationIndicator);
			}
		}
	}

	protected void replaceHistoricalAssociation(Task t, Concept concept, Concept current, Concept replacement, InactivationIndicator inactivationIndicator) throws TermServerScriptException {
		//We need a copy from the TS
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		loadedConcept.setInactivationIndicator(inactivationIndicator);
		//Make sure we only have one current association target
		int targetCount = loadedConcept.getAssociationTargets().size();
		if (targetCount > 1) {
			report (t, concept, Severity.HIGH, ReportActionType.INFO, "Replacing 1 historical association out of " + targetCount);
		}
		AssociationTargets targets = loadedConcept.getAssociationTargets();
		targets.remove(current.getConceptId());
		targets.getPossEquivTo().add(replacement.getConceptId());
		updateConcept(t, loadedConcept, " with re-jigged inactivation indicator and historical associations");
		report (t, loadedConcept, Severity.MEDIUM, ReportActionType.ASSOCIATION_ADDED, "InactReason set to " + inactivationIndicator + " and PossiblyEquivalentTo: " + replacement);
	}
	
	protected int checkAndSetProximalPrimitiveParent(Task t, Concept c, Concept newPPP) throws TermServerScriptException {
		int changesMade = 0;
		List<Concept> ppps = determineProximalPrimitiveParents(c);

		if (ppps.size() != 1) {
			report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept found to have " + ppps.size() + " proximal primitive parents.  Cannot state parent as: " + newPPP);
		} else {
			Concept ppp = ppps.get(0);
			if (ppp.equals(newPPP)) {
				changesMade += setProximalPrimitiveParent(t, c, ppp);
			} else {
				report (t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Calculated PPP " + ppp + " does not match that suggested by template: " + newPPP + ", cannot remodel.");
			}
		}
		return changesMade;
	}

	private int setProximalPrimitiveParent(Task t, Concept c, Concept newParent) throws TermServerScriptException {
		int changesMade = 0;
		List<Relationship> parentRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		//Do we in fact need to do anything?
		if (parentRels.size() == 1 && parentRels.get(0).getTarget().equals(newParent)) {
			report (t, c, Severity.NONE, ReportActionType.NO_CHANGE, "Concept already has template PPP: " + newParent);
		} else {
			boolean doAddition = true;
			for (Relationship r : parentRels) {
				if (r.getTarget().equals(newParent)) {
					doAddition = false;
				} else {
					//We can only remove relationships which are subsumed by the new Proximal Primitive Parent
					//Need a local copy of concept for transitive closure questions
					Concept thisParentLocal = gl.getConcept(r.getTarget().getConceptId());
					if (thisParentLocal.getAncestors(NOT_SET).contains(newParent)) {
						removeParentRelationship(t, r, c, newParent.toString(), null);
						changesMade++;
					} else {
						report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Unable to remove parent " + thisParentLocal + " because it it not subsumed by " + newParent );
					}
				}
			}

			if (doAddition) {
				Relationship newParentRel = new Relationship(c, IS_A, newParent, 0);
				c.addRelationship(newParentRel);
				changesMade++;
			}
		}
		return changesMade;
	}

}
