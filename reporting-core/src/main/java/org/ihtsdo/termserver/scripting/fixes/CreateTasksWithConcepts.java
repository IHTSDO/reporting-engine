package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

/*
 * QI-1179 Ad-hoc class to put a list of concepts into tasks
 */
public class CreateTasksWithConcepts extends BatchFix implements ScriptConstants{
	
	String[] conceptsToProcess = new String[] {
			"193685008","163322009","140532005","141521001",
			"164334006","141527002","141523003","162167000",
			"163437007","267865000","197830007","163922002",
			"163923007","164271006","139250008","192978001",
			"141314000","139250008","140006008","141710007",
			"164329007","141522008"
	};
	
	protected CreateTasksWithConcepts(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		CreateTasksWithConcepts fix = new CreateTasksWithConcepts(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m"); //Ad-Hoc Batch Updates
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
				.sorted(SnomedUtils::compareSemTagFSN)
				.collect(Collectors.toList());
	}

}
