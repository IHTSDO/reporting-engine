package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.domain.Task;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
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
			
			//If any term contains a PLUS sign, flag validation
			if (replacementTerm.contains(PLUS)) {
				//Try for the properly spaced version first, so we don't introduce extra spaces.
				replacementTerm = replacementTerm.replaceAll(PLUS_SPACED_ESCAPED, AND);
				replacementTerm = replacementTerm.replaceAll(PLUS_ESCAPED, AND);
			}
			
			//If this is the PT, remove any /1 each
			if (d.isPreferred() && d.getType().equals(DescriptionType.SYNONYM) && replacementTerm.contains(find)) {
				replacementTerm = replacementTerm.replace(find, replace);
			}
			
			//Remove any unwanted words (PT and FSN only)
			//Swap known replacements 
			if (d.isPreferred()) {
				replacementTerm = removeUnwantedWords(replacementTerm, isFSN);
				replacementTerm = SnomedUtils.substitute(replacementTerm, wordSubstitution);
			}

			//Check ingredient order
			if (replacementTerm.contains(AND)) {
				replacementTerm = normalizeMultiIngredientTerm(replacementTerm, d.getType());
			}

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

	private void ensureCaptialization(Description d) {
		String term = d.getTerm();
		for (String checkStr : forceCS) {
			if (term.startsWith(checkStr)) {
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			}
		}
	}

	protected String normalizeMultiIngredientTerm(String term, DescriptionType descriptionType) {
		
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
		
		String[] parts = deconstructDoseForm(term);
		term = parts[0];
		String oneEach = parts[1];
		String suffix = parts[2]; 
		term = sortIngredients(term);
		
		if (!doProductPrefix) {
			term = SnomedUtils.capitalize(term);
		}
		
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
		
		return (doProductPrefix?productPrefix:"") + term + oneEach + suffix + semanticTag;
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
		boolean termAlreadyExists = false;
		for (Description description : concept.getDescriptions()) {
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
