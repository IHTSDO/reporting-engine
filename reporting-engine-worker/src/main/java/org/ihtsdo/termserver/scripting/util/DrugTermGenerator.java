package org.ihtsdo.termserver.scripting.util;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript.ReportActionType;
import org.ihtsdo.termserver.scripting.TermServerScript.Severity;
import org.ihtsdo.termserver.scripting.domain.*;
public class DrugTermGenerator implements RF2Constants{
	
	private TermServerScript parent;
	private boolean quiet = false;
	private boolean useEach = false;
	private boolean ptOnly = false;
	private boolean specifyDenominator = false;
	private boolean includeUnitOfPresentation = false;
	
	private String [] forceCS = new String[] { "N-" };
	private String[] vitamins = new String[] {" A ", " B ", " C ", " D ", " E ", " G "};
	
	private List<Concept> neverAbbrev = new ArrayList<>();
	
	public DrugTermGenerator (TermServerScript parent) {
		this.parent = parent;
		neverAbbrev.add(MICROGRAM);
		neverAbbrev.add(INTERNATIONAL_UNIT);
		neverAbbrev.add(NANOGRAM);
	}
	
	public DrugTermGenerator includeUnitOfPresentation(Boolean state) {
		includeUnitOfPresentation = state;
		return this;
	}
	
	public boolean includeUnitOfPresentation() {
		return includeUnitOfPresentation;
	}
	
	public DrugTermGenerator useEach(Boolean state) {
		useEach = state;
		return this;
	}
	
	public boolean specifyDenominator() {
		return specifyDenominator;
	}
	
	public DrugTermGenerator specifyDenominator(Boolean state) {
		specifyDenominator = state;
		return this;
	}
	
	public boolean useEach() {
		return useEach;
	}
	
	public int ensureDrugTermsConform(Task t, Concept c, CharacteristicType charType) throws TermServerScriptException {
		return ensureDrugTermsConform(t, c, charType, false);  //Do not allow duplicate descriptions by default
	}

	public int ensureDrugTermsConform(Task t, Concept c, CharacteristicType charType, boolean allowDuplicates) throws TermServerScriptException {
		int changesMade = 0;
		
		//This function will split out the US / GB terms if the ingredients show variance where the product does not
		//The unit of presentation could also necessitate a variance
		validateUsGbVariance(t,c, charType, allowDuplicates);

		for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
			try { 
				changesMade += ensureDrugTermConforms(t, c, d, charType);
			} catch (Exception e) {
				String stack = ExceptionUtils.getStackTrace(e);
				report (t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to conform description " + d, stack);
			}
		}
		//Now that the FSN is resolved, remove any redundant terms
		if (!ptOnly) {
			changesMade += removeRedundantTerms(t,c);
		} else {
			changesMade += removeOldPTs(t,c);
		}
		return changesMade;
	}
	
	private int ensureDrugTermConforms(Task t, Concept c, Description d, CharacteristicType charType) throws TermServerScriptException {
		int changesMade = 0;
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
		if (!isPT && !isFSN ) {
			return NO_CHANGES_MADE;
		}
		
		//If we're only doing PTs, return if it's the FSN
		if (ptOnly && isFSN) {
			return NO_CHANGES_MADE;
		}
		
		//Skip FSNs that contain the word vitamin
		/*if (isFSN && d.getTerm().toLowerCase().contains("vitamin")) {
			report(t, c, Severity.MEDIUM, ReportActionType.NO_CHANGE, "Skipped vitamin FSN");
			return NO_CHANGES_MADE;
		}*/
		
		//Check the existing term has correct capitalization
		if (d.getTerm() != null) {
			ensureCaptialization(d);
		}
		String replacementTerm = calculateTermFromIngredients(c, isFSN, isPT, langRefset, charType);
		if (d.getTerm() != null) {
			replacementTerm = checkForVitamins(replacementTerm, d.getTerm());
		}
		Description replacement = d.clone(null);
		replacement.setTerm(replacementTerm);
		
		//Fix issues with existing acceptability
		if (isGbPT && !isUsPT) {
			replacement.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET));
		} else if (!isGbPT && isUsPT) {
			replacement.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET));
		}
		
		//Does the case significance of the ingredients suggest a need to modify the term?
		if (replacement.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) && StringUtils.isCaseSensitive(replacementTerm)) {
			replacement.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		} else if (!StringUtils.isCaseSensitive(replacementTerm)) {
			replacement.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
		}
		
		//We might have a CS ingredient that starts the term.  Check for this, set and warn
		checkCaseSensitiveOfIngredients(t, c, replacement, isFSN, charType);
		
		//Have we made any changes?  Create a new description if so
		if (!replacementTerm.equals(d.getTerm())) {
			changesMade += replaceTerm(t, c, d, replacement);
		}
		
		//If this is the FSN, then we should have another description without the semantic tag as an acceptable term
		//Update: We're not doing FSN Counterparts now, because of issues with US / GB Variants
		/*if (isFSN) {
			Description fsnCounterpart = replacement.clone(null);
			String counterpartTerm = SnomedUtils.deconstructFSN(fsnCounterpart.getTerm())[0];
			
			if (!SnomedUtils.termAlreadyExists(c, counterpartTerm)) {
				fsnCounterpart.setTerm(counterpartTerm);
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, "FSN Counterpart added: " + counterpartTerm);
				fsnCounterpart.setType(DescriptionType.SYNONYM);
				fsnCounterpart.setAcceptabilityMap(SnomedUtils.createAcceptabilityMap(AcceptabilityMode.ACCEPTABLE_BOTH));
				c.addDescription(fsnCounterpart);
			}
		}*/
		return changesMade;
	}

	private void checkCaseSensitiveOfIngredients(Task t, Concept c, Description d, boolean isFSN,
			CharacteristicType charType) throws TermServerScriptException {
		if (d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
			for (Concept ingred : DrugUtils.getIngredients(c, charType)) {
				ingred = GraphLoader.getGraphLoader().getConcept(ingred.getConceptId());
				Description ingredDesc = isFSN ? ingred.getFSNDescription() : ingred.getPreferredSynonym(US_ENG_LANG_REFSET);
				if (ingredDesc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, "Set term to CS due to CS present in ingredient term : " + ingredDesc);
					d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
				}
			}
		}
	}

	public String calculateTermFromIngredients(Concept c, boolean isFSN, boolean isPT, String langRefset, CharacteristicType charType) throws TermServerScriptException {
		String proposedTerm = "";
		String semTag = "";
		boolean ptContaining = false;
		if (isFSN && c.getFsn() != null) {
			semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		}
		//Get all the ingredients in order
		Set<String> ingredients = getIngredientsWithStrengths(c, isFSN, langRefset, charType);
		
		//What prefixes and suffixes do we need?
		String prefix = "";
		String suffix = "";
		
		if (c.getConceptType() == null) {
			SnomedUtils.populateConceptType(c);
		}
		
		if (isFSN) {
			prefix = "Product containing ";
			switch (c.getConceptType()) {
				case MEDICINAL_PRODUCT_ONLY: 
										prefix += "only ";
				case MEDICINAL_PRODUCT: 
										semTag = "(medicinal product)";
										break;
				case MEDICINAL_PRODUCT_FORM_ONLY : 
										prefix += "only ";
				case MEDICINAL_PRODUCT_FORM : suffix =  " in " + DrugUtils.getDosageForm(c, isFSN, langRefset);
										semTag = "(medicinal product form)";
										break;
				case CLINICAL_DRUG : 	prefix = "Product containing precisely ";
										//TODO Check that presentation is solid before adding 1 each
										suffix =  getCdSuffix(c, isFSN, langRefset);
										semTag = "(clinical drug)";
										break;
				case STRUCTURE_AND_DISPOSITION_GROUPER :
				case STRUCTURAL_GROUPER :
				case DISPOSITION_GROUPER : 
				default:
			}
		} else if (isPT) {
			switch (c.getConceptType()) {
				case MEDICINAL_PRODUCT_ONLY : 
										suffix = " only product";
										ptContaining = false;
										break;
				case MEDICINAL_PRODUCT : 
										suffix = "containing product";
										ptContaining = true;
										break;
				case MEDICINAL_PRODUCT_FORM_ONLY : 
										suffix =  " only product in " + DrugUtils.getDosageForm(c, isFSN, langRefset);
										break;
				case MEDICINAL_PRODUCT_FORM : 
										suffix =  "containing product in " + DrugUtils.getDosageForm(c, isFSN, langRefset);
										ptContaining = true;
										break;
				case CLINICAL_DRUG : 	suffix = getCdSuffix(c, isFSN, langRefset);
										break;
				case STRUCTURE_AND_DISPOSITION_GROUPER :
				case STRUCTURAL_GROUPER :
				case DISPOSITION_GROUPER : 
											suffix = "containing product";
											ptContaining = true;
											semTag = "(product)";
				default:
			}
		}
		
		//Are we adding "-" to every ingredient to indicate they're all containing?
		if (ptContaining) {
			Set<String> tempSet = new HashSet<>();
			for (String ingredient : ingredients) {
				tempSet.add(ingredient + "-");
			}
			ingredients.clear();
			ingredients.addAll(tempSet);
		}
		
		//Form the term from the ingredients with prefixes and suffixes as required.
		proposedTerm = prefix +  org.apache.commons.lang.StringUtils.join(ingredients, " and ") + suffix;
		if (isFSN) {
			proposedTerm += " " + semTag;
		}
		proposedTerm = StringUtils.capitalize(proposedTerm);
		return proposedTerm;
	}

	private String getCdSuffix(Concept c, boolean isFSN, String langRefset) throws TermServerScriptException {
		String suffix;
		String unitOfPresentation = DrugUtils.getAttributeType(c, HAS_UNIT_OF_PRESENTATION, isFSN, langRefset);
		String doseForm = DrugUtils.getDosageForm(c, isFSN, langRefset);
		boolean isActuation = unitOfPresentation.equals("actuation");
		
		if (includeUnitOfPresentation ||
				(hasAttribute(c, HAS_UNIT_OF_PRESENTATION) && !doseForm.endsWith(unitOfPresentation)) ) {
			if ((isFSN  && !specifyDenominator) || isActuation) {
				suffix = (isActuation?"/":"/1 ") + unitOfPresentation + " "  + doseForm;
			} else {
				suffix = " " + DrugUtils.getDosageForm(c, isFSN, langRefset) + " "  + unitOfPresentation;
			}
		} else {
			if (isFSN && !specifyDenominator && !hasAttribute(c, HAS_CONC_STRENGTH_DENOM_VALUE)) {
				suffix = "/1 each "  + doseForm;
			} else {
				suffix = " " + doseForm;
			}
		}
		return suffix;
	}

	private int removeRedundantTerms(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> allTerms = c.getDescriptions(ActiveState.ACTIVE);
		for (Description d : allTerms) {
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			if (!isFSN && !d.isPreferred()) {
				//Is this term the FSN counterpart?  Remove if not
				//Update: We don't do fsnCounterparts due to us / gb issues
				//String fsnCounterpart = SnomedUtils.deconstructFSN(c.getFsn())[0];
				//if (!d.getTerm().equals(fsnCounterpart)) {
					boolean isInactivated = removeDescription(c,d);
					String msg = (isInactivated?"Inactivated redundant desc ":"Deleted redundant desc ") +  d;
					report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, msg);
					changesMade++;
				//}
			}
		}
		return changesMade;
	}
	
	
	/**
	 * Designed for Medicinal Products, remove terms which are like the PT, but missing the suffix
	 * @param t
	 * @param c
	 * @return
	 * @throws TermServerScriptException 
	 */
	private int removeOldPTs(Task t, Concept c) throws TermServerScriptException {
		int changesMade = 0;
		List<Description> allTerms = c.getDescriptions(ActiveState.ACTIVE);
		String PT = c.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
		String badTerm = PT.replaceAll(" product", "");
		for (Description d : allTerms) {
			boolean isFSN = d.getType().equals(DescriptionType.FSN);
			if (!isFSN && !d.isPreferred()) {
				//Is this term bad?  Remove if so.
				if (d.getTerm().equals(badTerm)) {
					boolean isInactivated = removeDescription(c,d);
					String msg = (isInactivated?"Inactivated bad desc ":"Deleted bad desc ") +  d;
					report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, msg);
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
	
	private void validateUsGbVariance(Task t, Concept c, CharacteristicType charType, boolean allowDuplicates) throws TermServerScriptException {
		//If the ingredient names have US/GB variance, the Drug should too.
		//If the unit of presentation is present and has variance, the drug should too.
		//Do we have two preferred terms?
		boolean drugVariance = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1;
		boolean ingredientVariance = checkIngredientVariance(c, charType);
		Boolean presentationVariance = checkPresentationVariance(c, charType);  //May be null if no presentation is specified

		if (drugVariance != ingredientVariance || (presentationVariance != null && !drugVariance && presentationVariance)) {
			String msg = "Drug vs Ingredient vs Presentation US/GB term variance mismatch : Drug=" + drugVariance + " Ingredients=" + ingredientVariance + " Presentation=" + presentationVariance;
			report (t, c, Severity.HIGH, ReportActionType.VALIDATION_CHECK, msg);
			if (!drugVariance && (ingredientVariance || presentationVariance)) {
				splitPreferredTerm(t, c, allowDuplicates);
			}
		} 
	}

	private Boolean checkPresentationVariance(Concept c, CharacteristicType charType) throws TermServerScriptException {
		Boolean presentationVariance = null;  
		List<Relationship> presentationRels = c.getRelationships(charType, HAS_UNIT_OF_PRESENTATION, ActiveState.ACTIVE);
		if (presentationRels.size() > 0) {
			presentationVariance = false;
		}
		for (Relationship r : presentationRels) {
			Concept presentation = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			//Does the ingredient have more than one PT?
			if (presentation.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1 ) {
				presentationVariance = true;
				break;
			}
		}
		return presentationVariance;
	}

	private boolean checkIngredientVariance(Concept c, CharacteristicType charType) throws TermServerScriptException {
		boolean ingredientVariance = false;
		//Now check the ingredients
		List<Relationship> ingredients = c.getRelationships(charType, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		ingredients.addAll( c.getRelationships(charType, HAS_PRECISE_INGRED, ActiveState.ACTIVE));
		for (Relationship r : ingredients) {
			Concept ingredient = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			//Does the ingredient have more than one PT?
			if (ingredient.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1 ) {
				ingredientVariance = true;
				break;
			}
		}
		return ingredientVariance;
	}

	/**
	 * Take the preferred term and create US and GB specific copies
	 * @param t
	 * @param c
	 * @param allowDuplicates 
	 * @throws TermServerScriptException 
	 */
	private void splitPreferredTerm(Task t, Concept c, boolean allowDuplicates) throws TermServerScriptException {
		List<Description> preferredTerms = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		if (preferredTerms.size() != 1) {
			report (t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Unexpected number of preferred terms: " + preferredTerms.size());
		} else {
			Description usPT = preferredTerms.get(0);
			usPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET));
			Description gbPT = usPT.clone(null);
			gbPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET));
			report (t, c, Severity.HIGH, ReportActionType.DESCRIPTION_ADDED, "Split PT into US/GB variants: " + usPT + "/" + gbPT);
			c.addDescription(gbPT, allowDuplicates);
		}
	}

	private int replaceTerm(Task t, Concept c, Description removing, Description replacement) throws TermServerScriptException {
		int changesMade = 0;
		boolean doReplacement = true;
		if (SnomedUtils.termAlreadyExists(c, replacement.getTerm())) {
			//But does it exist inactive?
			if (SnomedUtils.termAlreadyExists(c, replacement.getTerm(), ActiveState.INACTIVE)) {
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
		changesMade++;
		
		//We're only going to report this if the term really existed, silently delete null terms
		if (removing.getTerm() != null) {
			Severity severity = removing.getType().equals(DescriptionType.FSN)?Severity.MEDIUM:Severity.LOW;
			report(t, c, severity, ReportActionType.DESCRIPTION_INACTIVATED, msg);
		}
		
		if (doReplacement) {
			report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_ADDED, replacement);
			c.addDescription(replacement);
		}
		
		return changesMade;
	}

	private void mergeAcceptability(Task t, Concept c, Description removing, Description replacement) throws TermServerScriptException {
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

	public String checkForVitamins(String term, String origTerm) {
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
	
	private Set<String> getIngredientsWithStrengths(Concept c, boolean isFSN, String langRefset, CharacteristicType charType) throws TermServerScriptException {
		List<Relationship> ingredientRels = c.getRelationships(charType, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		ingredientRels.addAll(c.getRelationships(charType, HAS_PRECISE_INGRED, ActiveState.ACTIVE));
		Set<String> ingredients = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);  //Will naturally sort in alphabetical order
		boolean ok = false;
		for (Relationship r : ingredientRels) {
			//Need to recover the full concept to have all descriptions, not the partial one stored as the target.
			Concept ingredient = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			
			//Do we have a BoSS in the same group?
			Concept boSS = SnomedUtils.getTarget(c, new Concept[] {HAS_BOSS}, r.getGroupId(), charType);
			
			//Are we adding the strength?
			Concept strength = SnomedUtils.getTarget (c, new Concept[] {HAS_PRES_STRENGTH_VALUE, HAS_CONC_STRENGTH_VALUE}, r.getGroupId(), charType);
			
			//Are we adding the denominator strength and units?
			String denominatorStr = "";
			if (specifyDenominator || hasAttribute(c, HAS_CONC_STRENGTH_DENOM_VALUE)) {
				denominatorStr = "/";
				Concept denStren = SnomedUtils.getTarget (c, new Concept[] {HAS_PRES_STRENGTH_DENOM_VALUE, HAS_CONC_STRENGTH_DENOM_VALUE}, r.getGroupId(), charType);
				String denStrenStr = SnomedUtils.deconstructFSN(denStren.getFsn())[0];
				if (!denStrenStr.equals("1") || isFSN) {
					denominatorStr += denStrenStr + " ";
				}
				Concept denUnit = SnomedUtils.getTarget (c, new Concept[] {HAS_PRES_STRENGTH_DENOM_UNIT, HAS_CONC_STRENGTH_DENOM_UNIT}, r.getGroupId(), charType);
				String denUnitStr = getTermForConcat(denUnit, isFSN || neverAbbrev.contains(denUnit), langRefset);
				denominatorStr += denUnitStr;
			}
			
			//And the unit
			Concept unit = SnomedUtils.getTarget(c, new Concept[] {HAS_PRES_STRENGTH_UNIT, HAS_CONC_STRENGTH_UNIT}, r.getGroupId(), charType);
			
			String ingredientWithStrengthTerm = formIngredientWithStrengthTerm (ingredient, boSS, strength, unit, denominatorStr, isFSN, langRefset);
			ingredients.add(ingredientWithStrengthTerm);
		}
		return ingredients;
	}

	private boolean hasAttribute(Concept c, Concept attrib) {
		//Dose this concept specify a concentration denominator?
		return c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attrib, ActiveState.ACTIVE).size() > 0;
	}

	private String formIngredientWithStrengthTerm(Concept ingredient, Concept boSS, Concept strength, Concept unit, String denominatorStr, boolean isFSN, String langRefset) throws TermServerScriptException {
		boolean separateBoSS = (boSS!= null && !boSS.equals(ingredient));
		String ingredientTerm="";
		
		//First the ingredient, with the BoSS first if different
		if (separateBoSS) {
			ingredientTerm = getTermForConcat(boSS, isFSN, langRefset);
			ingredientTerm += " (as ";
		}
		
		ingredientTerm += getTermForConcat(ingredient, isFSN, langRefset);
		
		if (separateBoSS) {
			ingredientTerm += ")";
		}

		
		//Now add the Strength
		if (strength != null) {
			ingredientTerm += " " + SnomedUtils.deconstructFSN(strength.getFsn())[0];
		}
		
		if (unit != null) {
			ingredientTerm += " " + getTermForConcat(unit, isFSN || neverAbbrev.contains(unit), langRefset);
		}
		
		ingredientTerm += denominatorStr;
		
		return ingredientTerm;
	}

	private String getTermForConcat(Concept c, boolean useFSN, String langRefset) throws TermServerScriptException {
		Description desc;
		String term;
		if (useFSN) {
			desc = c.getFSNDescription();
			term = SnomedUtils.deconstructFSN(desc.getTerm())[0];
		} else {
			desc = c.getPreferredSynonym(langRefset);
			term = desc.getTerm();
		}
		if (!desc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
			term = StringUtils.deCapitalize(term);
		}
		return term;
	}

	private void reactivateMatchingTerm(Task t, Concept c, Description replacement) throws TermServerScriptException {
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
	
	protected void report(Task task, Component component, Severity severity, ReportActionType actionType, Object... details) throws TermServerScriptException {
		if (!quiet) {
			parent.report(task, component, severity, actionType, details);
		}
	}
	
	public void ensureCaptialization(Description d) {
		String term = d.getTerm();
		for (String checkStr : forceCS) {
			if (term.startsWith(checkStr)) {
				d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			}
		}
	}
	
	public void setQuiet(boolean quiet) {
		this.quiet = quiet;
	}
	
	public void setPtOnly(boolean ptOnly) {
		this.ptOnly = ptOnly;
	}
	
}
