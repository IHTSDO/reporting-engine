package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.*;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;

import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.*;

/*
	Makes modifications to terms, driven by an input CSV file
	See DRUGS-291
*/
public class DrugsReTerming extends DrugBatchFix implements ScriptConstants{

	protected DrugsReTerming(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException {
		DrugsReTerming fix = new DrugsReTerming(null);
		try {
			fix.init(args);
			fix.inputFileHasHeaderRow = true;
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.processFile();
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task t, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept loadedConcept = loadConcept(concept, t.getBranchPath());
		int changesMade = 0;
		if (loadedConcept.isActive() == false) {
			report(t, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive.  No changes attempted");
		} else {
			changesMade = remodelDescriptions(t, concept, loadedConcept);
			if (changesMade > 0) {
				updateConcept(t, loadedConcept, info);
			}
		}
		return changesMade;
	}

	private int remodelDescriptions(Task task, Concept remodelledConcept,
			Concept tsConcept) throws TermServerScriptException {
		ConceptChange change = (ConceptChange)remodelledConcept;
		int changesMade = 0;
		//The input file has some spaces added around equals, so strip these out before comparison and make lower case
		String expectedFsnStrip = change.getCurrentTerm().replaceAll(SPACE, "").toLowerCase();
		String actualFsnStrip = tsConcept.getFsn().replaceAll(SPACE, "").toLowerCase();
		if (!expectedFsnStrip.equals(actualFsnStrip)) {
			report(task, tsConcept, Severity.CRITICAL, ReportActionType.VALIDATION_ERROR, "Existing FSN did not meet change input file expectations: " + change.getCurrentTerm());
		} else {
			//Firstly, inactivate and replace the FSN
			Description fsn = tsConcept.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE).iterator().next();
			Description replacement = fsn.clone(null);
			replacement.setTerm(change.getFsn());
			String termPart = SnomedUtils.deconstructFSN(change.getFsn())[0];
			CaseSignificance cs = isCaseSensitive(termPart)? CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE : CaseSignificance.CASE_INSENSITIVE;
			replacement.setCaseSignificance(cs);
			replacement.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.PREFERRED_BOTH));
			fsn.inactivateDescription(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			tsConcept.addDescription(replacement);
			changesMade += 2;  //One inactivation and one addition
			
			//Now synonyms
			changesMade += remodelSynonyms(task, remodelledConcept, tsConcept);
		}
		return changesMade;
	}

	private int remodelSynonyms(Task task, Concept remodelledConcept,
			Concept tsConcept) throws TermServerScriptException {
		int changesMade = 0;
		//Now inactivate all synonyms, unless we're keeping them
		List<Description> sortedDescriptions = getDescriptionsSorted(tsConcept);
		for (Description d : sortedDescriptions) {
			//Skip the FSN, we've dealt with that separately
			if (d.getType().equals(DescriptionType.FSN)) {
				continue;
			}
			Description keeping = findDescription (remodelledConcept, d.getTerm());
			if (keeping!=null) {
				if (!d.isActive()) {
					report(task, tsConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Inactivated description being made active");
					d.setActive(true);
					changesMade++;
				}
				//Copy the acceptability of the incoming term, onto the original
				if (!equals(d.getAcceptabilityMap(), keeping.getAcceptabilityMap())) {
					report(task, tsConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Modifying acceptability of existing description: " + d);
					d.setAcceptabilityMap(keeping.getAcceptabilityMap());
					changesMade++;
				}
				//And remove the description from our change set, so we don't add it again
				remodelledConcept.getDescriptions().remove(keeping);
				changesMade += checkCaseSensitivity(task, tsConcept, d);
			} else {
				//Inactivate all existing active descriptions that aren't being reused.
				if (d.isActive()) {
					if (d.isPreferred()) {
						report(task, tsConcept, Severity.HIGH, ReportActionType.DESCRIPTION_CHANGE_MADE, "Inactivating preferred term: " + d);
					} else {
						report(task, tsConcept, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Inactivating existing synonym: " + d);
					}
					d.inactivateDescription(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					changesMade++;
				}
			}
		}
		//Add back in any remaining descriptions from our change set
		for (Description newDesc : remodelledConcept.getDescriptions()) {
			tsConcept.addDescription(newDesc);
			changesMade++;
		}
		return changesMade;
	}

	private int checkCaseSensitivity(Task task, Concept tsConcept, Description d) throws TermServerScriptException {
		//Do we need to change the case sensitivity of this existing term?
		int changesMade = 0;
		boolean isCaseSensitive = isCaseSensitive(d.getTerm());
		CaseSignificance caseSig =d.getCaseSignificance();
		switch (caseSig) {
			case INITIAL_CHARACTER_CASE_INSENSITIVE : 
				if (!isCaseSensitive) {
					d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
					report(task, tsConcept, Severity.HIGH, ReportActionType.DESCRIPTION_CHANGE_MADE, "Existing cI sensitivity changed to ci : " + d);
					changesMade++;
				}
				break;
			case ENTIRE_TERM_CASE_SENSITIVE : 
				break;
			case CASE_INSENSITIVE : 
				if (isCaseSensitive) {
					d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
					report(task, tsConcept, Severity.HIGH, ReportActionType.DESCRIPTION_CHANGE_MADE, "Existing ci sensitivity changed to cI : " + d);
					changesMade++;
				}
				break;
			default : throw new TermServerScriptException("Unrecognised case significance " + caseSig + " for " + d);
		}
		return changesMade;
	}

	//Returns a list of descriptions on a concept, but with the active ones first
	private List<Description> getDescriptionsSorted(Concept tsConcept) {
		List<Description> sortedDescriptions = tsConcept.getDescriptions(ActiveState.ACTIVE);
		sortedDescriptions.addAll(tsConcept.getDescriptions(ActiveState.INACTIVE));
		return sortedDescriptions;
	}

	/**
	 * @return true if two acceptability maps are entirely identical
	 */
	private boolean equals(Map<String, Acceptability> origAcceptabilityMap,
			Map<String, Acceptability> newAcceptabilityMap) {
		if (origAcceptabilityMap == null && newAcceptabilityMap != null) {
			return false;
		} else if (origAcceptabilityMap != null && newAcceptabilityMap == null) {
			return false;
		} else if (origAcceptabilityMap.size() != newAcceptabilityMap.size()) {
			return false;
		} else {
			for (Map.Entry<String, Acceptability> mapEntry: origAcceptabilityMap.entrySet()) {
				if (!newAcceptabilityMap.containsKey(mapEntry.getKey())) {
					return false;
				} else {
					Acceptability origAcceptability = origAcceptabilityMap.get(mapEntry.getKey());
					Acceptability newAcceptability = newAcceptabilityMap.get(mapEntry.getKey());
					if (!origAcceptability.equals(newAcceptability)) {
						return false;
					}
				}
			}
		}
		return true;
	}

	//Return the first description that equals the term
	private Description findDescription(Concept remodelledConcept, String term) {
		for (Description d : remodelledConcept.getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().equals(term)) {
				return d;
			}
		}
		return null;
	}

	protected Batch formIntoBatch (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerScriptException {
		Batch batch = new Batch(getReportName());
		Task task = batch.addNewTask(getNextAuthor());

		for (Concept thisConcept : allConcepts) {
			if (((ConceptChange) thisConcept).getSkipReason() != null) {
				report(task, thisConcept, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Concept marked as excluded");
			} else {
				if (task.size() >= taskSize) {
					task = batch.addNewTask(getNextAuthor());
				}
				task.add(thisConcept);
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_TO_PROCESS, allConcepts);
		return batch;
	}

	/*
		A 0) Comment
		Any row with "Exclude" in the string will be skipped, but reported
		B 1) id
		C 2) Original FSN
		Will be checked against the current FSN in the TS and if different, row skipped and flagged.
		D 3) FSN generated from modeled ingredient using original substance names
		Column Ignored
		E 4) Original matches Generate FSN generated from modeled ingredient using original substance names
		Column Ignore
		F 5) FSN generated from modeled ingredient using INN conventions
		This column will be used for the FSN
		G 6)Synonym generated from modeled ingredients separated by plus signs and following INN
		Convert plus signs to 'and'   and put ingredients into alphabetical order
		This will be the PT in both dialects UNLESS column J is populated, in which case it will be the GB PT, and acceptable in US
		H 7) Original matches Generate FSN generated from modeled ingredient separated by plus signs and following INN
		Column Ignore
		I 8) Synonym generated from FSN without semantic tag
		Will be an acceptable synonym in all dialects.
		J 9) Synonym generated from modeled ingredients following USAN-acceptable naming with plus sign
		Where exists, this will be the US PT (and acceptable in GB)
		K 10) Synonym generated from FSN without semtag following USAN
		Where exists, this will be an acceptable US Synonym EDIT: acceptable in both dialects
		Comment	id	Original FSN	FSN generated from modeled ingredient using original substance names	Original matches Generate FSN generated from modeled ingredient using original substance names	FSN generated from modeled ingredient using INN conventions	Synonym generated from modeled ingredients separated by plus signs and following INN	Original matches Generate FSN generated from modeled ingredient separated by plus signs and following INN	Synonym generated from FSN without semantic tag	Synonym generated from modeled ingredients following USAN-acceptable naming with plus sign	Synonym generated from FSN without semtag following USAN																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																					

	 */
	@Override
	protected List<Component> loadLine(String[] items) throws TermServerScriptException {

		String sctid = items[1];
		ConceptChange concept = new ConceptChange(sctid);
		concept.setConceptType(ConceptType.MEDICINAL_PRODUCT);
		concept.setCurrentTerm(items[2]);
		concept.setFsn(items[5]);
		//Have we been told to exclude this row?
		if (items[0].toLowerCase().contains("exclude")) {
			concept.setSkipReason("Marked as excluded");
		} else {
			//If we have a columns 8 & 9, then split the preferred terms into US and GB separately
			addSynonyms(concept, items);
		}
		return Collections.singletonList(concept);
	}

	private void addSynonyms(ConceptChange concept, String[] items) {
		if (items.length > 9 && !items[9].isEmpty()) {
			addSynonym(concept, items[6], AcceptabilityMode.PREFERRED_GB );
			addSynonym(concept, items[9], AcceptabilityMode.PREFERRED_US );
			if (items.length > 10 &&  !items[10].isEmpty()) {
				addSynonym(concept, items[10], AcceptabilityMode.ACCEPTABLE_BOTH);
			}
		} else {
			addSynonym(concept, items[6], AcceptabilityMode.PREFERRED_BOTH );
		}
		addSynonym(concept, items[8], AcceptabilityMode.ACCEPTABLE_BOTH);
	}

	private void addSynonym(ConceptChange concept, String term, AcceptabilityMode acceptabilityMode) {
		if (term.isEmpty()) {
			return;
		}
		if (term.contains("+")) {
			//Replace + with 'and' and order terms
			term = normalizeMultiIngredientTerm(term);
		}
		Description d = new Description();
		d.setTerm(term);
		d.setActive(true);
		d.setType(DescriptionType.SYNONYM);
		d.setLang(LANG_EN);
		if (isCaseSensitive(term)) {
			d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		} else {
			d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
		}
		d.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(acceptabilityMode));
		d.setConceptId(concept.getConceptId());
		concept.addDescription(d);
	}
	
	private boolean isCaseSensitive(String term) {
		String afterFirst = term.substring(1);
		boolean allLowerCase = afterFirst.equals(afterFirst.toLowerCase());
		return !allLowerCase;
	}

	protected String normalizeMultiIngredientTerm(String term) {

		String[] ingredients = term.split(INGREDIENT_SEPARATOR_ESCAPED);
		//ingredients should be in alphabetical order, also trim spaces
		for (int i = 0; i < ingredients.length; i++) {
			ingredients[i] = ingredients[i].toLowerCase().trim();
		}
		Arrays.sort(ingredients);

		//Reform with 'and' and only first letter capitalized
		boolean isFirstIngredient = true;
		term = "";
		for (String thisIngredient : ingredients) {
			if (!isFirstIngredient) {
				term += " and ";
			}
			
			term += thisIngredient.toLowerCase();
			isFirstIngredient = false;
		}
		return StringUtils.capitalizeFirstLetter(term);
	}

}
