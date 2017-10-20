package org.ihtsdo.termserver.scripting.fixes;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NotImplementedException;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Component;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import us.monoid.json.JSONObject;

/*
Fix finds terms where the 2nd word is lower case and no equivalent upper case term exists.
The lower case term is inactivated and replaced with the upper case version.
 */
public class ReplaceLowerCaseTerms extends BatchFix implements RF2Constants{
	
	String subHierarchyStr = "27268008";  //Genus Salmonella (organism)
	String[] exceptions = new String[] {"398393000", "110378009"};
	String firstWord = "Salmonella";
	
	protected ReplaceLowerCaseTerms(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		ReplaceLowerCaseTerms fix = new ReplaceLowerCaseTerms(null);
		try {
			fix.useAuthenticatedCookie = true;
			fix.selfDetermining = true;
			fix.init(args);
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			//We won't incude the project export in our timings
			fix.startTimer();
			Batch batch = fix.formIntoBatch();
			fix.batchProcess(batch);
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}
	
	protected void init(String[] args) throws TermServerScriptException, IOException {
		super.init(args);
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException {
		Concept loadedConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = replaceLowerCaseTerm(task, loadedConcept);
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

	private int replaceLowerCaseTerm(Task task, Concept concept) {
		int changesMade = 0;
		List<Description> lowerCaseTerms = findUnmatchedLowerCaseTerms(concept);
		
		for (Description lower : lowerCaseTerms) {
			changesMade++;
			String[] words = lower.getTerm().split(" ");
			//Skip over Roman Numerals.
			//Also remove any dashes in the 2nd word.
			if (words.length > 2 && isRomanNumeral(words[1])) {
				words[2] = SnomedUtils.capitalize(words[2]).replace("-", "");
			} else {
				words[1] = SnomedUtils.capitalize(words[1]).replace("-", "");
			}
			String newTerm = StringUtils.join(words, " ");
			String msg;
			boolean replacementMade = false;
			if (!termAlreadyExists(concept, newTerm)) {
				Description upper = lower.clone(null);
				upper.setTerm(newTerm);
				upper.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE.toString());
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

	/**
	 * Identify any concept 
	 * @return
	 * @throws TermServerScriptException
	 */
	protected List<Component> identifyComponentsToProcess() throws TermServerScriptException {
		List<Component> processMe = new ArrayList<Component>();
		GraphLoader gl = GraphLoader.getGraphLoader();
		Concept subHierarchy = gl.getConcept(subHierarchyStr);
		Set<Concept>allDescendants = subHierarchy.getDescendents(NOT_SET);
		for (Concept thisConcept : allDescendants) {
			//Don't process the exceptions
			if (ArrayUtils.contains(exceptions, thisConcept.getConceptId())) {
				continue;
			}
			//Find terms which only exist in lower case
			List<Description> unmatchedLowerCaseTerms = findUnmatchedLowerCaseTerms(thisConcept);
			if (unmatchedLowerCaseTerms.size() > 0) {
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
				term = SnomedUtils.deconstructFSN(term)[0];
			}
			String [] words = term.split(" ");
			words = removeRomanNumerals(words);
			if (words.length == 2 && 
					words[0].equals(firstWord) && 
					!words[1].matches(".*\\d+.*") &&
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
	protected Concept loadLine(String[] lineItems) throws TermServerScriptException {
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
