package org.ihtsdo.termserver.scripting;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.domain.Branch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TaskHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(TaskHelper.class);

	public static final String DEFAULT_TASK_DESCRIPTION = "Batch Updates - see spreadsheet for details";

	private TermServerScript ts;
	private boolean firstTaskCreated = false;
	private boolean populateTaskDescription = true;
	private int taskThrottle = 5;
	private String taskPrefix = "";
	private int dryRunCounter = 0;

	public TaskHelper(TermServerScript ts, int taskThrottle, boolean populateTaskDescription, String taskPrefix) {
		this.ts = ts;
		this.taskThrottle = taskThrottle;
		this.populateTaskDescription = populateTaskDescription;
		setTaskPrefix(taskPrefix);
	}

	public void setTaskPrefix(String taskPrefix) {
		//Add a space to the prefix if one has not been provided
		if (!StringUtils.isEmpty(taskPrefix) && !taskPrefix.endsWith(" ")) {
			taskPrefix += " ";
		}
		this.taskPrefix = taskPrefix;
	}

	public Task createTask() throws TermServerScriptException {
		Task task = new Task(null, null, null);
		task.setBranchPath(ts.getProject().getBranchPath());
		createTask(task);
		return task;
	}

	public Task createTask(Task task) throws TermServerScriptException {
		if (!ts.isDryRun()) {
			if (firstTaskCreated) {
				LOGGER.debug("Letting TS catch up - {}s nap.", taskThrottle);
				try {
					Thread.sleep(taskThrottle * 1000L);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new IllegalStateException("Task creation sleep interrupted", e);
				}
			} else {
				firstTaskCreated = true;
			}
			boolean taskCreated = false;
			int taskCreationAttempts = 0;
			while (!taskCreated) {
				taskCreated = attemptTaskCreation(task, taskCreationAttempts);
				taskCreationAttempts++;
			}
		} else {
			task.setKey(ts.getProject() + "-" + dryRunCounter++);
			//If we're running in debug mode, the task path will not exist so use the project instead
			task.setBranchPath(ts.getProject().getBranchPath());
			LOGGER.info("Dry run task creation: {}", task.getKey());
		}
		return task;
	}

	private boolean attemptTaskCreation(Task task, int taskCreationAttempts) throws TermServerScriptException {
		try {
			LOGGER.debug("Creating jira task on project: {}", ts.getProject());
			if (populateTaskDescription && task.size() > 150) {
				task.setDescription(DEFAULT_TASK_DESCRIPTION);
				if (task.size() > 150 && populateTaskDescription) {
					LOGGER.warn("Task size {}, cannot populate Jira ticket description, even though populateTaskDescription flag set to true.", task.size());
					populateTaskDescription = false;
				}
			}
			String taskSummary = task.getSummary();
			if (taskPrefix != null) {
				task.setSummary(taskPrefix + taskSummary);
			}

			LOGGER.debug("Creating task in authoring services: {}", task);
			Task createdTSTask = ts.getAuthoringServicesClient().createTask(task);
			task.setBranchPath(createdTSTask.getBranchPath());
			task.setKey(createdTSTask.getKey());
			LOGGER.debug("Creating task branch on terminology server: {}", task);
			Branch createdBranch = ts.getTSClient().createBranch(createdTSTask);
			task.setBranchPath(createdBranch.getPath());
			ts.getTSClient().setAuthorFlag(task.getBranchPath(), "batch-change", "true");
			return true;
		} catch (Exception e) {
			taskCreationAttempts++;
			try {
				ts.getAuthoringServicesClient().deleteTask(ts.getProject().getKey(), task.getKey(), true);  //Don't worry if deletion fails
			} catch (Exception e2) {
				//Don't worry about failing to delete the task, we can do that directly in JIRA
			}

			if (taskCreationAttempts >= 3) {
				throw new TermServerScriptException("Maxed out failure attempts", e);
			}
			LOGGER.error("Task creation failed, retrying...", e);
			return false;
		}
	}
}
