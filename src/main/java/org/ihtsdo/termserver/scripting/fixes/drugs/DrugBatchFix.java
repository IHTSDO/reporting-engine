package org.ihtsdo.termserver.scripting.fixes.drugs;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.domain.RF2Constants;
import org.ihtsdo.termserver.scripting.fixes.BatchFix;
import org.ihtsdo.termserver.scripting.util.DrugTermGenerator;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

public abstract class DrugBatchFix extends BatchFix implements RF2Constants{
	
	static Map<String, String> wordSubstitution = new HashMap<String, String>();
	static {
		wordSubstitution.put("acetaminophen", "paracetamol");
	}
	
	String [] unwantedWords = new String[] { "preparation", "product" };
	
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
	
	DrugTermGenerator termGenerator = new DrugTermGenerator(this);

	protected DrugBatchFix(BatchFix clone) {
		super(clone);
	}

	//This code has been replaced by DrugTermGenerator
/*	protected String normalizeMultiIngredientTerm(String term, DescriptionType descriptionType, ConceptType conceptType) {
		
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
	/*	}
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
		
		term = termGenerator.checkForVitamins(term, origTerm);
		
		return (doProductPrefix?productPrefix:"") + term + oneEach + suffix + semanticTag;
	}*/

	/*private String sortIngredients(String term) {
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
	}*/

/*	private String[] deconstructDoseForm(String term) {
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
	}*/
	
	
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
