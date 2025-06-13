package org.ihtsdo.termserver.scripting.fixes;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

import javax.mail.*;
import javax.mail.internet.*;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.Status;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.RelationshipTemplate.Mode;
import org.ihtsdo.termserver.scripting.fixes.batch_import.BatchImport;
import org.ihtsdo.termserver.scripting.util.AcceptabilityMode;
import org.ihtsdo.termserver.scripting.util.DialectChecker;
import org.ihtsdo.termserver.scripting.util.HistAssocUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.scheduler.domain.JobParameters;
import org.snomed.otf.scheduler.domain.JobRun;

import us.monoid.json.*;

/**
 * Reads in a file containing a list of concept SCTIDs and processes them in batches
 * in tasks created on the specified project
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BatchFix extends TermServerScript implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(BatchFix.class);

	private static final int MAX_CONCEPTS_IN_TASK_DESCRIPTION = 50;

	protected int taskSize = 10;
	protected int wiggleRoom = 5;
	protected int failureCount = 0;
	protected int validationCount = 0;
	protected int taskThrottle = 5;
	protected int restartFromTask = NOT_SET;
	protected int conceptThrottle = 2;
	protected List<String> authors;
	protected List<String> reviewers;
	protected String[] emailDetails;
	protected boolean selfDetermining = false; //Set to true if the batch fix calculates its own data to process
	protected boolean populateEditPanel = true;
	protected boolean populateTaskDescription = true;
	protected boolean reportNoChange = true;
	protected boolean worksWithConcepts = true;
	protected boolean classifyTasks = false;
	protected boolean validateTasks = false;
	protected boolean groupByIssue = false;
	protected boolean keepIssuesTogether = false;
	protected List<Component> allComponentsToProcess = new ArrayList<>();
	protected List<Component> priorityComponents = new ArrayList<>();
	protected int priorityBatchSize = 10;
	protected boolean correctRoundedSCTIDs = false;
	public static final String DEFAULT_TASK_DESCRIPTION = "Batch Updates - see spreadsheet for details";
	protected String taskPrefix = null;
	protected Map<Concept, Set<Concept>> historicallyRewiredPossEquivTo = new HashMap<>();
	protected HistAssocUtils histAssocUtils = new HistAssocUtils(this);
	private Batch currentBatch;
	protected TaskHelper taskHelper;

	protected BatchFix(TermServerScript clone) {
		if (clone != null) {
			if (clone.hasInputFile()) {
				this.inputFiles.add(0, clone.getInputFile());
			}
			setReportManager(clone.getReportManager());
			this.project = clone.getProject();
			this.tsClient = clone.getTSClient();
			this.scaClient = clone.getAuthoringServicesClient();
		}
		this.headers = "TaskKey, TaskDesc, SCTID, FSN, ConceptType, Severity, ActionType, ";
		taskHelper = new TaskHelper(this, taskThrottle, populateTaskDescription, taskPrefix);
	}

	@Override
	public void runJob() throws TermServerScriptException {
		processFile();
	}

	@Override
	protected List<Component> processFile() throws TermServerScriptException {
		startTimer();
		if (selfDetermining) {
			allComponentsToProcess = identifyComponentsToProcess();
		} else {
			allComponentsToProcess = super.processFile();
		}
		batchProcess(formIntoBatch(allComponentsToProcess));
		return allComponentsToProcess;
	}

	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		throw new NotImplementedException("Override doFix in the Concrete Class");
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Default implementation uses any specified ECL
		return new ArrayList<>(SnomedUtils.sortFSN(findConcepts(subsetECL)));
	}

	protected Batch formIntoBatch(List<Component> allComponents) throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask(getNextAuthor(), getNextReviewer());
		//Do we need to prioritize some components?
		if (!priorityComponents.isEmpty()) {
			List<Component> unprioritized = new ArrayList<>(allComponents);
			unprioritized.removeAll(priorityComponents);
			allComponents = priorityComponents;
			allComponents.addAll(unprioritized);
		}

		//If we're grouping by issue or keeping issues together, then we need to sort by issue
		if (groupByIssue || keepIssuesTogether) {
			LOGGER.info("Sorting components by issue");
			allComponents = allComponents.stream()
					.sorted(Comparator.comparing(Component::getIssues))
					.collect(Collectors.toList());
		}

		if (!allComponents.isEmpty()) {
			String lastIssue = allComponents.get(0).getIssues();
			int currentPosition = 0;
			for (Component thisComponent : allComponents) {
				//DRUGS-522 Priority concepts might need a smaller batch size
				int thisTaskMaxSize = priorityComponents.contains(thisComponent) ? priorityBatchSize : taskSize;
				int remainingSpace = thisTaskMaxSize - task.size();
				if (keepIssuesTogether) {
					//If we're on the same issue, don't break the task
					if (!lastIssue.equals(thisComponent.getIssues()) &&
							!peekAheadFits(asConcepts(allComponents), currentPosition, remainingSpace)) {
						task = batch.addNewTask(getNextAuthor(), getNextReviewer());
					}
				} else if (task.size() >= thisTaskMaxSize ||
						(groupByIssue && !lastIssue.equals(thisComponent.getIssues()))) {
					task = batch.addNewTask(getNextAuthor(), getNextReviewer());
				}
				task.add(thisComponent);
				lastIssue = thisComponent.getIssues();
				currentPosition++;
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_TO_PROCESS, allComponents.size());
		return batch;
	}

	protected String getNextAuthor() {
		int nextAuthorIdx = Task.getNextTaskSequence() % authors.size();
		return authors.get(nextAuthorIdx);
	}

	protected String getNextReviewer() {
		if (reviewers == null || reviewers.isEmpty()) {
			return null;
		}
		int nextReviewerIdx = Task.getNextTaskSequence() % reviewers.size();
		return reviewers.get(nextReviewerIdx);
	}

	private boolean peekAheadFits(List<Concept> concepts, int pos, int remainingSpace) {
		//Given the issue of the current position, can we fit all successive concepts with the same issue into the 
		//same task, or do we need to start a new one?
		String thisIssue = concepts.get(pos).getIssues();
		int sameIssueCount = 0;

		while (pos < concepts.size() - 1 && thisIssue.equals(concepts.get(++pos).getIssues())) {
			sameIssueCount++;
		}

		return sameIssueCount <= remainingSpace;
	}

	protected Batch formIntoGroupedBatch(List<List<Component>> allComponentList) {
		Batch batch = new Batch(getScriptName());
		int componentsToProcess = 0;
		for (List<Component> thisSet : allComponentList) {
			Task task = batch.addNewTask(getNextAuthor(), getNextReviewer());
			if (!thisSet.isEmpty()) {
				String lastIssue = thisSet.get(0).getIssues();
				for (Component thisComponent : thisSet) {
					if (task.size() >= taskSize ||
							(groupByIssue && !lastIssue.equals(thisComponent.getIssues()))) {
						task = batch.addNewTask(getNextAuthor(), getNextReviewer());
					}
					task.add(thisComponent);
					componentsToProcess++;
					lastIssue = thisComponent.getIssues();
				}
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_TO_PROCESS, componentsToProcess);
		return batch;
	}

	protected void removeFromLaterTasks(Task t, Concept c) {
		//Loop through all tasks and remove concept from any after that specified
		boolean taskFound = false;
		for (Task thisTask : currentBatch.getTasks()) {
			if (!taskFound) {
				if (thisTask.equals(t)) {
					taskFound = true;
				}
				continue;
			}
			thisTask.remove(c);
		}
	}

	protected void batchProcess(Batch batch) throws TermServerScriptException {
		int currentTaskNum = 0;
		this.currentBatch = batch;
		for (Task task : batch.getTasks()) {
			try {
				currentTaskNum++;
				batchProcessTask(batch, task, currentTaskNum);
				flushFiles(false);
			} catch (Exception e) {
				throw new TermServerScriptException("Failed to process batch " + task.getSummary() + " on task " + task.getKey(), e);
			}

			if (processingLimit > NOT_SET && currentTaskNum >= processingLimit) {
				LOGGER.info("Processing limit of {} tasks reached.  Stopping", processingLimit);
				break;
			}
		}
	}

	private void batchProcessTask(Batch batch, Task task, int currentTaskNum) throws Exception {
		onNewTask(task);

		if (taskShouldBeSkipped(task, currentTaskNum)) {
			return;
		}

		String xOfY = (currentTaskNum) + " of " + batch.getTasks().size();
		createTaskIfRequired(batch, task, xOfY);

		//Process each component
		int conceptInTask = 0;

		//The components in the task might change during processing, so we'll drive this loop with a copy of the initial list
		List<Component> components = new ArrayList<>(task.getComponents());
		for (Component component : components) {
			conceptInTask++;
			processComponent(task, component, conceptInTask, xOfY);
			//Update file after each component processed - if data limits allow
			flushFilesSoft();  //Soft flush is optional
		}

		//Don't update Editpanel or assign task if we're using a pre-existing one
		if (!dryRun && !task.isPreExistingTask()) {
			populateEditPanel(task);
			updateTask(task, getReportName(), getReportManager().getUrl());

			Classification classification = null;
			if (classifyTasks) {
				LOGGER.info("Classifying {}", task);
				classification = scaClient.classify(task.getKey());
				LOGGER.debug("{}",classification);
			}

			if (validateTasks) {
				LOGGER.info("Validating {}", task);
				Status status = scaClient.validate(task.getKey());
				LOGGER.debug("{}", status);
			}

			if (classification != null) {
				try {
					tsClient.waitForCompletion(task.getBranchPath(), classification);
				} catch (Exception e) {
					LOGGER.error("Failed to wait for classification " + classification, e);
				}
			}
		}
	}

	protected Task createSingleTask() throws TermServerScriptException {
		Batch batch = new Batch(getScriptName());
		Task task = batch.addNewTask(getNextAuthor(), getNextReviewer());
		createTaskIfRequired(batch, task, "1 of 1");
		return task;
	}

	private void createTaskIfRequired(Batch batch, Task task, String xOfY) throws TermServerScriptException {
		//Did user specify that we're working in a particular task?  If so, we can only
		//work within a single task
		if (this.taskKey != null) {
			if (batch.getTasks().size() > 1) {
				throw new TermServerScriptException("Task key specified.  Cannot work with " + batch.getTasks().size() + " tasks.  Check number of concepts per task");
			}
			task.setBranchPath(this.getProject().getBranchPath());  //Project contains the task branch path at this point
			task.setPreExistingTask(true);
			String dryRunStr = (dryRun ? "Dry Run, p" : "P");
			LOGGER.info("{}re-existing task specified: {}", dryRunStr, task.getBranchPath());
		} else {
			//Create a task for this batch of concepts
			taskHelper.createTask(task);
			String msg = (dryRun ? "Dry Run " : "Created ") + "task (" + xOfY + "): " + task.getBranchPath();
			LOGGER.info(msg);
			incrementSummaryInformation("Tasks created", 1);
		}
	}

	private boolean taskShouldBeSkipped(Task task, int currentTaskNum) {
		//If we don't have any concepts in this task eg this is 100% ME file, then skip
		if (task.size() == 0) {
			LOGGER.info("Skipping Task {} - no concepts to process", task.getSummary());
			return true;
		} else if (selfDetermining && restartPosition > 1 && currentTaskNum < restartPosition) {
			//For self determining projects we'll restart based on a task count, rather than the line number in the input file
			LOGGER.info("Skipping Task {} - restarting from position {}", task.getSummary(), restartPosition);
			return true;
		} else if (restartFromTask != NOT_SET && currentTaskNum < restartFromTask) {
			//For file driven batches, we'll use the r2 restartFromTask setting
			LOGGER.info("Skipping Task {} - restarting from task {}", task.getSummary(), restartFromTask);
			return true;
		} else if (task.size() > (taskSize + wiggleRoom)) {
			LOGGER.warn("{} contains {} concepts", task, task.size());
		}
		return false;
	}

	protected void onNewTask(Task task) {
		// Override to do some processing for each new task;
	}

	private void processComponent(Task task, Component component, int conceptInTask, String xOfY) throws TermServerScriptException {
		try {
			if (!dryRun && task.getComponents().indexOf(component) != 0) {
				Thread.sleep(conceptThrottle * 1000L);
			}
			String info = " Task (" + xOfY + ") Concept (" + conceptInTask + " of " + task.getComponents().size() + ")";

			int changesMade = 0;
			if (worksWithConcepts) {
				changesMade = doFix(task, (Concept) component, info);
			} else {
				changesMade = doFix(task, component, info);
			}
			if (changesMade == 0 && reportNoChange) {
				report(task, component, Severity.MEDIUM, ReportActionType.NO_CHANGE, "");
			}
			incrementSummaryInformation("Total changes made", changesMade);
		} catch (ValidationFailure f) {
			if (++validationCount > maxFailures) {
				LOGGER.warn("Validation failures now {}", validationCount);
			}
			report(f);
		} catch (InterruptedException | TermServerScriptException e) {
			report(task, component, Severity.CRITICAL, ReportActionType.API_ERROR, getMessage(e));
			LOGGER.error("Critical issues encountered {} / {}. Failed to process {}, c", component, failureCount, maxFailures, e);
			if (++failureCount >= maxFailures) {
				throw new TermServerScriptException("Failure count exceeded " + maxFailures, e);
			}
			if (e instanceof InterruptedException) {
				Thread.currentThread().interrupt();
			}
		}
	}

	protected void report(ValidationFailure f) throws TermServerScriptException {
		report(f.getTask(), f.getConcept(), f.getSeverity(), f.getReportActionType(), f.getMessage());
	}

	private void populateEditPanel(Task task) throws TermServerScriptException {
		//Prefill the Edit Panel
		try {
			if (task.size() < 25) {
				if (populateEditPanel) {
					scaClient.setEditPanelUIState(project.getKey(), task.getKey(), task.toQuotedList());
				}
				scaClient.setSavedListUIState(project.getKey(), task.getKey(), convertToSavedListJson(task));
			} else if (populateEditPanel) {
				LOGGER.debug("Unable to populate edit panel due to high task size: {}", task.size());
			}
		} catch (Exception e) {
			String msg = "Failed to preload edit-panel ui state: " + e.getMessage();
			LOGGER.warn(msg);
			report(task, null, Severity.LOW, ReportActionType.API_ERROR, msg);
		}
	}

	protected void updateTask(Task task, String reportName, String reportURL) throws Exception {
		String taskDescription = DEFAULT_TASK_DESCRIPTION;
		if (this instanceof BatchImport batchImport) {
			taskDescription = batchImport.getAllNotes(task);
		} else if (task.getComponents().size() <= MAX_CONCEPTS_IN_TASK_DESCRIPTION) {
			taskDescription = populateTaskDescription ? task.getDescriptionHTML() : DEFAULT_TASK_DESCRIPTION;
		}

		if (reportName != null) {
			String link = reportName;
			if (reportURL != null) {
				link = "<a href=\"" + reportURL + "\" target=\"_blank\">" + link + "</a>";
			}
			String separator = populateTaskDescription ? " as per " : ": ";
			taskDescription += separator + link;
		}

		//Reassign the task to the intended author.  Set at task or processing level
		String reviewMsg = task.getReviewer() == null ? "" : " into review for " + task.getReviewer();
		LOGGER.debug("Assigning {} to {}{}", task, task.getAssignedAuthor(),reviewMsg);
		scaClient.updateTask(project.getKey(), task.getKey(), null, taskDescription, task.getAssignedAuthor(), task.getReviewer());
	}

	//Override if working with Refsets or Descriptions directly
	protected int doFix(Task task, Component component, String info) throws TermServerScriptException {
		throw new NotImplementedException("Override doFix in the Concrete Class - use components when working with refset members and descriptions directly.");
	}

	protected int ensureDefinitionStatus(Task t, Concept c, DefinitionStatus targetDefStat) throws TermServerScriptException {
		int changesMade = 0;
		if (!c.getDefinitionStatus().equals(targetDefStat)) {
			report(t, c, Severity.MEDIUM, ReportActionType.CONCEPT_CHANGE_MADE, "Definition status changed to " + targetDefStat);
			c.setDefinitionStatus(targetDefStat);
			changesMade++;
		}
		return changesMade;
	}

	@Override
	protected void init(JobRun jobRun) throws TermServerScriptException {
		super.init(jobRun);
		if (jobRun.getParamValue(DRY_RUN) != null) {
			TermServerScript.setDryRun(!jobRun.getParamValue(DRY_RUN).equals("N"));
		}
		if (jobRun.getParamValue(CONCEPTS_PER_TASK) != null) {
			taskSize = Integer.parseInt(jobRun.getParamValue(CONCEPTS_PER_TASK));
		}
		if (jobRun.getParamValue(RESTART_FROM_TASK) != null) {
			restartFromTask = Integer.parseInt(jobRun.getParamValue(RESTART_FROM_TASK));
		}
		authors = List.of(jobRun.getUser());
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		if (args.length < 3) {
			print("Usage: java <FixClass> [-a author][-a2 reviewer ][-n <taskSize>] [-r <restart position in file>] [-r2 <restart from task #>] [-l <limit> ] [-t taskCreationDelay] -c <authenticatedCookie> [-d <Y/N>] [-p <projectName>] -f <batch file Location>");
			print(" d - dry run");
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
			} else if (isAuthor) {
				authors = Arrays.asList(thisArg.toLowerCase().split(","));
				isAuthor = false;
			} else if (isReviewer) {
				reviewers = Arrays.asList(thisArg.toLowerCase().split(","));
				isReviewer = false;
			} else if (isTaskSize) {
				taskSize = Integer.parseInt(thisArg);
				isTaskSize = false;
			} else if (isLimit) {
				processingLimit = Integer.parseInt(thisArg);
				LOGGER.info("Limiting number of tasks being created to {}", processingLimit);
				isLimit = false;
			}
		}

		//For batch fixes we generally need to know if the components we're modifying have been
		//released or not, so set this by default
		getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);

		try {
			super.init(args);
		} catch (Exception e) {
			throw new TermServerScriptException("Unable to initialise batch fix", e);
		}


		if (!selfDetermining && getInputFile() == null) {
			if (jobRun != null && jobRun.getParamValue(INPUT_FILE) != null) {
				inputFiles.add(0, new File(jobRun.getParamValue(INPUT_FILE)));
			} else {
				LOGGER.warn("No valid batch import file detected in command line arguments, assuming self determining");
				selfDetermining = true;
			}
		}

		if (!selfDetermining) {
			LOGGER.info("Reading file from line {} - {}", restartPosition, getInputFile().getName());
		}

		taskHelper = new TaskHelper(this, taskThrottle, populateTaskDescription, taskPrefix);
	}

	@Override
	protected void checkSettingsWithUser(JobRun jobRun) throws TermServerScriptException {
		super.checkSettingsWithUser(jobRun);

		if (jobRun != null && jobRun.getParamValue(CONCEPTS_PER_TASK) != null) {
			taskSize = Integer.parseInt(jobRun.getParamValue(CONCEPTS_PER_TASK));
		}

		print("Number of concepts per task [" + taskSize + "]: ");
		String response = STDIN.nextLine().trim();
		if (!response.isEmpty()) {
			taskSize = Integer.parseInt(response);
		}

		if (taskThrottle > 0) {
			print("Time delay between tasks (throttle) seconds [" + taskThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				taskThrottle = Integer.parseInt(response);
			}
		}

		if (conceptThrottle > 0) {
			print("Time delay between concepts (throttle) seconds [" + conceptThrottle + "]: ");
			response = STDIN.nextLine().trim();
			if (!response.isEmpty()) {
				conceptThrottle = Integer.parseInt(response);
			}
		}

		if (authors == null) {
			if (jobRun != null && jobRun.getParamValue(AUTHOR) != null) {
				setAuthors(jobRun.getParamValue(AUTHOR));
			} else {
				throw new TermServerScriptException("No target author detected in command line arguments");
			}
		}

		LOGGER.info("Batching {} concepts per task", taskSize);
	}

	public void setAuthors(String authorsStr) {
		authors = Arrays.asList(authorsStr.split(","));
	}

	protected int ensureAcceptableParent(Task task, Concept c, Concept acceptableParent) throws TermServerScriptException {
		Set<Relationship> statedParents = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
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
	 * Validate that that any attribute with that attribute type is a descendant of the target Value
	 *
	 * @param cardinality
	 * @throws TermServerScriptException
	 */
	protected int validateAttributeValues(Task task, Concept concept,
										  Concept attributeType, Concept descendantsOfValue, CardinalityExpressions cardinality) throws TermServerScriptException {

		Set<Relationship> attributes = concept.getRelationships(CharacteristicType.ALL, attributeType, ActiveState.ACTIVE);
		Set<Concept> descendants = ClosureCache.getClosureCache().getClosure(descendantsOfValue);
		for (Relationship thisAttribute : attributes) {
			Concept value = thisAttribute.getTarget();
			if (!descendants.contains(value)) {
				Severity severity = thisAttribute.isActiveSafely() ? Severity.CRITICAL : Severity.LOW;
				String activeStr = thisAttribute.isActiveSafely() ? "" : "inactive ";
				String relType = thisAttribute.getCharacteristicType().equals(CharacteristicType.STATED_RELATIONSHIP) ? "stated " : "inferred ";
				String msg = "Attribute has " + activeStr + relType + "target which is not a descendant of: " + descendantsOfValue;
				report(task, concept, severity, ReportActionType.VALIDATION_ERROR, msg);
			}
		}

		int changesMade = 0;

		//Now check cardinality on active stated relationships
		attributes = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		String msg = null;
		switch (cardinality) {
			case EXACTLY_ONE:
				if (attributes.size() != 1) {
					msg = "Concept has " + attributes.size() + " active stated attributes of type " + attributeType + " expected exactly one.";
				}
				break;
			case AT_LEAST_ONE:
				if (attributes.isEmpty()) {
					msg = "Concept has 0 active stated attributes of type " + attributeType + " expected one or more.";
				}
				break;
		}

		//If we have no stated attributes of the expected type, attempt to pull from the inferred ones
		if (attributes.isEmpty()) {
			changesMade = transferInferredRelationshipsToStated(task, concept, attributeType, cardinality);
		}

		//If we have an error message but have not made any changes to the concept, then report that error now
		if (msg != null && changesMade == 0) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
		}
		return changesMade;
	}


	private int transferInferredRelationshipsToStated(Task task,
													  Concept concept, Concept attributeType, CardinalityExpressions cardinality) throws TermServerScriptException {
		Set<Relationship> replacements = concept.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, attributeType, ActiveState.ACTIVE);
		int changesMade = 0;
		if (replacements.isEmpty()) {
			String msg = "Unable to find any inferred " + attributeType + " relationships to state.";
			report(task, concept, Severity.HIGH, ReportActionType.INFO, msg);
		} else if (cardinality.equals(CardinalityExpressions.EXACTLY_ONE) && replacements.size() > 1) {
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

	protected int replaceParents(Task t, Concept c, Set<Concept> newParents) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> newParentRels = newParents.stream()
				.map(p -> new Relationship(c, IS_A, p, UNGROUPED))
				.collect(Collectors.toSet());

		Set<Relationship> currentParentRels = c.getParents(CharacteristicType.STATED_RELATIONSHIP).stream()
				.map(p -> new Relationship(c, IS_A, p, UNGROUPED))
				.collect(Collectors.toSet());

		for (Relationship currentParentRel : currentParentRels) {
			//Is this a relationship we want to keep?
			if (!newParentRels.contains(currentParentRel)) {
				removeRelationship(t, c, currentParentRel);
				changesMade++;
			}
		}

		for (Relationship newParentRel : newParentRels) {
			//Is this a relationship we already have?
			if (!currentParentRels.contains(newParentRel)) {
				addRelationship(t, c, newParentRel);
				changesMade++;
			}
		}
		return changesMade;
	}

	//This method is similar to the previous one but we'll be explicit about which parents we'll remove and 
	//also check to see if a more specific version of the parents to add might already exist, 
	//or remove one that is less specific.
	protected int replaceParents(Task t, Concept c, Set<Concept> removeParents, Set<Concept> addParents) throws TermServerScriptException {
		int changesMade = 0;
		AncestorsCache aCache = gl.getAncestorsCache();
		Set<Concept> newParentAncestors = addParents.stream()
				.map(aCache::getAncestorsSafely)
				.flatMap(Collection::stream)
				.collect(Collectors.toSet());

		Set<Relationship> newParentRels = addParents.stream()
				.map(p -> new Relationship(c, IS_A, p, UNGROUPED))
				.collect(Collectors.toSet());

		Set<Relationship> currentParentRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);

		for (Relationship currentParentRel : currentParentRels) {
			//If this relationships is on our list of removals
			//OR if it's less specific than one we're going to add
			//then remove it.
			Concept currentParent = currentParentRel.getTarget();
			if (removeParents.contains(currentParent) || newParentAncestors.contains(currentParent)) {
				removeRelationship(t, c, currentParentRel);
				changesMade++;
			}
		}

		//Re-recover this list in case it has been modified
		currentParentRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);

		nextNewParentRel:
		for (Relationship newParentRel : newParentRels) {
			//Don't add new parent if we already have this parent, or something more specific 
			//ie a descendant.  Check if our row is in the list of ancestors (or self) of an existing parent
			Concept newParent = newParentRel.getTarget();
			for (Relationship currentParentRel : currentParentRels) {
				Concept currentParent = currentParentRel.getTarget();
				Set<Concept> skipList = aCache.getAncestorsOrSelf(currentParent);
				if (skipList.contains(newParent)) {
					report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Skipped addition of " + newParent + " due to existing " + currentParent);
					continue nextNewParentRel;
				}
			}
			addRelationship(t, c, newParentRel);
			changesMade++;
		}
		return changesMade;
	}

	protected int replaceParents(Task t, Concept c, Concept newParent) throws TermServerScriptException {
		Relationship oldParentRel = null;
		Relationship newParentRel = new Relationship(c, IS_A, newParent, UNGROUPED);
		return replaceParents(t, c, oldParentRel, newParentRel, null);
	}

	protected int replaceParent(Task t, Concept c, Concept oldParent, Concept newParent) throws TermServerScriptException {
		Relationship oldParentRel = oldParent == null ? null : new Relationship(c, IS_A, oldParent, UNGROUPED);
		Relationship newParentRel = new Relationship(c, IS_A, newParent, UNGROUPED);
		return replaceParents(t, c, oldParentRel, newParentRel, null);
	}

	protected int replaceParents(Task t, Concept c, Relationship newParentRel, Object[] additionalDetails) throws TermServerScriptException {
		Relationship oldParentRel = null;
		return replaceParents(t, c, oldParentRel, newParentRel, additionalDetails);
	}

	protected int replaceParents(Task t, Concept c, Relationship oldParentRel, Relationship newParentRel, Object[] additionalDetails) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> parentRels = new HashSet<>(c.getRelationships(CharacteristicType.STATED_RELATIONSHIP,
				IS_A,
				ActiveState.ACTIVE));
		for (Relationship parentRel : parentRels) {
			//Ignore axiom in these checks since the replacement relationship won't know about them.
			if ((oldParentRel == null ||
					parentRel.equals(oldParentRel, true)) && !parentRel.equals(newParentRel, true)) {
				changesMade += removeParentRelationship(t, parentRel, c, newParentRel.getTarget().toString(), additionalDetails);
			}
		}

		changesMade += addRelationship(t, c, newParentRel);
		return changesMade;
	}

	protected int removeParentRelationship(Task t, Relationship removeMe, Concept c, String retained, Object[] additionalDetails) throws TermServerScriptException {
		//Does this concept in fact have this relationship to remove?
		Set<Relationship> matchingRels = c.getRelationships(removeMe, ActiveState.ACTIVE);
		if (matchingRels.size() > 1) {
			throw new IllegalStateException(c + " has multiple parent relationships to " + removeMe.getTarget());
		} else if (matchingRels.isEmpty()) {
			return NO_CHANGES_MADE;
		}
		Relationship r = matchingRels.iterator().next();

		//Are we inactivating or deleting this relationship?
		String msg;
		ReportActionType action;
		if (!r.isReleasedSafely()) {
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

		report(t, c, Severity.LOW, action, msg, c.getDefinitionStatus().toString(), additionalDetails);
		return CHANGE_MADE;
	}

	protected int removeRelationships(Task t, Concept c, Concept type, int groupId) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, groupId)) {
			changesMade += removeRelationship(t, c, r);
		}
		return changesMade;
	}


	protected void removeDescription(Task t, Concept c, Description d, InactivationIndicator i) throws TermServerScriptException {
		//Are we inactivating or deleting this relationship?
		if (!d.isReleasedSafely()) {
			c.removeDescription(d);
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_DELETED, d);
		} else {
			d.setEffectiveTime(null);
			d.setActive(false);
			d.setInactivationIndicator(i);
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, d, i.toString());
		}
	}

	protected Description addDescription(Task t, Concept c, Description d) throws TermServerScriptException {
		return addDescription(t, c, d, true);
	}

	protected Description addDescription(Task t, Concept c, Description d, boolean alertIfAlreadyPresent) throws TermServerScriptException {
		Description reuseMe = c.findTerm(d.getTerm());
		if (reuseMe != null) {
			if (reuseMe.isActiveSafely()) {
				if (alertIfAlreadyPresent) {
					report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Replacement term already exists active: " + reuseMe);
				}
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Replacement term already exists inactive.  Reactivating: " + reuseMe);
				reuseMe.setActive(true);
				reuseMe.setInactivationIndicator(null);
			}
			//And copy the acceptability from the one we're replacing
			reuseMe.setAcceptabilityMap(SnomedUtils.mergeAcceptabilityMap(d, reuseMe));
		} else {
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, d);
			c.addDescription(d);
		}
		return reuseMe == null ? d : reuseMe;
	}

	protected Description replaceDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator) throws TermServerScriptException {
		//Do not perform a demotion by default
		return replaceDescription(t, c, d, newTerm, indicator, false, null, null);
	}

	protected Description replaceDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator, String info) throws TermServerScriptException {
		//Do not perform a demotion by default
		return replaceDescription(t, c, d, newTerm, indicator, false, info, null);
	}

	protected Description replaceDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator, boolean demotePT) throws TermServerScriptException {
		return replaceDescription(t, c, d, newTerm, indicator, demotePT, "", null);
	}

	protected Description replaceDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator, boolean demotePT, String info) throws TermServerScriptException {
		//Do not perform a demotion by default
		return replaceDescription(t, c, d, newTerm, indicator, demotePT, info, null);
	}

	protected Description replaceDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator, String info, CaseSignificance cs) throws TermServerScriptException {
		//Do not perform a demotion by default
		return replaceDescription(t, c, d, newTerm, indicator, false, info, cs);
	}

	protected Description replaceDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator, boolean demotePT, String info, CaseSignificance cs) throws TermServerScriptException {
		Description replacement = null;
		Description reuseMe = c.findTerm(newTerm);

		//We'll recover an original copy of the description so that we see the original name in the report
		Concept origConcept = gl.getConcept(c.getId());

		if (reuseMe != null) {
			if (reuseMe.isActiveSafely()) {
				if (d.isPreferred()) {
					promoteExistingTermToPT(t, origConcept, d, reuseMe, info);
					if (demotePT) {
						return reuseMe;
					}
				} else {
					report(t, origConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Replacement term already exists active", reuseMe, info);
					//Have we in fact asked for no change? Return unchanged if so
					if (d.equals(reuseMe)) {
						return reuseMe;
					}
				}
			} else {
				report(t, origConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Replacement term already exists inactive.  Reactivating", reuseMe, info);
				reuseMe.setActive(true);
				reuseMe.setInactivationIndicator(null);
			}
			//And copy the acceptability from the one we're replacing
			reuseMe.setAcceptabilityMap(SnomedUtils.mergeAcceptabilityMap(d, reuseMe));
		} else {
			replacement = d.clone(null); //Includes acceptability and case significance
			//Unless we're overriding the CaseSignificance
			if (cs != null) {
				replacement.setCaseSignificance(cs);
			}
			//Now if the description we're replacing has us/gb variance but the replacement does not have us/gb specific
			//words, then we can level up the acceptability.
			DialectChecker dialectChecker = DialectChecker.create();
			if (SnomedUtils.hasUsGbVariance(d) && !dialectChecker.containsUSGBSpecificTerm(newTerm)) {
				SnomedUtils.levelUpAcceptability(replacement);
			}
			replacement.setTerm(newTerm);
			c.addDescription(replacement);
			report(t, origConcept, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, replacement, info);
		}

		//Are we deleting, demoting or inactivating this term?
		if (d != null) {
			if (d.isPreferred() && demotePT && d.getType().equals(DescriptionType.SYNONYM)) {
				SnomedUtils.demoteAcceptabilityMap(d);
				report(t, origConcept, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, "Existing PT demoted to acceptable", d, info);
			} else {
				removeDescription(t, c, d, newTerm, indicator);
			}
		}
		return replacement == null ? reuseMe : replacement;  //WATCH THAT THE CALLING CODE IS RESPONSIBLE FOR CHECKING THE CASE SIGNIFICANCE - copied from original
	}

	private void promoteExistingTermToPT(Task t, Concept c, Description d, Description reuseMe, String info) throws TermServerScriptException {
		//If d is preferred then if we're demoting the PT then just swap the acceptability
		//However, if we're moving to a us+gb term and the old PT is us or gb specific, then
		//we need to keep P + P and for the old term turn just P to A
		if (SnomedUtils.hasUsGbVariance(d) && !SnomedUtils.hasUsGbVariance(reuseMe)) {
			if (!reuseMe.isPreferred()) {
				reuseMe.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_BOTH));
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, "Replacement term already exists, promoting", reuseMe, info);
			}
			SnomedUtils.demoteAcceptabilityMap(d);
			report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, "Replacement term already exists, existing term demoted", d, info);
		} else {
			Map<String, Acceptability> ptAcceptMap = d.getAcceptabilityMap();
			d.setAcceptabilityMap(reuseMe.getAcceptabilityMap());
			reuseMe.setAcceptabilityMap(ptAcceptMap);
			report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_ACCEPTABILIY_CHANGED, "Replacement term already exists, swapping acceptability", reuseMe, info);
		}
	}

	protected void removeDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator) throws TermServerScriptException {
		removeDescription(t, c, d, newTerm, indicator, "");
	}

	protected void removeDescription(Task t, Concept c, Description d, String newTerm, InactivationIndicator indicator, String info) throws TermServerScriptException {
		String change;
		ReportActionType action;
		String before = d.toString();
		if (d.isReleasedSafely()) {
			d.setActive(false);
			d.setInactivationIndicator(indicator);
			d.setAcceptabilityMap(new HashMap<>());
			change = "Inactivated";
			action = ReportActionType.DESCRIPTION_INACTIVATED;
		} else {
			c.removeDescription(d);
			change = "Deleted";
			action = ReportActionType.DESCRIPTION_DELETED;
		}
		String msg = change + " " + before;
		String msg2 = (newTerm == null ? "" : "Replaced with: " + newTerm);
		report(t, c, Severity.LOW, action, msg, msg2, info);
	}

	protected int addRelationship(Task t, Concept c, IRelationship r, int groupId) throws TermServerScriptException {
		return replaceRelationship(t, c, r.getType(), r.getTarget(), groupId, RelationshipTemplate.Mode.UNIQUE_TYPE_ACROSS_ALL_GROUPS);  //Allow other relationships of the same type, but not value
	}

	protected int addRelationshipGroup(Task t, Concept c, RelationshipGroupTemplate group, int groupId) throws TermServerScriptException {
		int changesMade = 0;
		for (RelationshipTemplate r : group.getRelationships()) {
			changesMade += replaceRelationship(t, c, r.getType(), r.getTarget(), groupId, r.getMode());
		}
		return changesMade;
	}

	protected int addRelationship(Task t, Concept c, Relationship r, RelationshipTemplate.Mode mode) throws TermServerScriptException {
		return replaceRelationship(t, c, r.getType(), r.getTarget(), r.getGroupId(), mode);
	}

	protected int addRelationship(Task t, Concept c, Relationship r) throws TermServerScriptException {
		return replaceRelationship(t, c, r.getType(), r.getTarget(), r.getGroupId(), RelationshipTemplate.Mode.UNIQUE_TYPE_VALUE_ACROSS_ALL_GROUPS); //Allow other relationships of the same type, but not typeValue
	}

	protected int replaceRelationship(Task t, Concept c, Concept type, Concept value, int groupId, boolean ensureTypeUnique) throws TermServerScriptException {
		RelationshipTemplate.Mode mode = ensureTypeUnique ? Mode.UNIQUE_TYPE_ACROSS_ALL_GROUPS : Mode.UNIQUE_TYPE_IN_THIS_GROUP;
		return replaceRelationship(t, c, type, value, groupId, mode);
	}


	protected int replaceRelationship(Task t, Concept c, IRelationship from, IRelationship to) throws TermServerScriptException {
		int changesMade = 0;

		//Do we have any inactive relationships that we could reactivate, rather than creating new ones?
		Set<Relationship> inactiveRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, to.getType(), to.getTarget(), ActiveState.INACTIVE);

		Set<Relationship> originalRels;
		//If our 'from' is a full relationship, then only replace within that groupId
		if (from instanceof Relationship) {
			originalRels = c.getRelationships((Relationship) from, ActiveState.ACTIVE);
		} else {
			originalRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, from.getType(), from.getTarget(), ActiveState.ACTIVE);
		}

		for (Relationship r : originalRels) {
			if (!inactiveRels.isEmpty()) {
				//We'll reuse and reactivate this row, rather than create a new one
				//Do we have one with the same group, if not, use any
				Relationship bestReactivation = getBestReactivation(inactiveRels, r.getGroupId());
				bestReactivation.setActive(true);
				bestReactivation.setGroupId(r.getGroupId());
				inactiveRels.remove(bestReactivation);
				changesMade++;
				report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_REACTIVATED, bestReactivation);
			} else {
				Relationship newRel = r.clone();
				newRel.setType(to.getType());
				newRel.setTarget(to.getTarget());
				c.addRelationship(newRel);
				changesMade++;
				report(t, c, Severity.LOW, ReportActionType.RELATIONSHIP_ADDED, newRel);
			}

			removeRelationship(t, c, r);
			changesMade++;
		}
		return changesMade;
	}

	private Relationship getBestReactivation(Set<Relationship> rels, int targetGroupId) {
		for (Relationship r : rels) {
			if (r.getGroupId() == targetGroupId) {
				return r;
			}
		}
		//If not found in the same group, return the first one
		return rels.iterator().next();
	}

	protected void removeRedundancy(Task t, Concept c, Concept type, int groupNum) throws TermServerScriptException {
		//Remove any redundant attribute in the given group
		List<Concept> allValues = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, groupNum)
				.stream().map(Relationship::getTarget).toList();
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
				if (!thisConcept.equals(otherConcept) && gl.getAncestorsCache().getAncestors(otherConcept).contains(thisConcept)) {
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
		String trimmedFSN = SnomedUtilsBase.deconstructFSN(concept.getFsn())[0];
		//Special handling for acetaminophen
		if (trimmedFSN.toLowerCase().contains(ACETAMINOPHEN) || trimmedFSN.toLowerCase().contains(PARACETAMOL)) {
			LOGGER.info("Doing ACETAMINOPHEN processing for {}", concept);
		} else {
			for (Description pref : preferredTerms) {
				if (!pref.getTerm().equals(trimmedFSN)) {
					String msg = concept + " has preferred term that does not match FSN: " + pref.getTerm();
					report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, msg);
				}
			}
		}
	}

	protected void sendEmail(String content, File resultsFile) {
		Properties props = new Properties();
		props.put("mail.transport.protocol", emailDetails[0]);
		props.put("mail.smtp.host", emailDetails[1]); // smtp.gmail.com?
		props.put("mail.smtp.port", emailDetails[2]);
		props.put("mail.smtp.auth", "true");
		Authenticator authenticator = new Authenticator() {
			@Override
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
			LOGGER.info("Failed to send email {}", e.getMessage());
		}
	}

	private JSONObject convertToSavedListJson(Task task) throws JSONException {
		JSONObject savedList = new JSONObject();
		JSONArray items = new JSONArray();
		savedList.put("items", items);
		for (Component c : task.getComponents()) {
			items.put(convertToSavedListJson((Concept) c));
		}
		return savedList;
	}

	private JSONObject convertToSavedListJson(Concept concept) throws JSONException {
		JSONObject jsonObj = new JSONObject();
		jsonObj.put("term", concept.getPreferredSynonym());
		jsonObj.put("active", concept.isActiveSafely() ? "true" : "false");
		JSONObject conceptObj = new JSONObject();
		jsonObj.put("concept", conceptObj);
		conceptObj.put("active", concept.isActiveSafely() ? "true" : "false");
		conceptObj.put("conceptId", concept.getConceptId());
		conceptObj.put("fsn", concept.getFsn());
		conceptObj.put("moduleId", concept.getModuleId());
		conceptObj.put("defintionStatus", concept.getDefinitionStatus());
		return jsonObj;
	}

	/**
	 * Takes a remodelled concept and saves it as a new concept, inactivates tbe original
	 * and points to it as a replacement
	 *
	 * @throws TermServerScriptException
	 */
	protected void cloneAndReplace(Concept concept, Task t, InactivationIndicator inactivationIndicator) throws TermServerScriptException {
		String originalId = concept.getConceptId();
		Concept original = loadConcept(originalId, t.getBranchPath());
		//Clone the clone to wipe out all identifiers, just in case it retained any.
		Concept clone = concept.clone();
		clone.setActive(true); //Just incase we've cloned an inactive concept
		Concept savedConcept = createConcept(t, clone, ", clone of " + originalId);
		report(t, savedConcept, Severity.LOW, ReportActionType.CONCEPT_ADDED, "Cloned " + original);

		//If the original has stated children, they'll all need to be re-pointed to the clone
		for (Concept child : gl.getConcept(originalId).getChildren(CharacteristicType.STATED_RELATIONSHIP)) {
			Concept loadedChild = loadConcept(child.getConceptId(), t.getBranchPath());
			int changesMade = replaceParent(t, loadedChild, original, savedConcept);
			if (changesMade > 0) {
				updateConcept(t, loadedChild, "");
				report(t, child, Severity.HIGH, ReportActionType.RELATIONSHIP_REPLACED, "New Parent: " + savedConcept);
				//Add the child to the task, after the original
				t.addAfter(child, original);
				incrementSummaryInformation("Total children repointed to cloned concepts");
			} else {
				LOGGER.warn("Locally loaded ontology thought {} had stated child {} but TS disagreed.", concept, child);
			}
		}

		//Add our clone to the task, after the original
		t.addAfter(savedConcept, original);

		//Now inactivate the original.
		//This will inactivate all relationships and set the DefnStatus to Primitive
		original.setActive(false);
		original.setInactivationIndicator(inactivationIndicator);
		String histAssocStr;
		switch (inactivationIndicator) {
			case AMBIGUOUS:
				original.setAssociationTargets(AssociationTargets.possEquivTo(savedConcept));
				histAssocStr = "Possibly Equivalent to ";
				break;
			case OUTDATED:
				original.setAssociationTargets(AssociationTargets.replacedBy(savedConcept));
				histAssocStr = "Replaced by ";
				break;
			default:
				throw new TermServerScriptException("Unexpected inactivation indicator: " + inactivationIndicator);
		}
		report(t, original, Severity.LOW, ReportActionType.ASSOCIATION_ADDED, histAssocStr + savedConcept);

		checkAndReplaceHistoricalAssociations(t, original, savedConcept, inactivationIndicator);
		report(t, original, Severity.LOW, ReportActionType.CONCEPT_INACTIVATED);
		updateConcept(t, original, "");
	}

	protected void checkAndReplaceHistoricalAssociations(Task t, Concept inactivateMe, Concept replacing, InactivationIndicator i) throws TermServerScriptException {
		checkAndReplaceHistoricalAssociations(t, inactivateMe, Collections.singletonList(replacing), i);
	}

	protected void checkAndReplaceHistoricalAssociations(Task t, Concept inactivateMe, List<Concept> replacing, InactivationIndicator i) throws TermServerScriptException {
		List<AssociationEntry> histAssocs = gl.usedAsHistoricalAssociationTarget(inactivateMe);
		if (histAssocs != null && !histAssocs.isEmpty()) {
			if (replacing != null && replacing.size() > 1) {
				throw new IllegalArgumentException("No code support for replacing multiple historical associations");
			}
			
			Concept replacement = replacing == null ? null : replacing.get(0);
			for (AssociationEntry histAssoc : histAssocs) {
				replaceHistoricalAssociation(t, histAssoc, inactivateMe, replacement, i);
			}
		}
	}

	private void replaceHistoricalAssociation(Task t, AssociationEntry histAssoc, Concept inactivateMe, Concept replacement, InactivationIndicator i) throws TermServerScriptException {
		Concept source = gl.getConcept(histAssoc.getReferencedComponentId());
		String assocType = gl.getConcept(histAssoc.getRefsetId()).getPreferredSynonym(US_ENG_LANG_REFSET).getTerm().replace("association reference set", "");
		String thisDetail = "Concept was as used as the " + assocType + "target of a historical association for " + source;
		thisDetail += " (since " + (histAssoc.getEffectiveTime().isEmpty() ? " prospective release" : histAssoc.getEffectiveTime()) + ")";
		if (replacement == null) {
			//In this case we must load the source of this incoming assertion, remove this concept as a target and
			//if there are no associations left, set the inactivation reason to NCEP
			unpickHistoricalAssociation(t, source, inactivateMe);
		} else {
			report(t, inactivateMe, Severity.HIGH, ReportActionType.INFO, thisDetail);
			replaceHistoricalAssociation(t, source, inactivateMe, replacement, i);
		}
	}

	private void unpickHistoricalAssociation(Task t, Concept sourceCached, Concept target) throws TermServerScriptException {
		Concept source = gl.getConcept(sourceCached.getConceptId());
		AssociationTargets targets = source.getAssociationTargets();
		int removedCount = targets.remove(target.getConceptId());
		if (removedCount != 1) {
			throw new TermServerScriptException("Unexpectedly removed " + removedCount + " associations from " + source + " when looking for " + target);
		}
		if (targets.size() == 0 && !source.getInactivationIndicator().equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			source.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			report(t, source, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, "All outgoing associations removed and inactivation indicator switched to NCEP.");
		} else {
			report(t, source, Severity.HIGH, ReportActionType.ASSOCIATION_REMOVED, "No longer associated with " + target, "Remaining : " + targets.toString());
		}
		updateConcept(t, source, null);
		t.addBefore(sourceCached, target);
	}

	protected int inactivateConcept(Task t, Concept c, Concept replacement, InactivationIndicator i) throws TermServerScriptException {
		return inactivateConcept(t, c, Collections.singletonList(replacement), i, "");
	}

	protected int inactivateConcept(Task t, Concept c, List<Concept> replacements, InactivationIndicator i, String info) throws TermServerScriptException {
		ReportActionType reportAction = ReportActionType.CONCEPT_INACTIVATED;

		//If concept is already inactivated, then we'll be modifying it's inactivation reason or hist assoc
		if (!c.isActiveSafely()) {
			//Only if the concept was inactivated as Outdated will we update the inactivation indicator
			//This will need to become a list of acceptable existing inactivation indicators
			if (c.getInactivationIndicator().equals(InactivationIndicator.OUTDATED)) {
				reportAction = ReportActionType.INACT_IND_MODIFIED;
			} else {
				report(t, c, Severity.LOW, ReportActionType.NO_CHANGE, "Already inactivated as: " + c.getInactivationIndicator());
				return NO_CHANGES_MADE;
			}
		}

		//Check if the concept we're about to inactivate is used as the target of a historical association
		//and rewire that to point to our new clone
		checkAndReplaceHistoricalAssociations(t, c, replacements, i);

		c.setActive(false);
		c.setInactivationIndicator(i);

		if (replacements != null && replacements.size() > 1) {
			throw new IllegalArgumentException("No code support for replacing multiple historical assocaitions");
		}
		Concept replacement = (replacements == null ? null : replacements.get(0));

		if (replacement != null) {
			switch (i) {
				case NONCONFORMANCE_TO_EDITORIAL_POLICY:
					throw new TermServerScriptException("Cannot specify historical association for NCEP");
				case DUPLICATE:
					c.setAssociationTargets(AssociationTargets.sameAs(replacement));
					break;
				case OUTDATED:
					c.setAssociationTargets(AssociationTargets.replacedBy(replacement));
					break;
				case AMBIGUOUS:
					c.setAssociationTargets(AssociationTargets.possEquivTo(replacement));
					break;
				default:
					throw new IllegalArgumentException("No code support for inactivation indicator: " + i);
			}

		} else {
			//Remove any existing association targets
			if (c.getAssociationTargets().size() > 0) {
				report(t, c, Severity.MEDIUM, ReportActionType.ASSOCIATION_REMOVED, c.getAssociationTargets().toString(gl));
			}
			c.setAssociationTargets(new AssociationTargets());
		}
		//Inactivated concepts must necessarily be primitive
		c.setDefinitionStatus(DefinitionStatus.PRIMITIVE);

		//Need to also remove any unpublished relationships
		Set<Relationship> allRelationships = new HashSet<>(c.getRelationships());
		for (Relationship r : allRelationships) {
			if (r.isActiveSafely() && (r.getEffectiveTime() == null || r.getEffectiveTime().isEmpty())) {
				c.removeRelationship(r);
			}
		}
		report(t, c, Severity.LOW, reportAction, info);
		return CHANGE_MADE;
	}

	protected void replaceHistoricalAssociation(Task t, Concept concept, Concept current, Concept replacement, InactivationIndicator inactivationIndicator) throws TermServerScriptException {
		//We need a copy from the TS
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		loadedConcept.setInactivationIndicator(inactivationIndicator);
		//Make sure we only have one current association target
		int targetCount = loadedConcept.getAssociationTargets().size();
		if (targetCount > 1) {
			report(t, concept, Severity.HIGH, ReportActionType.INFO, "Replacing 1 historical association out of " + targetCount);
		}
		AssociationTargets targets = loadedConcept.getAssociationTargets();
		targets.remove(current.getConceptId());

		String histAssocStr;
		switch (inactivationIndicator) {
			case AMBIGUOUS:
				targets.getPossEquivTo().add(replacement.getConceptId());
				histAssocStr = "possibly equivalent to ";
				break;
			case OUTDATED:
				targets.getReplacedBy().add(replacement.getConceptId());
				histAssocStr = "replaced by ";
				break;
			default:
				throw new TermServerScriptException("Unexpected inactivation indicator: " + inactivationIndicator);
		}
		updateConcept(t, loadedConcept, " with re-jigged inactivation indicator and historical associations");
		report(t, loadedConcept, Severity.HIGH, ReportActionType.ASSOCIATION_ADDED, "InactReason set to " + inactivationIndicator + " and " + histAssocStr + replacement);
	}

	protected int checkAndSetProximalPrimitiveParent(Task t, Concept c, Concept newPPP) throws TermServerScriptException {
		return checkAndSetProximalPrimitiveParent(t, c, newPPP, false, false);
	}

	protected int checkAndSetProximalPrimitiveParent(Task t, Concept c, Concept newPPP, boolean checkOnly, boolean allowCompromise) throws TermServerScriptException {
		int changesMade = 0;

		//If we're not told the new Prox Prim Parent, then work it out from the hierarchy or semantic tag
		if (newPPP == null) {
			if (c.getFsn().contains("(disorder)")) {
				newPPP = DISEASE;
			} else {
				newPPP = SnomedUtils.getHierarchy(gl, c);
			}
		}

		if (newPPP == null) {
			report(t, c, Severity.HIGH, ReportActionType.NO_CHANGE, "Unable to determine appropriate PPP");
			return NO_CHANGES_MADE;
		}

		//Do we in fact need to make any changes here?
		Set<Concept> existingParents = c.getParents(CharacteristicType.STATED_RELATIONSHIP);
		if (existingParents.size() == 1 && existingParents.contains(newPPP)) {
			if (checkOnly) {
				report(t, c, Severity.NONE, ReportActionType.NO_CHANGE, "Single stated parent is already as required - " + newPPP);
			}
			return NO_CHANGES_MADE;
		}

		List<Concept> ppps = determineProximalPrimitiveParents(c);
		if (ppps.size() != 1) {
			String pppsStr = ppps.stream()
					.map(Object::toString)
					.collect(Collectors.joining(",\n"));
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Concept found to have " + ppps.size() + " proximal primitive parents.  Cannot state parent as: " + newPPP, pppsStr);
		} else {
			Concept ppp = ppps.iterator().next();
			//We need to either calculate the ppp as the intended one, or higher than it eg calculated PPP of Disease is OK if we're setting the more specific "Complication"
			if (allowCompromise || ppp.equals(newPPP) || gl.getAncestorsCache().getAncestors(newPPP).contains(ppp)) {
				if (allowCompromise) {
					report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Calculated PPP " + ppp + " allowed (compromise flag set).");
					newPPP = ppp;
				}

				if (!checkOnly) {
					changesMade += setProximalPrimitiveParent(t, c, newPPP);
				} else {
					//If we're just checking, then yes, we think we'd have made changes here
					return CHANGE_MADE;
				}
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Calculated PPP " + ppp + " does not match that requested: " + newPPP + ", cannot remodel.");
			}
		}
		return changesMade;
	}

	private int setProximalPrimitiveParent(Task t, Concept c, Concept newParent) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> parentRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_A, ActiveState.ACTIVE);
		//Do we in fact need to do anything?
		if (parentRels.size() == 1 && parentRels.iterator().next().getTarget().equals(newParent)) {
			report(t, c, Severity.NONE, ReportActionType.NO_CHANGE, "Concept already has template PPP: " + newParent);
		} else {
			boolean doAddition = true;
			for (Relationship r : parentRels) {
				if (r.getTarget().equals(newParent)) {
					doAddition = false;
				} else {
					//We can only remove relationships which are subsumed by the new Proximal Primitive Parent
					//OR if the current parent is a supertype of the PPP, such as when we're moving from Disease to Complication.
					//OR if the other parent itself is sufficiently defined and its PPP is a supertype of the concepts PPP eg Clinical Finding
					Concept thisParent = gl.getConcept(r.getTarget().getConceptId());
					if (gl.getAncestorsCache().getAncestors(thisParent).contains(newParent) ||
							gl.getAncestorsCache().getAncestors(newParent).contains(thisParent)) {
						removeParentRelationship(t, r, c, newParent.toString(), null);
						changesMade++;
					} else if (parentRelationshipRedundantToPPP(thisParent, newParent)) {
						report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Ancestry of exist SD parent " + thisParent + " sufficiently defined up to a primitive *above* the new parent: " + newParent + " so safe to remove.");
						removeParentRelationship(t, r, c, newParent.toString(), null);
						changesMade++;
					} else {
						report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Unable to remove parent " + thisParent + " because it it not subsumed by " + newParent);
					}
				}
			}

			if (doAddition) {
				Relationship newParentRel = new Relationship(c, IS_A, newParent, 0);
				changesMade += addRelationship(t, c, newParentRel);
			}
		}
		return changesMade;
	}

	private boolean parentRelationshipRedundantToPPP(Concept existingParent, Concept newParent) throws TermServerScriptException {
		AncestorsCache cache = gl.getAncestorsCache();
		//Is the existing parent's ancestry sufficiently defined to a point above the new parent?
		boolean isRedundant = false;
		if (existingParent.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			List<Concept> ppps = determineProximalPrimitiveParents(existingParent);
			for (Concept thisPPP : ppps) {
				if (cache.getAncestors(newParent).contains(thisPPP)) {
					isRedundant = true;
				} else {
					return false;
				}
			}
		}
		return isRedundant;
	}

	public int applyRemodelledGroups(Task t, Concept c, List<RelationshipGroup> groups) throws TermServerScriptException {
		int changesMade = 0;
		Set<Relationship> availableForReuse = new HashSet<>();
		Set<String> idsUsed = new HashSet<>();
		for (RelationshipGroup group : groups) {
			if (group != null) {

				//Do we need to retire any existing relationships?
				for (Relationship potentialRemoval : c.getRelationshipGroupSafely(CharacteristicType.STATED_RELATIONSHIP, group.getGroupId()).getRelationships()) {
					if (!group.getRelationships().contains(potentialRemoval)) {
						LOGGER.warn("Removing {} from {}", potentialRemoval, c);
						availableForReuse.add(potentialRemoval);
						removeRelationship(t, c, potentialRemoval);
						changesMade++;
					}
				}

				//If we've used the same relationship twice, the 2nd instance should have a new SCTID
				for (Relationship r : group.getRelationships()) {
					if (idsUsed.contains(r.getId())) {
						LOGGER.warn("Multiple use of: {}", r);
						r.setRelationshipId(null);
					} else if (r.getId() != null && !r.getId().isEmpty()) {
						idsUsed.add(r.getId());
					}
				}

				changesMade += c.addRelationshipGroup(group, availableForReuse);
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems)
			throws TermServerScriptException {
		//Default implementation is to take the first column and try that as an SCTID
		//Override for more complex implementation
		if (correctRoundedSCTIDs) {
			lineItems[0] = SnomedUtils.correctRoundedCheckDigit(lineItems[0]);
		}
		return Collections.singletonList(gl.getConcept(lineItems[0]));
	}

	public void setStandardParameters(JobParameters param) {

	}

	public void recoverStandardParameter(JobRun run) {

	}

	public List<Concept> getAllComponentsToProcess() {
		return asConcepts(allComponentsToProcess);
	}

	protected void inactivateHistoricalAssociation(Task t, AssociationEntry assoc, Concept inactivatingConcept, InactivationIndicator reason, Set<Concept> replacements) throws TermServerScriptException {
		//The source concept can no longer have this historical association, and its
		//inactivation reason must also change.
		Concept originalTarget = gl.getConcept(assoc.getTargetComponentId());
		Concept incomingConcept = loadConcept(assoc.getReferencedComponentId(), t.getBranchPath());
		String assocType = "Unknown";
		incomingConcept.setInactivationIndicator(reason);
		if ((replacements == null || replacements.isEmpty()) &&
				!reason.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			throw new ValidationFailure(t, inactivatingConcept, "Hist Assoc rewiring attempted wtih no replacement offered.");
		}
		replacements = new HashSet<>(replacements);

		if (reason.equals(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY)) {
			incomingConcept.setAssociationTargets(null);
		} else if (reason.equals(InactivationIndicator.OUTDATED)) {
			throw new NotImplementedException();
			//incomingConcept.setAssociationTargets(AssociationTargets.replacedBy(replacements));
		} else if (reason.equals(InactivationIndicator.DUPLICATE)) {
			assocType = "SameAs";
			if (replacements.size() != 1) {
				throw new IllegalArgumentException("Attempted to set SameAs with " + replacements.size() + " replacments.  Must be 1.");
			}
			incomingConcept.setAssociationTargets(AssociationTargets.sameAs(replacements.iterator().next()));
		} else if (reason.equals(InactivationIndicator.AMBIGUOUS)) {
			//Have we seen this concept before...in this task?
			Set<Concept> newAssocs = historicallyRewiredPossEquivTo.get(incomingConcept);
			if (newAssocs == null) {
				newAssocs = new HashSet<>();
			} else {
				report(t, inactivatingConcept, Severity.MEDIUM, ReportActionType.INFO, "Concept has previously rewired associations", newAssocs.toString());
			}
			replacements.addAll(newAssocs);
			assocType = "PossEquivTo";
			//There may be other potential equivalents we don't want to remove, so just take out the one we're inactivating 
			//and add in the new ones.
			histAssocUtils.modifyPossEquivAssocs(t, incomingConcept, inactivatingConcept, replacements, assoc);

			//Store the complete set away so if we see that concept again, we maintain a complete set
			historicallyRewiredPossEquivTo.put(incomingConcept, replacements);
		} else if (reason.equals(InactivationIndicator.MOVED_ELSEWHERE)) {
			//We can only move to one location
			if (replacements.size() != 1) {
				throw new IllegalArgumentException("Moved_Elsewhere expects a single MovedTo association.  Found " + replacements.size());
			}
			assocType = "MovedTo";
			Concept replacement = replacements.iterator().next();
			incomingConcept.setAssociationTargets(AssociationTargets.movedTo(replacement));
		} else {
			throw new IllegalArgumentException("Don't know what historical association to use with " + reason);
		}
		Severity severity = replacements.isEmpty() ? Severity.CRITICAL : Severity.MEDIUM;
		for (Concept replacement : replacements) {
			report(t, originalTarget, severity, ReportActionType.ASSOCIATION_CHANGED, "Historical incoming association from " + incomingConcept, " rewired as " + assocType, replacement);
		}

		//Add this concept into our task so we know it's been updated
		t.addAfter(incomingConcept, gl.getConcept(assoc.getTargetComponentId()));
		updateConcept(t, incomingConcept, "");
	}

	//Were a concept (due to be removed) is the target value of some relationship, rewire it to the 
	//replacement
	protected void rewireRelationshipsUsingTargetValue(Task t, Concept removing, Concept replacement) throws TermServerScriptException {
		nextConcept:
		for (Concept c : gl.getAllConcepts()) {
			if (c.isActiveSafely() && !c.equals(replacement)) {
				for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
					if (r.getTarget().equals(removing)) {
						Concept loadedConcept = loadConcept(c, t.getBranchPath());
						if (loadedConcept.isActiveSafely()) {
							for (Relationship rLoaded : loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
								rLoaded.setTarget(replacement);
								report(t, c, Severity.MEDIUM, ReportActionType.RELATIONSHIP_MODIFIED, r, rLoaded);
							}
							updateConcept(t, loadedConcept, null);
							t.addAfter(removing, loadedConcept);
							continue nextConcept;
						}
					}
				}
			}
		}
	}

	/**
	 * Within each group, check if there are any relationships which are entirely
	 * redundant as more specific versions of the same type/value exist
	 */
	protected int removeRedundandRelationships(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		AncestorsCache cache = gl.getAncestorsCache();
		Set<Relationship> removedRels = new HashSet<>();
		for (RelationshipGroup group : c.getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP)) {
			Set<Relationship> originalRels = group.getRelationships();
			for (Relationship originalRel : originalRels) {
				if (removedRels.contains(originalRel)) {
					continue;
				}
				for (Relationship potentialRedundancy : originalRels) {
					if (SnomedUtils.isMoreSpecific(originalRel, potentialRedundancy, cache)) {
						report(t, c, Severity.MEDIUM, ReportActionType.INFO, "Redundant relationship within group", potentialRedundancy, originalRel);
						removeRelationship(t, c, potentialRedundancy);
						removedRels.add(potentialRedundancy);
						changesMade++;
					}
				}
			}
		}
		return changesMade;
	}

	public List<String> getAuthors() {
		return authors;
	}

	public void setTaskPrefix(String taskPrefix) {
		this.taskPrefix = taskPrefix;
	}
}
