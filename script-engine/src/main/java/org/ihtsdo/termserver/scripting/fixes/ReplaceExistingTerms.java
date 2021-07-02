package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * Removes a substring from all active Terms, where matched in context for a given subHierarchy
 * Updated for INFRA-6959
 */
public class ReplaceExistingTerms extends BatchFix implements RF2Constants{
	
	static Map<String, String> replacementMap = new HashMap<String, String>();
	
	/*String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static final String match = "Product containing";
	static final String replace =  "Product containing only";*/
	
	String subHierarchyStr = "183944003"; // Procedure refused (situation)|
	static final String match = "refused";
	static final String replace =  "declined";
	boolean retainPtAsAcceptable = true;
	
	protected ReplaceExistingTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		ReplaceExistingTerms fix = new ReplaceExistingTerms(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
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
		int changesMade = replaceTerms(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int replaceTerms(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			if (d.isPreferred() && d.getTerm().contains(match)) {
				String newTerm = d.getTerm().replace(match, replace);
				replaceDescription(t, c, d, newTerm, InactivationIndicator.ERRONEOUS, retainPtAsAcceptable, "");
				changesMade++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allPotential = gl.getConcept(subHierarchyStr).getDescendents(NOT_SET);
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		List<DescriptionType> descTypes = new ArrayList<>();
		descTypes.add(DescriptionType.FSN);
		descTypes.add(DescriptionType.SYNONYM);
		info("Identifying concepts to process");
		for (Concept c : allPotential) {
			String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, descTypes)) {
				if (d.isPreferred() && d.getTerm().contains(match)) {
					allAffected.add(c);
					break;
				}
			}
		}
		info ("Identified " + allAffected.size() + " concepts to process");
		return new ArrayList<Component>(allAffected);
	}
}
