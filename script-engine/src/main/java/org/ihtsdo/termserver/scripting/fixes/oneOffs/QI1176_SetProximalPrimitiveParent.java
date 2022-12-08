package org.ihtsdo.termserver.scripting.fixes.oneOffs;

import java.io.IOException;
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
public class QI1176_SetProximalPrimitiveParent extends BatchFix {
	
	Concept focusConcept = null;
	Concept findParent = null;
	Concept replaceParent = null;
	Set<Concept> focusConceptDescendants;

	protected QI1176_SetProximalPrimitiveParent(BatchFix clone) {
		super(clone);
	}
	
	public static void main(String[] args) throws TermServerScriptException, IOException {
		QI1176_SetProximalPrimitiveParent app = new QI1176_SetProximalPrimitiveParent(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  // Ad-Hoc Batch Updates
			app.init(args);
			app.loadProjectSnapshot(false);  //Load all descriptions
			app.postInit();
			app.processFile();
		} catch (Exception e) {
			info("Failed to complete fix due to " + e.getMessage());
			e.printStackTrace(new PrintStream(System.out));
		} finally {
			app.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException {
		reportNoChange = true;
		selfDetermining = true;
		runStandAlone = true;
		super.init(args);
	}
	
	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[] {"TASK_KEY, TASK_DESC, SCTID, FSN, ConceptType, Severity, ActionType, CharacteristicType, MatchedTemplate, Detail, Detail, Detail"};
		String[] tabNames = new String[] {"Setting PPP"};
		//subsetECL = "<< 26402001 |Nerve block anesthesia (procedure)| ";
		subsetECL = "<< 745638006 |Injection of anesthetic agent (procedure)| MINUS << 26402001 |Nerve block anesthesia (procedure)| ";
		focusConcept = gl.getConcept("399248000 |Procedure related to anesthesia and sedation (procedure)|");
		findParent = PROCEDURE;
		replaceParent = focusConcept;
		focusConceptDescendants = focusConcept.getDescendents(NOT_SET);
		super.postInit(tabNames, columnHeadings, false);
	}

	@Override
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(c, t.getBranchPath());
		if ((loadedConcept.getGciAxioms() != null && loadedConcept.getGciAxioms().size() > 0) 
				|| (loadedConcept.getAdditionalAxioms() != null && loadedConcept.getAdditionalAxioms().size() > 0)) {
			throw new ValidationFailure(t, loadedConcept, "Concept uses axioms");
		}
		Set<Concept> calculatedPPP = new HashSet<>();
		int changesMade = checkAndSetProximalPrimitiveParent(t, loadedConcept, focusConcept, calculatedPPP);
		
		//Did we make a change?  If not, try the long way
		if (changesMade == NO_CHANGES_MADE) {
			//If any of the calculatedPPP are a descendant of the focus concept, then we don't need to make any changes
			for (Concept parent : calculatedPPP) {
				if (focusConceptDescendants.contains(parent)) {
					report (t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Calculated PPP " + parent + " is a descendant of focus concept.  Adding focus concept would be redundant");
					return NO_CHANGES_MADE;
				}
			}
			changesMade = replaceParent(t, c, findParent, replaceParent);
		}
		
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
