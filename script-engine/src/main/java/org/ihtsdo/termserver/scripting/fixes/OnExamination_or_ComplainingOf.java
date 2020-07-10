package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.BrowserClient;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
 * QI-300
For Jim.  In the Clinical Findings hierarchy, find concepts starting "On Examination - X"
and "Complaining of X" and see if they have X on its own as a parent

*/
public class OnExamination_or_ComplainingOf extends BatchFix implements RF2Constants{
	
	String[] itemsOfInterest = new String[] { "On examination - ", "Complaining of "};
	String targetHierarchy = "404684003"; // |Clinical finding (finding)|
	//String except = "64572001"; // |Disease (disorder)|
	Collection<Concept> targetDomain;
	String[] genericTerms = new String[] {"present", "external", "microscopy", "reflex", 
			"defect", "group", "week", "size", "organism", "female", "male", "positive"};
	int maxPossibilities = 7;
	BrowserClient browserClient;
	
	protected OnExamination_or_ComplainingOf(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, InterruptedException {
		OnExamination_or_ComplainingOf fix = new OnExamination_or_ComplainingOf(null);
		try {
			fix.reportNoChange = true;
			fix.selfDetermining = true;
			fix.runStandAlone = true;
			fix.additionalReportColumns = "Exact Match - Existing Parent, Close Match - Existing Parent, Suggested Addition - Existing Concept, possibleAdditions, suggestedCreation";
			fix.init(args);
			fix.loadProjectSnapshot(true); 
			fix.postInit();
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	public void postInit() throws TermServerScriptException {
		//The target domain is Clinical Finding Hierarchy, with the disorders removed
		targetDomain = gl.getConcept(targetHierarchy).getDescendents(NOT_SET);
		//Collection<Concept> exceptions = gl.getConcept(except).getDescendents(NOT_SET);
		//targetDomain.removeAll(exceptions);
		browserClient = new BrowserClient();
		super.postInit();
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException {
		
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = removeRedundantParents(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
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
		
		Set<Relationship> parentRels = new HashSet<Relationship> (loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
																				IS_A,
																				ActiveState.ACTIVE));
		
		for (Relationship parentRel : parentRels) {
			//If we have a more specific parent, delete or inactivate this one
			Concept moreSpecific = findMoreSpecificCoparent(parentRel.getTarget(), loadedConcept);
			if (moreSpecific != null) {
				removeParentRelationship(task, parentRel, loadedConcept, moreSpecific.toString(), null);
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
		Set<Relationship> parentRels = loadedConcept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, 
										IS_A,
										ActiveState.ACTIVE);
		for (Relationship r : parentRels) {
			parents.add(gl.getConcept(r.getTarget().getConceptId()));
		}
		return parents;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return Collections.singletonList(new Concept(lineItems[0]));
	}
	
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		//Find primitive concepts with redundant stated parents
		info ("Identifying concepts to process");
		Collection<Concept> checkMe = gl.getConcept(targetHierarchy).getDescendents(NOT_SET);
		List<Component> processMe = new ArrayList<>();
		
		int count = 0;
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
		info ("Identified " + processMe.size() + " concepts to process");
		return processMe;
	}

	private void checkForXParent(Concept c, String itemOfInterest) throws TermServerScriptException {
		//What do we think X is?
		String X = SnomedUtils.deconstructFSN(c.getFsn())[0].replace(itemOfInterest,"").trim();
		Severity severity = Severity.NONE;
		incrementSummaryInformation("ConceptsExamined");
		
		//A perfect match would be Finding of X or X finding
		Concept exactMatch = null;
		for (Concept thisParent : c.getParents(CharacteristicType.INFERRED_RELATIONSHIP)) {
			String fsnPart = SnomedUtils.deconstructFSN(thisParent.getFsn())[0];
			if (fsnPart.equalsIgnoreCase(X) || fsnPart.equalsIgnoreCase("Finding of " + X) || fsnPart.equalsIgnoreCase(X + " finding")) {
				exactMatch = thisParent;
				incrementSummaryInformation("ExactMatch with FSN");
				break;
			}
			//Check the other descriptions of parents also
			for (Description d : thisParent.getDescriptions(ActiveState.ACTIVE)) {
				if (d.getTerm().equalsIgnoreCase(X)) {
					exactMatch = thisParent;
					incrementSummaryInformation("ExactMatch with synonym");
					break;
				}
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
		if (exactMatch == null && tokensOfX.length > 1) {
			String searchTerms = Arrays.stream(tokensOfX)
					.map(s -> s.substring(0, s.length() > 4 ? 4 : s.length()))
					.collect(Collectors.joining(" ")).replaceAll("[^A-Za-z0-9\\s]", "");
			List<Concept> possibilities = browserClient.findConcepts(searchTerms, "finding", 3);
			possibleAdditionsStr = possibilities.stream()
					.map(p -> p.toString())
					.filter(s -> !s.contains("examination"))
					.filter(s -> !s.contains("Complaining"))
					.collect(Collectors.joining(", "));
		}
		
		//If we can find one to add, what would we suggest creating?
		String suggestedCreation="";
		if (exactMatch == null && closeMatch == null && suggestedAddition == null && possibleAdditionsStr.isEmpty()) {
			suggestedCreation = X + " (finding)";
			severity = Severity.HIGH;
			incrementSummaryInformation ("SuggestedCreation");
		}
		
		report ((Task)null, c, severity, ReportActionType.INFO, 
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
	
	private boolean containsOneOf(String text, String[] matches) {
		for (String match : matches) {
			if (text.contains(match)) {
				return true;
			}
		}
		return false;
	}
	
}
