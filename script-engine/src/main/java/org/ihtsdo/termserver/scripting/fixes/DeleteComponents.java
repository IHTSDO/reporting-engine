package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DeleteComponents extends BatchFix implements ScriptConstants {

	private static final Logger LOGGER = LoggerFactory.getLogger(DeleteComponents.class);

	private String[] componentsToDelete = new String[] {
			"a0de0643-9861-47b5-b01d-0b4bdfdc49e3",
			"54234842-1f8a-4827-aa21-c829665c3085",
			"f8a11edc-a9b5-49bf-9bc9-92d4794e0df1",
			"aa0f2ada-dca3-4fca-a38b-3c3487b44b9a",
			"9c00fe2f-26ef-4ed8-afe0-821bfdcedaaf"
	};

	protected DeleteComponents(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		DeleteComponents fix = new DeleteComponents(null);
		try {
			fix.populateEditPanel = false;
			fix.populateTaskDescription = false;
			fix.selfDetermining = true;
			fix.runStandAlone = false;
			fix.worksWithConcepts = false; //Ensures doFix is called with Component
			fix.getArchiveManager().setPopulateReleasedFlag(true);
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
