package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.reports.loinc.LoincUtils;
import org.snomed.otf.script.dao.ReportManager;

/*
 * LE-3 Batch Fix class expected to be called from another process which has created
 * the concepts to be saved
 */
public class CreateConceptsPreModelled extends BatchFix implements ScriptConstants{
	
	private List<Component> conceptsToCreate;
	private int tabIdx;
	
	public CreateConceptsPreModelled(ReportManager rm, int tabIdx, Set<Concept> concepts) {
		super(null);
		this.setReportManager(rm);
		this.tabIdx = tabIdx;
		this.conceptsToCreate = asComponents(concepts);
		this.selfDetermining = true;
	}

	@Override
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		String loincNum = LoincUtils.getLoincNumFromDescription(c);
		try {
			String scg = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			//Remove any temporary identifiers before creating
			c.setId(null);
			Concept createdConcept = createConcept(t, c, info, false);
			report(tabIdx, t, createdConcept, Severity.NONE, ReportActionType.CONCEPT_ADDED, loincNum, scg);
		} catch (Exception e) {
			report(tabIdx, t, c, Severity.CRITICAL, ReportActionType.API_ERROR, loincNum, e.getMessage());
		}
		return CHANGE_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return conceptsToCreate;
	}

}
