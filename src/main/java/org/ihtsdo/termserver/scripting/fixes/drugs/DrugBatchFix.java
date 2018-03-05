package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hamcrest.text.IsEqualIgnoringCase;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Relationship;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugUtils;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public abstract class DrugBatchFix extends BatchFix implements RF2Constants{
	
	enum AcceptabilityMode { PREFERRED_BOTH, PREFERRED_US, PREFERRED_GB, ACCEPTABLE_BOTH, ACCEPTABLE_US }

	static Map<String, String> wordSubstitution = new HashMap<String, String>();
	static {
		wordSubstitution.put("acetaminophen", "paracetamol");
	}
	
	String [] unwantedWords = new String[] { "preparation", "product" };
	String [] forceCS = new String[] { "N-" };
	
	static final String productPrefix = "Product containing ";
	static final String find = "/1 each";
	static final String replace = "";
	static final String PLUS = "+";
	static final String PLUS_ESCAPED = "\\+";
	static final String PLUS_SPACED_ESCAPED = " \\+ ";
	static final String IN = " in ";
	static final String ONLY = "only ";
	static final String AND = " and ";
	static final String newSemanticTag = "(medicinal product)";
	
	List<String> doseForms = new ArrayList<String>();
	private String[] vitamins = new String[] {" A ", " B ", " C ", " D ", " E ", " G "};

	protected DrugBatchFix(BatchFix clone) {
		super(clone);
	}
	
	protected int normalizeDrugTerms(Task task, Concept concept) throws TermServerScriptException {
		int changesMade = 0;
		for (Description d : concept.getDescriptions(ActiveState.ACTIVE)) {

			String replacementTerm = d.getTerm();
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			
			ensureCaptialization(d);
			


			//If this is an FSN, make sure we start with the "Product containing" prefix
			//and force the semantic tag
			if (isFSN) {
				if (!replacementTerm.startsWith(productPrefix)) {
					//If we're not case sensitive, make the first letter lower case
					if (!d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
						replacementTerm = SnomedUtils.deCapitalize(replacementTerm);
					}
					replacementTerm = productPrefix + replacementTerm;
				}
				String[] fsnParts = SnomedUtils.deconstructFSN(replacementTerm);
				replacementTerm = fsnParts[0] + " " + newSemanticTag;
			}
			
			//Have we made any changes?  Create a new description if so
			Description replacement = d.clone(null);
			replacement.setTerm(replacementTerm);
			if (!replacementTerm.equals(d.getTerm())) {
				boolean doReplacement = true;
				if (termAlreadyExists(concept, replacementTerm)) {
					report(task, concept, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Replacement term already exists: '" + replacementTerm + "' inactivating abnormal term only.");
					doReplacement = false;
				}
				
				String msg;
				//Has our description been published?  Remove entirely if not
				if (d.isReleased()) {
					d.setActive(false);
					d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
					msg = "Inactivated desc ";
				} else {
					concept.getDescriptions().remove(d);
					msg = "Deleted desc ";
				}
				msg +=  d.getDescriptionId() + " - '" + d.getTerm().toString();
				if (doReplacement) {
					msg += "' in favour of '" + replacementTerm + "'";
					concept.addDescription(replacement);
				}
				changesMade++;
				report(task, concept, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
			}
			
			//If this is the FSN, then we should have another description without the semantic tag as an acceptable term
			if (d.getType().equals(DescriptionType.FSN)) {
				Description fsnCounterpart = replacement.clone(null);
				String counterpartTerm = SnomedUtils.deconstructFSN(fsnCounterpart.getTerm())[0];
				
				if (!termAlreadyExists(concept, counterpartTerm)) {
					fsnCounterpart.setTerm(counterpartTerm);
					report(task, concept, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, "FSN Counterpart added: " + counterpartTerm);
					fsnCounterpart.setType(DescriptionType.SYNONYM);
					fsnCounterpart.setAcceptabilityMap(createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
					concept.addDescription(fsnCounterpart);
				}
			}
		}
		return changesMade;
	}
	
	protected int ensureDrugTermsConform(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		
		//This function will split out the US / GB terms if the ingredients show variance where the product does not
		validateUsGbVarianceInIngredients(t,c);
		
		if (c.getConceptId().equals("109044007")) {
			debug ("Check this concept");
		}
		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			boolean isGbPT = d.isPreferred(GB_ENG_LANG_REFSET);
			boolean isUsPT = d.isPreferred(US_ENG_LANG_REFSET);
			boolean isPT = (isGbPT || isUsPT);
			boolean hasUsGbVariance = !(isGbPT && isUsPT);
			String langRefset = US_ENG_LANG_REFSET;
			if (isGbPT && hasUsGbVariance) {
				langRefset = GB_ENG_LANG_REFSET;
			}
			
			//If it's not the PT or FSN, skip it.   We'll delete later if it's not the FSN counterpart
			if (!isPT && !isFSN) {
				continue;
			}
			
			//Skip FSNs that contain the word vitamin
			if (isFSN && d.getTerm().toLowerCase().contains("vitamin")) {
				report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Skipped vitamin FSN");
				continue;
			}
			
			//Check the existing term has correct capitalization
			ensureCaptialization(d);
			String replacementTerm = DrugUtils.calculateTermFromIngredients(c, isFSN, isPT, langRefset);
			replacementTerm = checkForVitamins(replacementTerm, d.getTerm());
			Description replacement = d.clone(null);
			replacement.setTerm(replacementTerm);
			
			//Does the case significance of the ingredients suggest a need to modify the term?
			if (replacement.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) && SnomedUtils.isCaseSensitive(replacementTerm)) {
				replacement.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
			}
			
			//Have we made any changes?  Create a new description if so
			if (!replacementTerm.equals(d.getTerm())) {
				changesMade += replaceTerm(t, c, d, replacement);
			}
			
			//If this is the FSN, then we should have another description without the semantic tag as an acceptable term
			if (isFSN) {
				Description fsnCounterpart = replacement.clone(null);
				String counterpartTerm = SnomedUtils.deconstructFSN(fsnCounterpart.getTerm())[0];
				
				if (!termAlreadyExists(c, counterpartTerm)) {
					fsnCounterpart.setTerm(counterpartTerm);
					report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, "FSN Counterpart added: " + counterpartTerm);
					fsnCounterpart.setType(DescriptionType.SYNONYM);
					fsnCounterpart.setAcceptabilityMap(createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
					c.addDescription(fsnCounterpart);
				}
			}
		}
		//Now that the FSN is resolved, remove any redundant terms
		changesMade += removeRedundantTerms(t,c);
		return changesMade;
	}

	private int replaceTerm(Task t, Concept c, Description removing, Description replacement) {
		int changesMade = 0;
		boolean doReplacement = true;
		if (termAlreadyExists(c, replacement.getTerm())) {
			//But does it exist inactive?
			if (termAlreadyExists(c, replacement.getTerm(), ActiveState.INACTIVE)) {
				reactivateMatchingTerm(t, c, replacement);
			} else {
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Replacement term already exists: '" + replacement.getTerm() + "' inactivating unwanted term only.");
			}
			//If we're removing a PT, merge the acceptability into the existing term, also any from the replacement
			if (removing.isPreferred()) {
				mergeAcceptability(t, c, removing, replacement);
			}
			doReplacement = false;
		}
		
		//Has our description been published?  Remove entirely if not
		boolean isInactivated = removeDescription(c,removing);
		String msg = (isInactivated?"Inactivated desc ":"Deleted desc ") +  removing;
		if (doReplacement) {
			msg += " in favour of " + replacement;
			c.addDescription(replacement);
		}
		changesMade++;
		Severity severity = removing.getType().equals(DescriptionType.FSN)?Severity.MEDIUM:Severity.LOW;
		report(t, c, severity, ReportActionType.DESCRIPTION_CHANGE_MADE, msg);
		return changesMade;
	}

	private void mergeAcceptability(Task t, Concept c, Description removing, Description replacement) {
		//Find the matching term that is not removing and merge that with the acceptability of removing
		boolean merged = false;
		for (Description match : c.getDescriptions(ActiveState.ACTIVE)) {
			if (!removing.equals(match) && match.getTerm().equals(replacement.getTerm())) {
				Map<String,Acceptability> mergedMap = SnomedUtils.mergeAcceptabilityMap(removing.getAcceptabilityMap(), match.getAcceptabilityMap());
				match.setAcceptabilityMap(mergedMap);
				//Now add in any improved acceptability that was coming from the replacement 
				mergedMap = SnomedUtils.mergeAcceptabilityMap(match.getAcceptabilityMap(), replacement.getAcceptabilityMap());
				match.setAcceptabilityMap(mergedMap);
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Merged acceptability from description being replaced and replacement into term that already exists: " + match);
				merged = true;
			}
		}
		if (!merged) {
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to find existing term to receive accepabilty merge with " + removing);
		}
	}

	private void reactivateMatchingTerm(Task t, Concept c, Description replacement) {
		//Loop through the inactive terms and reactivate the one that matches the replacement
		for (Description d : c.getDescriptions(ActiveState.INACTIVE)) {
			if (d.getTerm().equals(replacement.getTerm())) {
				d.setActive(true);
				d.setCaseSignificance(replacement.getCaseSignificance());
				d.setAcceptabilityMap(replacement.getAcceptabilityMap());
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_CHANGE_MADE, "Re-activated inactive term " + d);
				return;
			}
		}
	}

	private int removeRedundantTerms(Task t, Concept c) {
		int changesMade = 0;
		List<Description> allTerms = c.getDescriptions(ActiveState.ACTIVE);
		for (Description d : allTerms) {
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			if (!isFSN && !d.isPreferred()) {
				//Is this term the FSN counterpart?  Remove if not
				String fsnCounterpart = SnomedUtils.deconstructFSN(c.getFsn())[0];
				if (!d.getTerm().equals(fsnCounterpart)) {
					boolean isInactivated = removeDescription(c,d);
					String msg = (isInactivated?"Inactivated redundant desc ":"Deleted redundant desc ") +  d;
					report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_REMOVED, msg);
					changesMade++;
				}
			}
		}
		return changesMade;
	}

	private boolean removeDescription(Concept c, Description d) {
		if (d.isReleased()) {
			d.setActive(false);
			d.setInactivationIndicator(InactivationIndicator.NONCONFORMANCE_TO_EDITORIAL_POLICY);
			return true;
		} else {
			c.getDescriptions().remove(d);
			return false;
		}
	}

	private void validateUsGbVarianceInIngredients(Task t, Concept c) throws TermServerScriptException {
		//If the ingredient names have US/GB variance, the Drug should too.
		//Do we have two preferred terms?
		boolean drugVariance = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1;
		boolean ingredientVariance = false;
		//Now check the ingredients
		for (Relationship r : c.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
			Concept ingredient = gl.getConcept(r.getTarget().getConceptId());
			//Does the ingredient have more than one PT?
			if (ingredient.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1 ) {
				ingredientVariance = true;
				break;
			}
		}
		if (drugVariance != ingredientVariance) {
			String msg = "Drug vs Ingredient US/GB term variance mismatch : Drug=" + drugVariance + " Ingredients=" + ingredientVariance;
			report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			if (!drugVariance && ingredientVariance) {
				splitPreferredTerm(t, c);
			}
		}
	}

	/**
	 * Take the preferred term and create US and GB specific copies
	 * @param t
	 * @param c
	 * @throws TermServerScriptException 
	 */
	private void splitPreferredTerm(Task t, Concept c) throws TermServerScriptException {
		List<Description> preferredTerms = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (preferredTerms.size() != 1) {
			report (t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Unexpected number of preferred terms: " + preferredTerms.size());
		} else {
			Description usPT = preferredTerms.get(0);
			usPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET));
			Description gbPT = usPT.clone(null);
			gbPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET));
			report (t, c, Severity.HIGH, ReportActionType.DESCRIPTION_ADDED, "Split PT into US/GB variants: " + usPT + "/" + gbPT);
			c.addDescription(gbPT);
		}
	}

	private void ensureCaptialization(Description d) {
		String term = d.getTerm();
		for (String checkStr : forceCS) {
			if (term.startsWith(checkStr)) {
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			}
		}
	}

	protected String normalizeMultiIngredientTerm(String term, DescriptionType descriptionType, ConceptType conceptType) {
		
		String semanticTag = "";
		if (descriptionType.equals(DescriptionType.FSN)) {
			String[] parts = SnomedUtils.deconstructFSN(term);
			term = parts[0];
			semanticTag = " " + parts[1];
		}
		//If this is the FSN then add "Product containing ", or remember if it's already there
		boolean doProductPrefix = (descriptionType.equals(DescriptionType.FSN));
		String origTerm = term;
		if (term.startsWith(productPrefix)) {
			doProductPrefix = true;
			term = term.substring(productPrefix.length());
			/*if (term.startsWith(ONLY)) {
				prefix += ONLY;
				term = term.substring(ONLY.length());
			}*/
		}
		String oneEach ="";
		String suffix = "";
		if (conceptType.equals(ConceptType.MEDICINAL_PRODUCT_FORM)) {
			String[] parts = deconstructDoseForm(term);
			term = parts[0];
			oneEach = parts[1];
			suffix = parts[2]; 
			term = sortIngredients(term);
		}
		
		if (!doProductPrefix) {
			term = SnomedUtils.capitalize(term);
		}
		
		term = checkForVitamins(term, origTerm);
		
		return (doProductPrefix?productPrefix:"") + term + oneEach + suffix + semanticTag;
	}

	private String checkForVitamins(String term, String origTerm) {
		//See if we've accidentally made vitamin letters lower case and switch back
		for (String vitamin : vitamins) {
			if (origTerm.contains(vitamin)) {
				term = term.replace(vitamin.toLowerCase(), vitamin);
			} else if (origTerm.endsWith(vitamin.substring(0, 2))) {
				//Is it still at the end?
				if (term.endsWith(vitamin.toLowerCase().substring(0, 2))) {
					term = term.replace(vitamin.toLowerCase().substring(0, 2), vitamin.substring(0, 2));
				} else {
					//It should now have a space around it
					term = term.replace(vitamin.toLowerCase(), vitamin);
				}
			}
		}
		return term;
	}

	private String sortIngredients(String term) {
		String[] ingredients = term.split(AND);
		//ingredients should be in alphabetical order, also trim spaces
		for (int i = 0; i < ingredients.length; i++) {
			ingredients[i] = ingredients[i].toLowerCase().trim();
		}
		Arrays.sort(ingredients);

		//Reform with spaces around + sign and only first letter capitalized
		boolean isFirstIngredient = true;
		term = "";
		for (String thisIngredient : ingredients) {
			if (!isFirstIngredient) {
				term += AND;
			} 
			term += thisIngredient.toLowerCase();
			isFirstIngredient = false;
		}
		return term;
	}
	
	protected boolean termAlreadyExists(Concept concept, String newTerm) {
		return termAlreadyExists(concept, newTerm, ActiveState.BOTH);
	}

	protected boolean termAlreadyExists(Concept concept, String newTerm, ActiveState activeState) {
		boolean termAlreadyExists = false;
		for (Description description : concept.getDescriptions(activeState)) {
			if (description.getTerm().equals(newTerm)) {
				termAlreadyExists = true;
			}
		}
		return termAlreadyExists;
	}
	

	private String[] deconstructDoseForm(String term) {
		String[] parts = new String[]{term,"", ""};
		for (String doseForm : doseForms ) {
			if (term.endsWith(doseForm)) {
				parts[0] = term.substring(0, term.length() - doseForm.length());
				parts[2] = doseForm;
				if (parts[0].endsWith(find)) {
					parts[0] = parts[0].substring(0, parts[0].length() - find.length());
					parts[1] = find;
				}
				break;
			}
		}
		return parts;
	}
	
	protected Map<String, Acceptability> createAcceptabilityMap(AcceptabilityMode acceptabilityMode) {
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
	
	protected String removeUnwantedWords(String str, boolean isFSN) {
		String semTag = "";
		//Keep the semantic tag separate
		if (isFSN) {
			String[] parts = SnomedUtils.deconstructFSN(str);
			str = parts[0];
			semTag = " " + parts[1];
		}
		
		for (String unwantedWord : unwantedWords) {
			String[] unwantedWordCombinations = new String[] { SPACE + unwantedWord, unwantedWord + SPACE };
			for (String thisUnwantedWord : unwantedWordCombinations) {
				if (str.contains(thisUnwantedWord)) {
					str = str.replace(thisUnwantedWord,"");
				}
			}
		}
		return str + semTag;
	}

}
