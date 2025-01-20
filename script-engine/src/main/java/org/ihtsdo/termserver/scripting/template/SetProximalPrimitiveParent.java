package org.ihtsdo.termserver.scripting.template;

import java.io.PrintStream;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.snomed.otf.script.dao.ReportSheetManager;

/**
 * Set Proximal Primitive Parent where possible
 */

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SetProximalPrimitiveParent extends BatchFix {

	private static final Logger LOGGER = LoggerFactory.getLogger(SetProximalPrimitiveParent.class);

	Concept focusConcept = null;

	protected SetProximalPrimitiveParent(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException {
		SetProximalPrimitiveParent app = new SetProximalPrimitiveParent(null);
		try {
			ReportSheetManager.setTargetFolderId("1Ay_IwhPD1EkeIYWuU6q7xgWBIzfEf6dl");  // QI/Normalization
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			LOGGER.info("Failed to complete fix due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}

	@Override
	protected void init(String[] args) throws TermServerScriptException {
		reportNoChange = false;
		selfDetermining = true;
		classifyTasks = true;
		summaryTabIdx = SECONDARY_REPORT;
		super.init(args);
	}

	@Override
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"TASK_KEY, TASK_DESC, SCTID, FSN, ConceptType, Severity, ActionType, CharacteristicType, MatchedTemplate, Detail, Detail, Detail"};
		String[] tabNames = new String[] {"Setting PPP"};
		subsetECL = "<< 55680006 |Drug overdose (disorder)| ";
		focusConcept = gl.getConcept("1149222004 |Overdose (disorder)|");
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	protected int doFix(Task t, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		if ((loadedConcept.getGciAxioms() != null && loadedConcept.getGciAxioms().size() > 0) 
				|| (loadedConcept.getAdditionalAxioms() != null && loadedConcept.getAdditionalAxioms().size() > 0)) {
			throw new ValidationFailure(t, loadedConcept, "Concept uses axioms");
		}
		int changesMade = checkAndSetProximalPrimitiveParent(t, loadedConcept, focusConcept);
		
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		for (Concept c : findConcepts(subsetECL)) {
			//Make changes to a clone of the concept so we don't affect our local copy
			if (!c.getParents(CharacteristicType.STATED_RELATIONSHIP).contains(focusConcept)) {
				toProcess.add(c);
			}
		}
		return toProcess;
	}
	
}
