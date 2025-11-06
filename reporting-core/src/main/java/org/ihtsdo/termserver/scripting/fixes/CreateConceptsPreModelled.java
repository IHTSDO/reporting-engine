package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.pipeline.domain.ConceptWrapper;
import org.snomed.otf.script.dao.ReportManager;

/*
 * LE-3 Batch Fix class expected to be called from another process which has created
 * the concepts to be saved
 */
public class CreateConceptsPreModelled extends BatchFix implements ScriptConstants{
	
	private List<Component> conceptsToCreate;
	private Map<Concept, ConceptWrapper> conceptWrapperMap;  //Needed so we can find the original homes of our concepts once they're created
	private int tabIdx;
	
	public CreateConceptsPreModelled(ReportManager rm, int tabIdx, Set<? extends ConceptWrapper> conceptsWrapped) {
		super(null);
		this.setReportManager(rm);
		this.tabIdx = tabIdx;
		conceptWrapperMap = conceptsWrapped.stream()
				.collect(Collectors.toMap(ConceptWrapper::getConcept, Function.identity()));
		this.conceptsToCreate = asComponents(conceptWrapperMap.keySet());
		this.selfDetermining = true;
	}

	@Override
	protected int doFix(Task t, Concept c, String info) throws TermServerScriptException, ValidationFailure {
		//What was this concept original wrapped in?
		ConceptWrapper wrapping = conceptWrapperMap.get(c);
		try {
			String scg = c.toExpression(CharacteristicType.STATED_RELATIONSHIP);
			//Remove any temporary identifiers before creating
			c.setId(null);
			Concept createdConcept = createConcept(t, c, info, false);
			wrapping.setConcept(createdConcept);  //Save it so our calling function can retain this
			report(tabIdx, t, createdConcept, Severity.NONE, ReportActionType.CONCEPT_ADDED, wrapping.getWrappedId(), scg, "OK");
		} catch (Exception e) {
			report(tabIdx, t, c, Severity.CRITICAL, ReportActionType.API_ERROR, wrapping.getWrappedId(), e.getMessage());
		}
		return CHANGE_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return conceptsToCreate;
	}

}
