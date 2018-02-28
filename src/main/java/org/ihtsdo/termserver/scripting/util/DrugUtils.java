package org.ihtsdo.termserver.scripting.util;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

public class DrugUtils implements RF2Constants {
	
	public static final String CD = "(clinical drug)";
	public static final String MP = "(medicinal product)";
	public static final String MPF = "(medicinal product form)";

	public static String calculateTermFromIngredients(Concept c, boolean isFSN, boolean isPT, String langRefset) throws TermServerScriptException {
		String proposedTerm = "";
		//Get all the ingredients and put them in order
		List<Relationship> ingredientRels = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		Set<String> ingredients = new TreeSet<String>();  //Will naturally sort in alphabetical order
		for (Relationship r : ingredientRels) {
			//Need to recover the full concept to have all descriptions, not the partial one stored as the target.
			Concept ingredient = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			Description ingredientDesc = ingredient.getPreferredSynonym(langRefset);
			String ingredientTerm = ingredientDesc.getTerm();
			//If the ingredient name is not case sensitive, decaptialize
			if (!ingredientDesc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
				ingredientTerm = SnomedUtils.deCapitalize(ingredientTerm);
			}
			ingredients.add(ingredientTerm);
		}
		//What prefixes and suffixes do we need?
		String prefix = "";
		String suffix = "";
		if (isFSN) {
			prefix = "Product containing ";
		} else if (isPT) {
			switch (c.getConceptType()) {
				case MEDICINAL_PRODUCT : suffix = " product";
										break;
				case MEDICINAL_PRODUCT_FORM : suffix =  " in " + getDosageForm(c);
										break;
				default:
			}
		}
		
		//Form the term from the ingredients with prefixes and suffixes as required.
		proposedTerm = prefix + StringUtils.join(ingredients, " and ") + suffix;
		if (isFSN) {
			proposedTerm += " " + SnomedUtils.deconstructFSN(c.getFsn())[1];
		}
		proposedTerm = SnomedUtils.capitalize(proposedTerm);
		return proposedTerm;
	}
	
	private static  String getDosageForm(Concept concept) {
		List<Relationship> doseForms = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE);
		if (doseForms.size() == 0) {
			return "NO STATED DOSE FORM DETECTED";
		} else if (doseForms.size() > 1) {
			return "MULTIPLE DOSE FORMS";
		} else {
			String doseForm = SnomedUtils.deconstructFSN(doseForms.get(0).getTarget().getFsn())[0];
			doseForm = SnomedUtils.deCapitalize(doseForm);
			//Translate known issues
			switch (doseForm) {
				case "ocular dosage form": doseForm =  "ophthalmic dosage form";
					break;
				case "inhalation dosage form": doseForm = "respiratory dosage form";
					break;
				case "cutaneous AND/OR transdermal dosage form" : doseForm = "topical dosage form";
					break;
				case "oromucosal AND/OR gingival dosage form" : doseForm = "oropharyngeal dosage form";
					break;
			}
			
			//In the product we say "doseage form", so make that switch
			doseForm = doseForm.replace(" dose ", " dosage ");
			
			return doseForm;
		}
	}
}
