package org.ihtsdo.termserver.scripting.fixes.one_offs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.ExceptionUtils;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.snomed.otf.script.dao.ReportSheetManager;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.*;
import java.util.stream.Collectors;

/**
 * QI-1209
 */
public class INFRA13212_ConvertDrugStrengthTo3SF extends BatchFix {

	protected INFRA13212_ConvertDrugStrengthTo3SF(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		INFRA13212_ConvertDrugStrengthTo3SF fix = new INFRA13212_ConvertDrugStrengthTo3SF(null);
		try {
			ReportSheetManager.targetFolderId = "1fIHGIgbsdSfh5euzO3YKOSeHw4QHCM-m";  //Ad-hoc batch updates
			fix.populateEditPanel = false;
			fix.selfDetermining = true;
			fix.reportNoChange = true;
			fix.additionalReportColumns = "Action Detail";
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		String[] columnHeadings = new String[]{
				"Task Key, Task Description, Concept SCTID,FSN, ,Severity, Action, Details, Details, Details, , ,",
				"Concept SCTID,FSN,Descriptions,Expression"};
		String[] tabNames = new String[]{
				"Processed",
				"Excluded"};
		super.postInit(tabNames, columnHeadings, false);

		subsetECL = " << 373873005 |Pharmaceutical / biologic product|";

	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		int changesMade = 0;
		try {
			Concept loadedConcept = loadConcept(concept, task.getBranchPath());
			changesMade = adjustStrength(task, loadedConcept);
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


	private int adjustStrength(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (has3dpStrength(r)) {
				String currentStrength = r.getConcreteValue().getValue();
				BigDecimal bd = new BigDecimal(currentStrength);
				String newStrength = bd.round(new MathContext(3)).toPlainString();
				r.setConcreteValue(new ConcreteValue(ConcreteValue.ConcreteValueType.DECIMAL, newStrength));
				changesMade++;
				report(t, c, Severity.MEDIUM, ReportActionType.INFO, r,"Changing strength from " + currentStrength + " to " + newStrength);
				changesMade += updateTerms(t, c, currentStrength, newStrength);
			}
		}
		return changesMade;
	}

	private int updateTerms(Task t, Concept c, String currentStrengthStr, String newStrength) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			String term = d.getTerm();
			if (term.contains(currentStrengthStr)) {
				String newTerm = term.replace(currentStrengthStr, newStrength);
				replaceDescription(t, c, d, newTerm, InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY, false);
				report(t, c, Severity.LOW, ReportActionType.INFO, d, "Changing term from " + term + " to " + newTerm);
				changesMade++;
			}
		}
		return changesMade;
	}

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		setQuiet(true);
		List<Component> componentsToProcess = SnomedUtils.sort(findConcepts(subsetECL))
				.stream()
				.filter(c -> has3dpStrength(c))
				.collect(Collectors.toList());
		setQuiet(false);
		return componentsToProcess;
	}

	private boolean has3dpStrength(Concept c) {
		for (Relationship r : c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			boolean has3dpStrength = has3dpStrength(r);
			if (has3dpStrength) {
				return true;
			}
		}
		return false;
	}

	private boolean has3dpStrength(Relationship r) {
		if (r.isConcrete() && r.getConcreteValue().getValue().contains(".")) {
			String[] parts = r.getConcreteValue().getValue().split("\\.");
			if (parts.length > 1 && parts[1].length() > 2) {
				return true;
			}
		}
		return false;
	}
}