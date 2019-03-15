package org.ihtsdo.termserver.scripting.fixes.qi;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.TermServerClientException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;
/**
 * QI-28 For << 95896000 |Protozoan infection (disorder)|
 * Switch any 370135005 |Pathological process (attribute)| values
 * from 441862004 |Infectious process (qualifier value)|
 * to 442614005 |Parasitic process (qualifier value)|
 * BUT only where we have a causative agent which is  <<  417396000 |Kingdom Protozoa (organism)|
 */
public class QI28_SwitchParasiticProcess extends BatchFix {
	
	String subHierarchy = "95896000"; // |Protozoan infection (disorder)|
	Concept findType;
	Concept findValue;
	Concept replaceValue;
	Concept alsoPresentType;
	String alsoPresentHierarchy =  "417396000"; // |Kingdom Protozoa (organism)|
	Set<Concept> alsoPresentValues;
	
	protected QI28_SwitchParasiticProcess(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, TermServerClientException, InterruptedException {
		QI28_SwitchParasiticProcess fix = new QI28_SwitchParasiticProcess(null);
		try {
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postLoadInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		findType = PATHOLOGICAL_PROCESS;
		findValue = gl.getConcept("441862004"); // |Infectious process (qualifier value)|
		replaceValue = gl.getConcept("442614005"); // |Parasitic process (qualifier value)|
		alsoPresentType = gl.getConcept("246075003"); // |Causative agent (attribute)|
		Concept alsoPresent = gl.getConcept(alsoPresentHierarchy); 
		alsoPresentValues = alsoPresent.getDescendents(NOT_SET);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = switchValues(task, loadedConcept);
			String conceptSerialised = gson.toJson(loadedConcept);
			if (changesMade > 0) {
				debug ((dryRun ?"Dry run ":"Updating state of ") + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}
	
	private int switchValues(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		//Switch all stated relationships from the findValue to the replaceValue
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, findType, ActiveState.ACTIVE)) {
			if (r.getTarget().equals(findValue)) {
				//We also need to have our also present type present in the same group
				Concept alsoPresentValue = SnomedUtils.getTarget(c, new Concept[] {alsoPresentType}, r.getGroupId(), CharacteristicType.STATED_RELATIONSHIP);
				if (alsoPresentValues.contains(alsoPresentValue)) {
					changesMade += replaceRelationship(t, c, findType, replaceValue, r.getGroupId(), false);
				} else {
					report (t, c, Severity.MEDIUM, ReportActionType.INFO, "Skipped relationship due to lack of " + alsoPresentType + " = <<" + gl.getConcept(alsoPresentHierarchy) + " : " + r);
				}
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<>();
		//Find all descendants of subHierarchy
		for (Concept c : gl.getConcept(subHierarchy).getDescendents(NOT_SET)) {
			//which have stated 370135005 |Pathological process (attribute)| 
			//equal 441862004 |Infectious process (qualifier value)|
			Set<Concept> pathProcs = SnomedUtils.getTargets(c, new Concept[]{findType}, CharacteristicType.STATED_RELATIONSHIP);
			if (pathProcs.contains(findValue)) {
				processMe.add(c);
			}
		}
		return processMe;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		throw new NotImplementedException();
	}

}
