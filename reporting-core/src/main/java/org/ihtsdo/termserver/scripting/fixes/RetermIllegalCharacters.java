package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.util.*;

public class RetermIllegalCharacters extends BatchFix {

	private static final String EN_DASH = "\u2013";
	private static final String EM_DASH = "\u2014";

	Map<String, String> illegalReplacementMap = new HashMap<>();
	Map<String, String> illegalCharacterNames = new HashMap<>();

	protected RetermIllegalCharacters(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RetermIllegalCharacters fix = new RetermIllegalCharacters(null);
		try {
			ReportSheetManager.setTargetFolderId("1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m");  //Ad-hoc batch updates
			fix.populateEditPanel = true;
			fix.populateTaskDescription = true;
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		illegalReplacementMap.put(EN_DASH, "-");
		illegalReplacementMap.put(EM_DASH, "-");

		illegalCharacterNames.put(EN_DASH, "En Dash");
		illegalCharacterNames.put(EM_DASH, "Em Dash");
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = reterm(task, loadedConcept);
			if (changesMade > 0) {
				updateConcept(task, loadedConcept, info);
			}
		} catch (ValidationFailure v) {
			report(task, concept, v);
		} catch (Exception e) {
			report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
		}
		return changesMade;
	}

	private int reterm(Task t, Concept c) throws TermServerScriptException, ValidationFailure {
		int changesMade = 0;
		for (Map.Entry<String,String> entry : illegalReplacementMap.entrySet()) {
			String find = entry.getKey();
			String replace = entry.getValue();
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (!inScope(d, false)) {
					continue;
				}
				//In this case we're looking for an entire match
				if (d.getTerm().contains(find)) {
					if (!d.isReleased()) {
						report(t, c, Severity.MEDIUM, ReportActionType.INFO, "New description this cycle");
					}
					String replacement = d.getTerm().replaceAll(find, replace);
					String msg = "Replaced " + illegalCharacterNames.get(find);
					replaceDescription(t, c, d, replacement, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, false, msg, null	);
					changesMade++;
				}
			}
		}
		return changesMade;
	}


	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> toProcess = new ArrayList<>();
		nextConcept:
		//for (Concept c : SnomedUtils.sort(gl.getAllConcepts())) {
		for (Concept c : Collections.singleton(gl.getConcept("776207001|Product containing only human regular insulin (medicinal product)|"))) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				if (inScope(d, false)) {
					for (String illegalCharacter : illegalReplacementMap.keySet()) {
						if (d.getTerm().contains(illegalCharacter)) {
							toProcess.add(c);
							continue nextConcept;
						}
					}
				}
			}
		}
		return toProcess;
	}

}
