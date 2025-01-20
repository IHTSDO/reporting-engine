package org.ihtsdo.termserver.scripting.fixes;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;

public class DeleteComponents extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeleteComponents.class);

	private String[] componentsToDelete = new String[] {
		"4523b4f4-edc8-4123-8d97-f65e658bdea1","7487abdf-d923-471d-897d-e7db5f518118",
		"d967c399-049a-47be-8044-09c6210d4efd","db7b351e-e1d6-4300-ae73-594388b4e912"
	};

	protected DeleteComponents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DeleteComponents fix = new DeleteComponents(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.worksWithConcepts = false; //Ensures doFix is called with Component
			fix.getArchiveManager().setEnsureSnapshotPlusDeltaLoad(true);
			fix.init(args);
			fix.loadProjectSnapshot(false);
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Component c, String info) throws TermServerScriptException {
		Component fullComponent = gl.getComponent(c.getId());
		if (fullComponent == null || fullComponent instanceof RefsetMember) {
			if (fullComponent == null && c.getId().contains("-")) {
				LOGGER.warn("Suspected orphan {}, attempting refset deletion", c.getId());
			} else {
				throw new TermServerScriptException("Further work needed to delete oprhan component: " + c.getId());
			}
			deleteRefsetMember(t, c.getId());
		} else if (fullComponent instanceof Concept) {
			Concept concept = gl.getConcept(c.getId());
			deleteConcept(t, concept);
		} else if (fullComponent instanceof Description) {
			Description d = gl.getDescription(c.getId());
			deleteDescription(t, d);
		} else {
			throw new TermServerScriptException("Unable to delete component of type " + c.getClass().getSimpleName());
		}

		if (fullComponent == null) {
			report(t, (Concept)null, Severity.LOW, ReportActionType.COMPONENT_DELETED, c.getId());
		} else {
			Concept concept = gl.getComponentOwner(c.getId());
			report(t, concept, Severity.LOW, ReportActionType.COMPONENT_DELETED, c);
		}
			return CHANGE_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return Arrays.stream(componentsToDelete)
				.map(ComponentFactory::create)
				.collect(Collectors.toList());
	}
	
}
