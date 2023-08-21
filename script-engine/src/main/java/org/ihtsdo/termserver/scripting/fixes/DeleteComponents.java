package org.ihtsdo.termserver.scripting.fixes;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.termserver.scripting.domain.ComponentFactory;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.ScriptConstants;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class DeleteComponents extends BatchFix implements ScriptConstants{

	private String[] componentsToDelete = new String[] {
			"1383141000124117","1383631000124119","1383381000124113",
			"1383201000124112","1383451000124112","1383521000124116",
			"1383651000124114","1383401000124113","1383161000124118",
			"1383501000124114","1383581000124117","1383291000124117",
			"1384161000124115","1383731000124111","1383911000124119",
			"1384001000124115","1384081000124112","1383821000124118",
			"1384111000124118","1383741000124118","1383891000124116",
			"1383941000124115","1384051000124116","1383801000124111",
			"1383251000124111"
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
		c = gl.getComponent(c.getId());
		if (c instanceof Concept) {
			Concept concept = gl.getConcept(c.getId());
			deleteConcept(t, concept);
		} else if (c instanceof Description) {
			Description d = gl.getDescription(c.getId());
			deleteDescription(t, d);
		} else {
			throw new TermServerScriptException("Unable to delete component of type " + c.getClass().getSimpleName());
		}
		Concept concept = gl.getComponentOwner(c.getId());
		report(t, concept, Severity.LOW, ReportActionType.COMPONENT_DELETED, c);
		return CHANGE_MADE;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		return Arrays.stream(componentsToDelete)
				.map(ComponentFactory::create)
				.collect(Collectors.toList());
	}
	
}
