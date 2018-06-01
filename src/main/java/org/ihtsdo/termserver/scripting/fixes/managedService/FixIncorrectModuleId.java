package org.ihtsdo.termserver.scripting.fixes.managedService;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import us.monoid.json.JSONObject;

/*
Fix takes concepts identified in the input file and ensures that the 
moduleid of the concept, descriptions and relationships all match
the default.   Note that unlike other fixes, this does not create a 
new task, but targets existing tasks. 
 */
public class FixIncorrectModuleId extends BatchFix implements RF2Constants{
	
	Map<String, Task> knownTasks = new HashMap<String, Task>();
	Set<TaskConcept> processMe = new HashSet<TaskConcept>();
	String intendedModuleId;
	
	protected FixIncorrectModuleId(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		FixIncorrectModuleId fix = new FixIncorrectModuleId(null);
		try {
			fix.init(args);
			fix.loadEntriesToFix();
			fix.process();
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
		intendedModuleId = project.getMetadata().getDefaultModuleId();
		info ("Identified correct module to be " + intendedModuleId);
	}
	
	protected void process() {
		for (TaskConcept tc : processMe) {
			Task task = tc.t;
			Concept loadedConcept = tc.c;
			try {
				loadedConcept = loadConcept(tc.c, task.getBranchPath());
				doFix (task, loadedConcept);
			} catch (Exception e) {
				String msg = "Failed to process " + tc.c + " in task " + task + " due to " + e.getMessage();
				report (task, loadedConcept, Severity.CRITICAL, ReportActionType.API_ERROR, msg);
			}
		}
	}

	public int doFix(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = checkModuleId(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, loadedConcept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getMessage());
			}
		}
		return changesMade;
	}

	private int checkModuleId(Task task, Concept loadedConcept) {
		int changesMade = 0;
		if (!loadedConcept.getModuleId().equals(intendedModuleId)) {
			changesMade++;
			loadedConcept.setModuleId(intendedModuleId);
		}
		
		for (Description d : loadedConcept.getDescriptions()) {
			if (!d.getModuleId().equals(intendedModuleId)) {
				changesMade++;
				d.setModuleId(intendedModuleId);
			}
		}
		
		for (Relationship r : loadedConcept.getRelationships()) {
			if (!r.getModuleId().equals(intendedModuleId)) {
				changesMade++;
				r.setModuleId(intendedModuleId);
			}
		}
		String msg = changesMade + " module ids corrected to " + intendedModuleId;
		report (task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, msg);
		return changesMade;
	}

	private void loadEntriesToFix() throws IOException, SnowOwlClientException, TermServerScriptException {
		List<String> lines = Files.readLines(inputFile, Charsets.UTF_8);
		info ("Loading affected description ids from " + inputFile);
		for (String line : lines) {
			String trimmedLine = line.trim();
			if (trimmedLine.isEmpty()) {
				continue;
			}
			String[] taskConcept = trimmedLine.split(TAB);
			String taskStr = taskConcept[0];
			String conceptStr = taskConcept[1];
			Task task= knownTasks.get(taskStr);
			if (task == null) {
				task = scaClient.getTask(taskStr);
				knownTasks.put(taskStr, task);
			}
			processMe.add(new TaskConcept(task, gl.getConcept(conceptStr)));
		}
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}

	class TaskConcept {
		Task t;
		Concept c;
		
		TaskConcept (Task t, Concept c) {
			this.t = t;
			this.c = c;
		}
	}

	@Override
	protected int doFix(Task task, Concept concept, String info)
			throws TermServerScriptException, ValidationFailure {
		// TODO Auto-generated method stub
		return 0;
	}

}
