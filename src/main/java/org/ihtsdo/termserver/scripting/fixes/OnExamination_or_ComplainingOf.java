package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
For Jim.  In the Clinical Findings hierarchy, find concepts starting "On Examination - X"
and "Complaining of X" and see if they have X on its own as a parent
*/
public class OnExamination_or_ComplainingOf extends BatchFix implements RF2Constants{
	
	String[] itemsOfInterest = new String[] { "On examination - ", "Complaining of "};
	String targetHierarchy = "404684003"; // |Clinical finding (finding)|
	String except = "64572001"; // |Disease (disorder)|
	Collection<Concept> targetDomain;
	String[] genericTerms = new String[] {"present", "external", "microscopy", "reflex", 
			"defect", "group", "week", "size", "organism", "female", "male", "positive"};
	int maxPossibilities = 7;
	
	protected OnExamination_or_ComplainingOf(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		OnExamination_or_ComplainingOf fix = new OnExamination_or_ComplainingOf(null);
		try {
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.additionalReportColumns = "exactMatch, closeMatch, suggestedAddition, possibleAdditions, suggestedCreation";
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(true); 
			fix.postLoadInit();
			fix.startTimer();
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}

	private void postLoadInit() throws TermServerScriptException {
		//The target domain is Clinical Finding Hierarchy, with the disorders removed
		targetDomain = gl.getConcept(targetHierarchy).getDescendents(NOT_SET);
		Collection<Concept> exceptions = gl.getConcept(except).getDescendents(NOT_SET);
		targetDomain.removeAll(exceptions);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = removeRedundantParents(task, loadedConcept);
		if (changesMade > 0) {
			try {
				String conceptSerialised = gson.toJson(loadedConcept);
				debug ("Updating state of " + loadedConcept + info);
				if (!dryRun) {
					tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
				}
			} catch (Exception e) {
				report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + ExceptionUtils.getStackTrace(e));
			}
		}
		return changesMade;
	}

	private int removeRedundantParents(Task task, Concept loadedConcept) throws TermServerScriptException {
		int changesMade = 0;
		
		//Make sure we're working with a Primitive Concept
		if (loadedConcept.getDefinitionStatus().equals(DefinitionStatus.FULLY_DEFINED)) {
			report (task, loadedConcept, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Concept is fully defined" );
			return 0;
		}
		
		List<Relationship> parentRels = new ArrayList<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																				IS_A,
																				ActiveState.ACTIVE));
		
		for (Relationship parentRel : parentRels) {
			//If we have a more specific parent, delete or inactivate this one
			Concept moreSpecific = findMoreSpecificCoparent(parentRel.getTarget(), loadedConcept);
			if (moreSpecific != null) {
				remove(task, parentRel, loadedConcept, moreSpecific);
				changesMade++;
			}
		}
		return changesMade;
	}


	private Concept findMoreSpecificCoparent(Concept parent, Concept loadedConcept) throws TermServerScriptException {
		for (Concept coParent : getParents(loadedConcept)) {
			//Does the coParent have the parent in question as one of it's ancestors?
			if (coParent.getAncestors(NOT_SET).contains(parent)) {
				return coParent;
			}
		}
		return null;
	}

	/*
	 * Loaded concepts don't fill the parents list on the concept pojo, so we'll do that explicitly here
	 */
	private List<Concept> getParents(Concept loadedConcept) throws TermServerScriptException {
		List<Concept> parents = new ArrayList<>();
		List<Relationship> parentRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
										IS_A,
										ActiveState.ACTIVE);
		for (Relationship r : parentRels) {
			parents.add(gl.getConcept(r.getTarget().getConceptId()));
		}
		return parents;
	}

	private void remove(Task t, Relationship rel, Concept loadedConcept, Concept retained) {
		//Are we inactivating or deleting this relationship?
		if (rel.getEffectiveTime() == null || rel.getEffectiveTime().isEmpty()) {
			loadedConcept.removeRelationship(rel);
			report (t, loadedConcept, Severity.LOW, ReportActionType.RELATIONSHIP_DELETED, "Deleted parent: " + rel.getTarget() + " in favour of " + retained);
		} else {
			rel.setEffectiveTime(null);
			rel.setActive(false);
			report (t, loadedConcept, Severity.MEDIUM, ReportActionType.RELATIONSHIP_REMOVED, "Inactivated parent: " + rel.getTarget() + " in favour of " + retained);
		}
	}

	@Override
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
		return new Concept(lineItems[0]);
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find primitive concepts with redundant stated parents
		println ("Identifying concepts to process");
		Collection<Concept> checkMe = gl.getConcept(targetHierarchy).getDescendents(NOT_SET);
		List<Component> processMe = new ArrayList<>();
		
		int count = 0;
		nextConcept:
		for (Concept c : checkMe) {
			for (String itemOfInterest : itemsOfInterest) {
				if (c.isActive() && c.getFsn().startsWith(itemOfInterest)) {
					checkForXParent(c, itemOfInterest);
				}
			}
			if (++count  % 1000 == 0) {
				print(".");
			}
		}
		println ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	private void checkForXParent(Concept c, String itemOfInterest) throws TermServerScriptException {
		//What do we think X is?
		String X = SnomedUtils.deconstructFSN(c.getFsn())[0].replace(itemOfInterest,"");
		Severity severity = Severity.NONE;
		incrementSummaryInformation("ConceptsExamined");
		
		//A perfect match would be Finding of X or X finding
		Concept exactMatch = null;
		for (Concept thisParent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			String fsnPart = SnomedUtils.deconstructFSN(thisParent.getFsn())[0];
			if (fsnPart.equalsIgnoreCase(X) || fsnPart.equalsIgnoreCase("Finding of " + X) || fsnPart.equalsIgnoreCase(X + " finding")) {
				exactMatch = thisParent;
				incrementSummaryInformation("ExactMatch");
				break;
			}
		}
		
		//Otherwise can we find X in any parent that is on OE or Cof?
		Concept closeMatch = null;
		if (exactMatch == null) {
			for (Concept thisParent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
				if (thisParent.getFsn().contains(X)) {
					if (!containsOneOf(thisParent.getFsn(), itemsOfInterest)) {
						closeMatch = thisParent;
						incrementSummaryInformation("CloseMatch");
						severity = Severity.LOW;
						break;
					}
				}
			}
		}
		
		String[] tokensOfX = X.split(SPACE);
		
		//If we don't already have a desirable parent, can we find one to suggest?
		Concept suggestedAddition = null;
		if (exactMatch == null && closeMatch == null) {
			
			suggestedAddition = findConcept(X + " (finding)");
			
			if (suggestedAddition == null) {
				suggestedAddition = findConcept("Finding of " + X + " (finding)");
			}
			
			if (suggestedAddition == null) {
				suggestedAddition = findConcept(X + " finding (finding)");
			}
			
			if (suggestedAddition == null) {
				suggestedAddition = findConceptContainingAll(new String[] {"Finding of", X});
			}
			
			if (suggestedAddition == null && tokensOfX.length > 1) {
				suggestedAddition = findConceptContainingAll(X.split(SPACE));
			}
			
			if (suggestedAddition != null) {
				severity = Severity.MEDIUM;
				incrementSummaryInformation ("SuggestedAddition");
			}
		}
		
		//Try matching any of the words in X, if it has more than one word
		String possibleAdditionsStr = "";
		/*
		if (exactMatch == null && closeMatch == null && suggestedAddition == null && tokensOfX.length > 1) {
			Collection<Concept> possibleAdditions = new ArrayList<Concept>();
			int numberFound = findConceptsContainingAny(tokensOfX, possibleAdditions);
			if (possibleAdditions.size() > 0) {
				boolean isFirst = true;
				for (Concept possible : possibleAdditions) {
					if (!isFirst) {
						possibleAdditionsStr += ",\n";
					} else {
						isFirst = false;
					}
					possibleAdditionsStr += possible.toString();
				}
				if (numberFound > maxPossibilities) {
					possibleAdditionsStr += ",\n" + numberFound + " found";
				}
				incrementSummaryInformation ("PossibleAddition");
			}
		}
		*/
		
		//If we can find one to add, what would we suggest creating?
		String suggestedCreation="";
		if (exactMatch == null && closeMatch == null && suggestedAddition == null && possibleAdditionsStr.isEmpty()) {
			suggestedCreation = "Finding of " + X + " (finding)";
			severity = Severity.HIGH;
			incrementSummaryInformation ("SuggestedCreation");
		}
		
		report (c, severity, ReportActionType.INFO, 
				exactMatch == null? "" : exactMatch.toString(), 
				closeMatch == null ? "" : closeMatch.toString(), 
				suggestedAddition == null ? "" : suggestedAddition.toString(), 
				possibleAdditionsStr,
				suggestedCreation);
	}

	private Concept findConcept(String matchMe) throws TermServerScriptException {
		//Find an active concept whose fsn matches the target fsn
		for (Concept c : targetDomain) {
			if (c.getFsn().equalsIgnoreCase(matchMe)) {
				return c;
			}
		}
		return null;
	}
	
	private Concept findConceptContainingAll(String[] allTargetTerms) throws TermServerScriptException {
		//Find an active concept whose fsn matches the target fsn
		//TODO Could work all all the close matches and pick the one that is closest, rather than - here - the first one.
		for (Concept c : targetDomain) {
			//Don't process any of the terms we're starting with
			if (containsOneOf(c.getFsn(), itemsOfInterest)) {
				continue;
			}
			boolean containsAllTerms = true;
			for (String targetTerm : allTargetTerms) {
				if (!c.getFsn().contains(targetTerm)) {
					containsAllTerms = false;
				}
			}
			if (containsAllTerms) {
				return c;
			}
		}
		return null;
	}
	
	private int findConceptsContainingAny(String[] targetTerms, Collection<Concept> results) throws TermServerScriptException {
		int numberFound = 0;
		concept:
		for (Concept c : targetDomain) {
			//Don't process any of the terms we're starting with
			if (containsOneOf(c.getFsn(), itemsOfInterest)) {
				continue;
			}
			for (String targetTerm : targetTerms) {
				//Is this a target term we'd ignore eg "present"
				if (containsOneOf(targetTerm, genericTerms) || isStringInt(targetTerm)) {
					continue;
				}
				if (c.getFsn().contains(targetTerm)) {
					if (numberFound < maxPossibilities) {
						results.add(c);
					}
					numberFound++;
					continue concept;
				}
			}
		}
		return numberFound;
	}

	private boolean containsOneOf(String text, String[] matches) {
		for (String match : matches) {
			if (text.contains(match)) {
				return true;
			}
		}
		return false;
	}
	
	public boolean isStringInt(String s)
	{
	    try
	    {
	        Integer.parseInt(s);
	        return true;
	    } catch (NumberFormatException ex)
	    {
	        return false;
	    }
	}

}
