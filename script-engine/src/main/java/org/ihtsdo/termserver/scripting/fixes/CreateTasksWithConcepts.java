package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
 * QI-1179 Ad-hoc class to put a list of concepts into tasks
 */
public class CreateTasksWithConcepts extends BatchFix implements ScriptConstants{
	
	String[] conceptsToProcess = new String[] {
			"104685000","12843005","2406000",
			"241671007","401295002","412857008",
			"423911002","42423000","442039000",
			"44340006","444225007","445881001",
			"446323000","446889005","446913004",
			"447317003","45681003","710213002",
			"710214008","710215009","710216005",
			"710217001","710219003","711359007",
			"71387007","76145000","773298008"
	};
	
	protected CreateTasksWithConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		CreateTasksWithConcepts fix = new CreateTasksWithConcepts(null);
		try {
			ReportSheetManager.targetFolderId="1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"; //Ad-Hoc Batch Updates
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.taskPrefix = "";  //TODO Set this for each batch
			fix.expectNullConcepts = true; 
			fix.validateConceptOnUpdate = false;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	protected int doFix(Task task, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		return NO_CHANGES_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return Arrays.stream(conceptsToProcess)
				.map(s -> gl.getConceptSafely(s))
				.sorted((c1, c2) -> SnomedUtils.compareSemTagFSN(c1,c2))
				.collect(Collectors.toList());
	}

}
