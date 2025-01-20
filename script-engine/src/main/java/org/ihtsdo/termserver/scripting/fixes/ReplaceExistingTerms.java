package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Removes a substring from all active Terms, where matched in context for a given subHierarchy
 * Updated for INFRA-6959
 * MSSP-1851 Remove "yp-" from Dutch terms
 */
public class ReplaceExistingTerms extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(ReplaceExistingTerms.class);
	
	static Map<String, String> replacementMap = new HashMap<String, String>();
	static final String match = "yp-stadium";
	static final String replace =  "stadium";
	boolean retainPtAsAcceptable = false;
	
	private static final Set<String> exclusions = new HashSet<>();
	static {
		exclusions.add("1222594003");
	}
	
	protected ReplaceExistingTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceExistingTerms fix = new ReplaceExistingTerms(null);
		try {
			fix.selfDetermining = true;
			fix.populateEditPanel = false;
			fix.getArchiveManager().setRunIntegrityChecks(false);
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
			if (inScope(d) && /*d.isPreferred() &&*/ d.getTerm().contains(match)
					/*d.getTerm().startsWith(match)*/) {
				String newTerm = d.getTerm().replace(match, replace);
				replaceDescription(t, c, d, newTerm, InactivationIndicator.ERRONEOUS, retainPtAsAcceptable, "");
				changesMade++;
			}
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		
		List<Concept> allPotential = SnomedUtils.sort(gl.getAllConcepts());
		Set<Concept> allAffected = new TreeSet<Concept>();  //We want to process in the same order each time, in case we restart and skip some.
		List<DescriptionType> descTypes = new ArrayList<>();
		descTypes.add(DescriptionType.FSN);
		descTypes.add(DescriptionType.SYNONYM);
		LOGGER.info("Identifying concepts to process");
		for (Concept c : allPotential) {
			if (exclusions.contains(c.getId())) {
				continue;
			}
			for (Description d : c.getDescriptions(ActiveState.ACTIVE, descTypes)) {
				if (inScope(d) && /*d.isPreferred() &&*/ d.getTerm().contains(match)
						/*d.getTerm().startsWith(match)*/) {
					allAffected.add(c);
					break;
				}
			}
		}
		LOGGER.info("Identified {} concepts to process", allAffected.size());
		return new ArrayList<>(allAffected);
	}
}
