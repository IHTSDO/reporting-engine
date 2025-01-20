package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.BrowserClient;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RemoveWAS_A extends BatchFix implements ScriptConstants{

	private static final Logger LOGGER = LoggerFactory.getLogger(RemoveWAS_A.class);

	BrowserClient browserClient;
	Set<Concept> allActiveConcepts;
	
	protected RemoveWAS_A(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		RemoveWAS_A fix = new RemoveWAS_A(null);
		try {
			fix.reportNoChange = false;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.additionalReportColumns = "Inactivation, Sibling + Lexical match, WAS_A -> MAY BE, Current Siblings, Lexical Matches (same semtag), Lexical matches (other semtag)";
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		browserClient = new BrowserClient();
		allActiveConcepts = gl.getAllConcepts().stream().filter(c -> c.isActive()).collect(Collectors.toSet());
		super.postInit();
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = removeWAS_A(task, loadedConcept);
		if (changesMade > 0) {
			try {
				updateConcept(task, loadedConcept, info);
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int removeWAS_A(Task t, Concept c) throws TermServerScriptException {
		//TODO Remove any "On Examination" was a and report if no other options
		int changesMade = 0;
		Set<Concept> was_a = getWAS_A(c);
		
		//How many concepts have this same number of parents?
		Set<Concept> sameParents = SnomedUtils.hasParents(was_a, allActiveConcepts, 5);
		
		//What's the closest lexical match we have for this concept
		List<Concept> lexicalMatches = getClosestLexicalMatch(c, true);
		
		List<Concept> lexicalMatchesOther = null;
		if (lexicalMatches.size() == 0) {
			lexicalMatchesOther = getClosestLexicalMatch(c, false);
		}
		
		List<Concept> intersection = new ArrayList<>(sameParents);
		intersection.retainAll(lexicalMatches);
		
		String semtag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		InactivationIndicator inactivation = c.getInactivationIndicator();
		report(t, c, Severity.LOW, ReportActionType.INFO, inactivation, toString(intersection), "MAY BE A " + toString(was_a), toString(sameParents), toString(lexicalMatches, semtag, 5),  toString(lexicalMatchesOther, 5));
		
		//Intersection between these two would be superb!
		
		for (Concept oldParent : was_a) {
			if (!oldParent.isActive()) {
				//There are none of these.
				report(t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "WAS_A concept is now inactive", oldParent);
			}
		}
		return changesMade;
	}
	
	private String toString(Collection<Concept> concepts) {
		return concepts.stream()
		.map(c -> c.toString())
		.collect(Collectors.joining(", "));
	}
	
	private String toString(Collection<Concept> concepts, String semtag, int limit) {
		return concepts.stream()
		.filter(c -> SnomedUtils.deconstructFSN(c.getFsn())[1].equals(semtag))
		.map(c -> c.toString())
		.limit(limit)
		.collect(Collectors.joining(", "));
	}
	
	private String toString(Collection<Concept> concepts,  int limit) {
		if (concepts == null) {
			return "";
		}
		return concepts.stream()
		.map(c -> c.toString())
		.limit(limit)
		.collect(Collectors.joining(", "));
	}

	private List<Concept> getClosestLexicalMatch(Concept c, boolean filterSemTag) throws TermServerScriptException {
		String[] fsnParts = SnomedUtils.deconstructFSN(c.getFsn());
		//Clean up all the things we don't want to match
		String matchTerms = fsnParts[0].replaceAll("-", " ")
				.replaceAll("NOS", " ")
				.replaceAll("NEC", " ")
				.replaceAll("OE", " ")
				.replaceAll("unspecified", " ")
				.replaceAll("specified", " ")
				.replaceAll("Other", " ")
				.trim();
		return browserClient.findConcepts(matchTerms, filterSemTag?fsnParts[1]:null, 5);
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find primitive concepts with redundant stated parents
		LOGGER.info("Identifying concepts to process");
		List<Component> processMe = new ArrayList<>();
		for (Concept c : gl.getAllConcepts()) {
			//Any concepts with a historical association WAS A is of interest
			if (getWAS_A(c).size() > 0) {
				if (c.isActive()) {
					LOGGER.warn (c + " is active with an active historical association");
				}
				processMe.add(c);
			}
		}
		LOGGER.info("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}
	
	Set<Concept> getWAS_A(Concept c) throws TermServerScriptException {
		Set<Concept> was_a = new HashSet<>();
		for (AssociationEntry assoc: c.getAssociationEntries(ActiveState.ACTIVE)) {
			if (assoc.getRefsetId().equals(SCTID_ASSOC_WAS_A_REFSETID)) {
				was_a.add(gl.getConcept(assoc.getTargetComponentId()));
			}
		}
		return was_a;
	}
}
