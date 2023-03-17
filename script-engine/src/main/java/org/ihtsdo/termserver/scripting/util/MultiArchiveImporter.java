package org.ihtsdo.termserver.scripting.util;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

import org.apache.commons.lang3.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
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
public class MultiArchiveImporter extends BatchFix {
	
	private final static String taskPrefix = "INFRA-9531 ";

	protected MultiArchiveImporter(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		MultiArchiveImporter importer = new MultiArchiveImporter(null);
		try {
			ReportSheetManager.targetFolderId = "1bO3v1PApVCEc3BWWrKwc525vla7ZMPoE"; //Batch Import
			importer.classifyTasks = true;
			importer.init(args);
			importer.postInit(null, new String[] {"Task, Archive, Result"}, false);
			importer.importArchives();
		} finally {
			importer.finish();
		}
	}

	private void importArchives() throws TermServerScriptException {
		info("Processing all archives in " + getInputFile());
		String[] dirListing = getInputFile().list();
		Arrays.sort(dirListing, NumberAwareStringComparator.INSTANCE);
		for (String archiveStr : dirListing){
			File thisArchive = new File(getInputFile() + File.separator + archiveStr);
			if (thisArchive.getPath().endsWith(".zip")) {
				info("Processing: " + thisArchive);
				importArchive(thisArchive);
			} else {
				info("Skipping non archive: " + thisArchive);
			}
		}
	}

	private void importArchive(File thisArchive) throws TermServerScriptException {
		String result = "OK";
		Task task = null;
		try {
			task = new Task(null, author_reviewer);
			task.setSummary(taskPrefix + "Import " + thisArchive.getName());
			createTask(task);
			if (!dryRun) {
				tsClient.importArchive(task.getBranchPath(), ImportType.DELTA, thisArchive);
				updateTask(task);
				if (classifyTasks) {
					info ("Classifying " + task);
					Classification classification = scaClient.classify(task.getKey());
					debug(classification);
					tsClient.waitForCompletion(task.getBranchPath(), classification);
				}
			}
		} catch (Exception e) {
			error("Failure to import " + thisArchive, e);
			result = e.toString();
		}
		report(PRIMARY_REPORT, task.getKey(), thisArchive.getName(), result);
	}

	@Override
	protected int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		throw new NotImplementedException();
	}
}
