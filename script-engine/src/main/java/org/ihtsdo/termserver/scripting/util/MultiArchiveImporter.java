package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Classification;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.TermServerClient.ImportType;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * Class to import a set of archives (found in a single directory) into a successive
 * set of tasks.  Specify -f argument for the directory and -p for the project 
 * @author peter
 *
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MultiArchiveImporter extends BatchFix {

	enum MODE { TASK_PER_ARCHIVE, ALL_ARCHIVES_IN_ONE_TASK };

	private static final Logger LOGGER = LoggerFactory.getLogger(MultiArchiveImporter.class);

	private final static String taskPrefix = "";
	private final static MODE mode = MODE.ALL_ARCHIVES_IN_ONE_TASK;

	protected MultiArchiveImporter(BatchFix clone) {
		super(clone);
	}

	private Task lastTaskCreated = null;

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MultiArchiveImporter importer = new MultiArchiveImporter(null);
		try {
			ReportSheetManager.targetFolderId = "1bO3v1PApVCEc3BWWrKwc525vla7ZMPoE"; //Batch Import
			importer.classifyTasks = true;
			importer.allowDirectoryInputFile = true;
			importer.init(args);
			importer.postInit(null, new String[] {"Task, Archive, User, Result"}, false);
			importer.importArchives();
		} finally {
			importer.finish();
		}
	}

	private void importArchives() throws TermServerScriptException {
		String limitStr = processingLimit == NOT_SET ? "all" : processingLimit + "";
		LOGGER.info("Processing " + limitStr + " archives in " + getInputFile());
		String[] dirListing = getInputFile().list();
		Arrays.sort(dirListing, NumberAwareStringComparator.INSTANCE);
		int archivesProcessed = 0;
		for (String archiveStr : dirListing){
			File thisArchive = new File(getInputFile() + File.separator + archiveStr);

			if (thisArchive.getPath().endsWith(".zip")) {
				archivesProcessed++;
				if (archivesProcessed < restartFromTask) {
					LOGGER.info("Skipping archive " + thisArchive + " as we're restarting from task " + restartFromTask);
					continue;
				}
				LOGGER.info("Processing: " + thisArchive);
				importArchive(thisArchive);

				if (processingLimit != NOT_SET && archivesProcessed >= processingLimit) {
					LOGGER.info("Processing limit of " + processingLimit + " reached.  Exiting.");
					break;
				}
			} else {
				LOGGER.info("Skipping non archive: " + thisArchive);
			}
		}

		if (classifyTasks && mode == MODE.ALL_ARCHIVES_IN_ONE_TASK) {
			classify(lastTaskCreated);
		}
	}

	private void importArchive(File thisArchive) throws TermServerScriptException {
		String result = "OK";
		Task task = mode == MODE.ALL_ARCHIVES_IN_ONE_TASK ? lastTaskCreated : null;
		try {
			if (task == null || mode == MODE.TASK_PER_ARCHIVE) {
				task = new Task(null, getNextAuthor(), getNextReviewer());
				lastTaskCreated = task;
				task.setSummary(taskPrefix + "Import " + thisArchive.getName());
				createTask(task);
			}

			if (!dryRun) {
				tsClient.importArchive(task.getBranchPath(), ImportType.DELTA, thisArchive);
				updateTask(task, getReportName(), getReportManager().getUrl());
				if (classifyTasks && mode == MODE.TASK_PER_ARCHIVE) {
					classify(task);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failure to import " + thisArchive, e);
			result = e.toString();
		}
		report(PRIMARY_REPORT, task.getKey(), thisArchive.getName(), task.getAssignedAuthor(), result);
	}

	private void classify(Task task) throws TermServerScriptException {
		try {
			LOGGER.info ("Classifying " + task);
			Classification classification = null;
			classification = scaClient.classify(task.getKey());
			LOGGER.debug(classification.toString());
			tsClient.waitForCompletion(task.getBranchPath(), classification);
		} catch (RestClientException e) {
			throw new TermServerScriptException(e);
		}
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		throw new NotImplementedException();
	}
}
