package org.ihtsdo.termserver.scripting.fixes;

import java.util.*;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.domain.*;

/*
Fix finds terms where the 2nd word is lower case and no equivalent upper case term exists.
The lower case term is inactivated and replaced with the upper case version.
 */
public class ReplaceLowerCaseTerms extends BatchFix implements ScriptConstants{
	
	String subHierarchyStr = "27268008";  //Genus Salmonella (organism)
	String[] exceptions = new String[] {"398393000", "110378009"};
	String firstWord = "Salmonella";
	
	protected ReplaceLowerCaseTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		ReplaceLowerCaseTerms fix = new ReplaceLowerCaseTerms(null);
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
		int changesMade = replaceLowerCaseTerm(t, loadedConcept);
		if (changesMade > 0) {
			updateConcept(t, loadedConcept, info);
		}
		return changesMade;
	}

	private int replaceLowerCaseTerm(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> lowerCaseTerms = findUnmatchedLowerCaseTerms(concept);
		
		for (Description lower : lowerCaseTerms) {
			changesMade++;
			String[] words = lower.getTerm().split(" ");
			//Skip over Roman Numerals.
			//Also remove any dashes in the 2nd word.
			if (words.length > 2 && isRomanNumeral(words[1])) {
				words[2] = StringUtils.capitalize(words[2]).replace("-", "");
			} else {
				words[1] = StringUtils.capitalize(words[1]).replace("-", "");
			}
			String newTerm = StringUtils.join(words, " ");
			String msg;
			boolean replacementMade = false;
			if (!termAlreadyExists(concept, newTerm)) {
				Description upper = lower.clone(null);
				upper.setTerm(newTerm);
				upper.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				concept.addDescription(upper);
				replacementMade = true;
				msg = "Replaced term '" + lower.getTerm() + "' with '" + upper.getTerm() + "'.";
			} else {
				msg = "Inactivated term '" + lower.getTerm() + "', replacement already exists.";
			}
			
			//We still want terms to turn up in searches, so just make terms with 
			//dashes removed unacceptable, not inative
			if (lower.getTerm().contains(DASH)) {
				//If not inactivating, setting the acceptability to 'Not Acceptable'
				//with an empty map
				lower.setAcceptabilityMap(new HashMap<String, Acceptability> ());
				lower.setEffectiveTime(null);
				msg = "Removing acceptability of '" + lower.getTerm() + "' " + (replacementMade?msg:"");
			} else {
				lower.setActive(false);
				lower.setEffectiveTime(null);
				lower.setInactivationIndicator(InactivationIndicator.ERRONEOUS);
			}
			report(task, concept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
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

	@Override
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		GraphLoader gl = GraphLoader.getGraphLoader();
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept>allDescendants = subHierarchy.getDescendants(NOT_SET);
		for (Concept thisConcept : allDescendants) {
			//Don't process the exceptions
			if (ArrayUtils.contains(exceptions, thisConcept.getConceptId())) {
				continue;
			}
			//Find terms which only exist in lower case
			List<Description> unmatchedLowerCaseTerms = findUnmatchedLowerCaseTerms(thisConcept);
			if (!unmatchedLowerCaseTerms.isEmpty()) {
				processMe.add(thisConcept);
			}
		}
		return processMe;
	}

	//Find active descriptions that consist of two words, where the second word is 
	//lower case, and there is no other active term where it is upper case.
	private List<Description> findUnmatchedLowerCaseTerms(Concept thisConcept) {
		List<Description> unmatchedLowerCaseTerms = new ArrayList<Description>();
		for (Description lowerCase : thisConcept.getDescriptions(ActiveState.ACTIVE)) {
			//Do we only have two words?  And is word 2 all lower case?
			//No interested in words containing numbers
			//Also if it's an FSN, strip off the semantic tag
			String term = lowerCase.getTerm();
			if (lowerCase.getType().equals(DescriptionType.FSN)) {
				term = SnomedUtilsBase.deconstructFSN(term)[0];
			}
			String [] words = term.split(" ");
			words = removeRomanNumerals(words);
			if (words.length == 2 && 
					words[0].equals(firstWord) && 
					!words[1].matches("\\d") &&
					!words[1].contains("[") &&
					words[1].equals(words[1].toLowerCase())) {
				//Are there no other terms differing only in case?
				if (!hasCaseDifferenceTerm(thisConcept, lowerCase)) {
					unmatchedLowerCaseTerms.add(lowerCase);
				}
			}
		}
		return unmatchedLowerCaseTerms;
	}

	private String[] removeRomanNumerals(String[] words) {
		//Is the 2nd word in composed only of Is, Vs & Xs, and not longer than 3 letters?
		if (words.length > 2 && isRomanNumeral(words[1])) {
			String[] withoutRNs = new String[words.length - 1];
			int newPos = 0;
			for (int i=0; i<words.length; i++) {
				if (i!=1) {
					withoutRNs[newPos++] = words[i];
				}
			}
			return withoutRNs;
		}
		return words;
	}
	
	private boolean isRomanNumeral (String word) {
		return (word.length() <= 3 &&
				word.replace("I","").replace("V", "").replace("X", "").isEmpty());
	}

	/*
	 * Returns true if an active description exists which differs only in case.
	 */
	private boolean hasCaseDifferenceTerm(Concept concept,
			Description matchTerm) {
		for (Description thisTerm : concept.getDescriptions(ActiveState.ACTIVE)) {
			//Check it's not exactly the same
			if (!matchTerm.getTerm().equals(thisTerm.getTerm())) {
				//Check if it matches ignoring case
				if (matchTerm.getTerm().equalsIgnoreCase(thisTerm.getTerm())) {
					return true;
				}
			}
		}
		return false;
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
