package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
Find matching semantic tags and swap for specified replacement
Currently verifies that the existing FSN is new and unpublished
-- Also adding in a cheeky fix while we're going through concepts to make GB only preferred terms acceptable in US dialect.
 */
public class ReplaceSematicTags extends BatchFix implements ScriptConstants{
	
	String subHierarchyStr = "373873005"; // |Pharmaceutical / biologic product (product)|
	static Map<String, String> replacementMap = new HashMap<String, String>();
	static {
		replacementMap.put("(virtual medicinal product form)","(medicinal product form)");
		replacementMap.put("(virtual medicinal product)","(medicinal product)");
		replacementMap.put("(virtual clinical drug)","(clinical drug)");
	}
	protected ReplaceSematicTags(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceSematicTags fix = new ReplaceSematicTags(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = replaceSemanticTag(t, loadedConcept);
		if (changesMade > 0) {
			checkAllDescriptionsAcceptable(t, loadedConcept);
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private void checkAllDescriptionsAcceptable(Task task, Concept loadedConcept) throws TermServerScriptException {
		//If we have two preferred terms, then make the GB Pref Term also acceptable in US Dialect
		List<Description> synonyms = loadedConcept.getDescriptions(Acceptability.BOTH, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		for (Description d : synonyms) {
			Map<String, Acceptability> acceptabilityMap = d.getAcceptabilityMap();
			if (acceptabilityMap.size() == 1) {
				//If we only have one acceptability for this term, add the other one.
				String missing = acceptabilityMap.containsKey(GB_ENG_LANG_REFSET)?US_ENG_LANG_REFSET:GB_ENG_LANG_REFSET;
				acceptabilityMap.put(missing, Acceptability.ACCEPTABLE);
				String msg = "Added " + (missing.equals(GB_ENG_LANG_REFSET) ? "GB":"US") + " acceptability";
				report(task, loadedConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
			}
		}
	}

	private int replaceSemanticTag(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> fsnList = concept.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE);
		if (fsnList.size() != 1) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Number of active FSNs encountered was: " + fsnList.size());
		} else {
			Description fsn = fsnList.iterator().next();
			String[] fsnParts = SnomedUtils.deconstructFSN(fsn.getTerm());
			String newFSN = null;
			for (Map.Entry<String, String> entry : replacementMap.entrySet()) {
				if (fsnParts[1].equals(entry.getKey())) {
					newFSN = fsnParts[0] + " " + entry.getValue();
				}
			}
			if (newFSN != null) {
				if (termAlreadyExists(concept, newFSN)) {
					throw new TermServerScriptException("Term already exists: " + newFSN);
				}
				if (fsn.getEffectiveTime() != null) {
					throw new TermServerScriptException("Detected attempt to delete published description");
				}
				//This will cause the existing fsn to be deleted and a new one created.
				fsn.setDescriptionId(null);
				fsn.setTerm(newFSN);
				changesMade++;
				report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "FSN set to " + newFSN);
			}
		}
		return changesMade;
	}

	private boolean termAlreadyExists(Concept concept, String newTerm) {
		boolean termAlreadyExists = false;
		for (Description description : concept.getDescriptions()) {
			if (description.getTerm().equals(newTerm)) {
				termAlreadyExists = true;
			}
		}
		return termAlreadyExists;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		Set<Concept> allPotential = gl.getConcept(subHierarchyStr).getDescendants(NOT_SET);
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		for (Concept thisConcept : allPotential) {
			for (String replaceTag : replacementMap.keySet()) {
				if (thisConcept.getFSNDescription().getTerm().contains(replaceTag)) {
					allAffected.add(thisConcept);
				}
			}
		}
		return new ArrayList<Component>(allAffected);
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
}
