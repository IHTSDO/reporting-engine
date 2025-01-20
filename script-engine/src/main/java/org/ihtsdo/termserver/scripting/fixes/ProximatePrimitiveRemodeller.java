package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.*;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

/*
Fix identifies concepts that can be safely remodelled using a proximate primitives pattern
 */
public class ProximatePrimitiveRemodeller extends BatchFix implements ScriptConstants {
	
	String subHierarchyStr = "282100009 |Adverse reaction caused by substance (disorder)| ";
	Concept proximalPrimitiveParent = null;
	
	protected ProximatePrimitiveRemodeller(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ProximatePrimitiveRemodeller fix = new ProximatePrimitiveRemodeller(null);
		try {
			fix.selfDetermining = true;
			fix.init(args);
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}
	
	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = remodel(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}
	
	public void postInit() throws TermServerScriptException {
		proximalPrimitiveParent = gl.getConcept("281647001 |Adverse reaction (disorder)|");
		super.postInit();
	}

	/**
	 * Restates the parent as the subHierarchy root, and the inferred relationships as stated
	 * @param task
	 * @param concept
	 * @return
	 * @throws TermServerScriptException 
	 */
	private int remodel(Task t, Concept c) throws TermServerScriptException {
		return checkAndSetProximalPrimitiveParent(t, c, proximalPrimitiveParent);
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> conceptsToProcess = new ArrayList<>();
		for (Concept c : gl.getConcept(subHierarchyStr).getDescendants(NOT_SET)) {
			if (!inScope(c)) {
				continue;
			}
			//This check will report any concepts that cannot have their PPP remodelled.
			if (checkAndSetProximalPrimitiveParent(null, c.cloneWithIds(), proximalPrimitiveParent, true, false) != NO_CHANGES_MADE) {
				conceptsToProcess.add(c);
			}
		}
		return conceptsToProcess;
	}

}
