package org.ihtsdo.termserver.scripting.fixes.managed_service;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
Fix takes concepts identified in the input file and ensures that the 
moduleid of the concept, descriptions and relationships all match
the default.   Note that unlike other fixes, this does not create a 
new task, but targets existing tasks. 
 */
public class FixIncorrectModuleId extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(FixIncorrectModuleId.class);

	Map<String, Task> knownTasks = new HashMap<String, Task>();
	Set<TaskConcept> processMe = new HashSet<TaskConcept>();
	String intendedModuleId;
	
	protected FixIncorrectModuleId(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		FixIncorrectModuleId fix = new FixIncorrectModuleId(null);
		try {
			fix.init(args);
			fix.loadEntriesToFix();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		super.init(args);
		intendedModuleId = project.getMetadata().getDefaultModuleId();
		LOGGER.info("Identified correct module to be {}", intendedModuleId);
	}
	
	public int doFix(Task t, Concept loadedConcept, String info) throws TermServerScriptException {
		int changesMade = checkModuleId(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, "");
		}
		return changesMade;
	}

	private int checkModuleId(Task task, Concept loadedConcept) throws TermServerScriptException {
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
		report(task, loadedConcept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, msg);
		return changesMade;
	}

	private void loadEntriesToFix() throws IOException, TermServerScriptException {
		List<String> lines = Files.readLines(getInputFile(), Charsets.UTF_8);
		LOGGER.info("Loading affected description ids from {}", getInputFile());
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
				try {
					task = scaClient.getTask(taskStr);
				} catch (RestClientException e) {
					throw new TermServerScriptException("Failed to recover task " + taskStr, e);
				}
				knownTasks.put(taskStr, task);
			}
			processMe.add(new TaskConcept(task, gl.getConcept(conceptStr)));
		}
	}

	class TaskConcept {
		Task t;
		Concept c;
		
		TaskConcept (Task t, Concept c) {
			this.t = t;
			this.c = c;
		}
	}

}
