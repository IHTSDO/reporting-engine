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
			"285598005","285603002","285604008",
			"285605009","285606005","285607001",
			"285608006","285609003","285610008",
			"285611007","285612000","285613005",
			"285614004","285615003","285616002",
			"285617006","285618001","285619009",
			"285631006","285633009","285634003",
			"285635002","285637005","285638000",
			"285639008","285640005","285641009",
			"285642002","285643007","285644001"	
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
			fix.reportNoChange = true;
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
