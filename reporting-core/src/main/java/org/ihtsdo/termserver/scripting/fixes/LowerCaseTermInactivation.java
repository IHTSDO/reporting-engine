package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

/*
Fix identifies otherwise identical lower case and upper case terms and inactivates
the lower case term
 */
public class LowerCaseTermInactivation extends BatchFix implements ScriptConstants{
	
	String subHierarchyStr = "27268008";  //Genus Salmonella (organism)
	
	protected LowerCaseTermInactivation(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		LowerCaseTermInactivation fix = new LowerCaseTermInactivation(null);
		try {
			fix.selfDetermining = true;
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
		int changesMade = inactivateLowerCaseTerm(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int inactivateLowerCaseTerm(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		MatchedSet m = findMatchingDescriptionSet(concept);
		
		if (m != null) {
			changesMade++;
			m.inactivate.setActive(false);
			m.inactivate.setEffectiveTime(null);
			m.inactivate.setInactivationIndicator(InactivationIndicator.ERRONEOUS);
			String msg = "Inactivated term '" + m.inactivate.getTerm() + "' due to presence of '" + m.keep.getTerm() + "'.";
			report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
		}
		return changesMade;
	}

	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		GraphLoader gl = GraphLoader.getGraphLoader();
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept>allDescendants = subHierarchy.getDescendants(NOT_SET);
		for (Concept thisConcept : allDescendants) {
			//Find concepts where there are otherwise identical lower and uppercase terms
			MatchedSet lowerCaseMatchingSet = findMatchingDescriptionSet(thisConcept);
			if (lowerCaseMatchingSet != null) {
				processMe.add(thisConcept);
			}
		}
		return processMe;
	}

	//Find active descriptions that match, where we want to keep the one that has the second
	//word capitalized, and inactivate the one that has the second word in lower case.
	private MatchedSet findMatchingDescriptionSet(Concept thisConcept) {
		for (Description upperCase : thisConcept.getDescriptions(ActiveState.ACTIVE)) {
			if (upperCase.getType().equals(DescriptionType.SYNONYM)) {  //Only comparing Synonyms 
				for (Description lowerCase : thisConcept.getDescriptions(ActiveState.ACTIVE)) {	
					if (lowerCase.getType().equals(DescriptionType.SYNONYM) && 
							upperCase != lowerCase && 
							upperCase.getTerm().equalsIgnoreCase(lowerCase.getTerm())) {
						if (lowerCase.getTerm().equals(SnomedUtils.initialCapitalOnly(lowerCase.getTerm()))) {
							return new MatchedSet (upperCase, lowerCase);
						}
					}
				}
			}
		}
		return null;
	}

	@Override
	protected List<Component> loadLine(String[] lineItems) throws TermServerScriptException {
		return null; // We will identify descriptions to edit from the snapshot
	}
	
	class MatchedSet {
		MatchedSet (Description keep, Description inactivate) {
			this.keep = keep;
			this.inactivate = inactivate;
		}
		Description keep;
		Description inactivate;
	}
}
