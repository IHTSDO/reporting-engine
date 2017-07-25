package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.client.SnowOwlClientException;
import org.ihtsdo.termserver.scripting.domain.Batch;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.ConceptChange;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;

import com.b2international.commons.StringUtils;

import us.monoid.json.JSONObject;

/*
	Makes modifications to terms, driven by an input CSV file
	See DRUGS-291
*/
public class DrugsReTerming extends BatchFix implements RF2Constants{
	
	enum AcceptabilityMode { PREFERRED_BOTH, PREFERRED_US, PREFERRED_GB, ACCEPTABLE_BOTH, ACCEPTABLE_US }
	
	String[] author_reviewer = new String[] {targetAuthor};
	
	protected DrugsReTerming(BatchFix clone) {
		super(clone);
	}

	public static void main(String[] args) throws TermServerScriptException, IOException, SnowOwlClientException, InterruptedException {
		DrugsReTerming fix = new DrugsReTerming(null);
		try {
			fix.init(args);
			fix.inputFileDelimiter = COMMA;
			fix.inputFileHasHeaderRow = true;
			//Recover the current project state from TS (or local cached archive) to allow quick searching of all concepts
			fix.loadProjectSnapshot(false); //Load all descriptions
			fix.startTimer();
			println ("Processing started.  See results: " + fix.reportFile.getAbsolutePath());
			fix.processFile();
			println ("Processing complete.  See results: " + fix.reportFile.getAbsolutePath());
		} finally {
			fix.finish();
		}
	}

	@Override
	public int doFix(Task task, Concept concept, String info) throws TermServerScriptException, ValidationFailure {
		Concept tsConcept = loadConcept(concept, task.getBranchPath());
		int changesMade = 0;
		if (tsConcept.isActive() == false) {
			report(task, concept, Severity.HIGH, ReportActionType.VALIDATION_ERROR, "Concept is inactive.  No changes attempted");
		} else {
			changesMade = remodelDescriptions(task, concept, tsConcept);
			if (changesMade > 0) {
				try {
					String conceptSerialised = gson.toJson(tsConcept);
					debug ((dryRun?"Dry run updating":"Updating") + " state of " + tsConcept + info);
					if (!dryRun) {
						tsClient.updateConcept(new JSONObject(conceptSerialised), task.getBranchPath());
					}
					report(task, concept, Severity.LOW, ReportActionType.CONCEPT_CHANGE_MADE, "Concept successfully remodelled. " + changesMade + " changes made.");
				} catch (Exception e) {
					report(task, concept, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to save changed concept to TS: " + e.getClass().getSimpleName()  + " - " + e.getMessage());
					e.printStackTrace();
				}
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
			Description fsn = tsConcept.getDescriptions(Acceptability.PREFERRED, DescriptionType.FSN, ActiveState.ACTIVE).get(0);
			Description replacement = fsn.clone(null);
			replacement.setTerm(change.getFsn());
			replacement.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE.toString());
			replacement.setAcceptabilityMap(createAcceptabilityMap(AcceptabilityMode.PREFERRED_BOTH));
			fsn.inactivateDescription(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			tsConcept.addDescription(replacement);
			changesMade += 2;  //One inactivation and one addition
			
			//Now synonyms
			changesMade += remodelSynonyms(task, remodelledConcept, tsConcept);
		}
		return changesMade;
	}

	private int remodelSynonyms(Task task, Concept remodelledConcept,
			Concept tsConcept) {
		int changesMade = 0;
		//Now inactivate all synonyms, unless we're keeping them
		for (Description d : tsConcept.getDescriptions(ActiveState.BOTH)) {
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
			} else {
				//Inactivate all existing active descriptions that aren't being reused.
				if (d.isActive()) {
					if (d.isPreferred()) {
						report(task, tsConcept, Severity.HIGH, ReportActionType.DESCRIPTION_CHANGE_MADE, "Inactivating preferred term: " + d);
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
		for (Description d : remodelledConcept.getDescriptions()) {
			if (d.getTerm().equals(term)) {
				return d;
			}
		}
		return null;
	}

	@Override
	protected Batch formIntoBatch (String fileName, List<Concept> allConcepts, String branchPath) throws TermServerScriptException {
		Batch batch = new Batch(getReportName());
		Task task = batch.addNewTask();

		for (Concept thisConcept : allConcepts) {
			if (((ConceptChange) thisConcept).getSkipReason() != null) {
				report(task, thisConcept, Severity.LOW, ReportActionType.VALIDATION_CHECK, "Concept marked as excluded");
			} else {
				if (task.size() >= taskSize) {
					task = batch.addNewTask();
					setAuthorReviewer(task, author_reviewer);
				}
				task.add(thisConcept);
			}
		}
		addSummaryInformation("Tasks scheduled", batch.getTasks().size());
		addSummaryInformation(CONCEPTS_PROCESSED, allConcepts);
		return batch;
	}

	private void setAuthorReviewer(Task task, String[] author_reviewer) {
		task.setAssignedAuthor(author_reviewer[0]);
		if (author_reviewer.length > 1) {
			task.setReviewer(author_reviewer[1]);
		}
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
		Where exists, this will be an acceptable US Synonym
		Comment	id	Original FSN	FSN generated from modeled ingredient using original substance names	Original matches Generate FSN generated from modeled ingredient using original substance names	FSN generated from modeled ingredient using INN conventions	Synonym generated from modeled ingredients separated by plus signs and following INN	Original matches Generate FSN generated from modeled ingredient separated by plus signs and following INN	Synonym generated from FSN without semantic tag	Synonym generated from modeled ingredients following USAN-acceptable naming with plus sign	Synonym generated from FSN without semtag following USAN																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																																					

	 */
	@Override
	protected Concept loadLine(String[] items) throws TermServerScriptException {

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
		return concept;
	}

	private void addSynonyms(ConceptChange concept, String[] items) {
		if (items.length > 9) {
			addSynonym(concept, items[6], AcceptabilityMode.PREFERRED_GB );
			addSynonym(concept, items[9], AcceptabilityMode.PREFERRED_US );
			if (items.length > 10) {
				addSynonym(concept, items[10], AcceptabilityMode.ACCEPTABLE_US);
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
			d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE.toString());
		} else {
			d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE.toString());
		}
		d.setAcceptabilityMap(createAcceptabilityMap(acceptabilityMode));
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

	private Map<String, Acceptability> createAcceptabilityMap(AcceptabilityMode acceptabilityMode) {
		Map<String, Acceptability> aMap = new HashMap<String, Acceptability>();
		//Note that when a term is preferred in one dialect, we'll make it acceptable in the other
		switch (acceptabilityMode) {
			case PREFERRED_BOTH :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.PREFERRED);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.PREFERRED);
				break;
			case PREFERRED_US :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.PREFERRED);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				break;
			case PREFERRED_GB :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.PREFERRED);
				break;
			case ACCEPTABLE_BOTH :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				aMap.put(GB_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				break;
			case ACCEPTABLE_US :
				aMap.put(US_ENG_LANG_REFSET, Acceptability.ACCEPTABLE);
				break;
		}
		return aMap;
	}
}
