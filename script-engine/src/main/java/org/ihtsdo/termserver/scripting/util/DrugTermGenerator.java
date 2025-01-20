package org.ihtsdo.termserver.scripting.util;

import java.util.*;

import org.apache.commons.lang.exception.ExceptionUtils;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Task;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.ValidationFailure;
import org.ihtsdo.termserver.scripting.domain.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DrugTermGenerator extends TermGenerator {

	private static final Logger LOGGER = LoggerFactory.getLogger(DrugTermGenerator.class);

	private boolean ptOnly = false;
	private static final boolean SPECIFY_DENOMINATOR = false;
	private static final boolean INCLUDE_UNIT_OF_PRESENTATION = false;

	private final String[] forceCS = new String[]{"N-" };
	private final String[] vitamins = new String[]{" A ", " B ", " C ", " D ", " E ", " G " };

	private final List<Concept> neverAbbrev = new ArrayList<>();
	private final Map<Concept, String> overridePTs = new HashMap<>();

	public DrugTermGenerator(TermServerScript parent) {
		this.parent = parent;
		neverAbbrev.add(MICROGRAM);
		neverAbbrev.add(MICROLITER);
		neverAbbrev.add(INTERNATIONAL_UNIT);
		neverAbbrev.add(NANOGRAM);
		neverAbbrev.add(MICROEQUIVALENT);
		neverAbbrev.add(PICOGRAM);
		neverAbbrev.add(UNIT);
		neverAbbrev.add(MILLION_UNIT);

		overridePTs.put(INTERNATIONAL_UNIT, "unit");
	}

	public boolean includeUnitOfPresentation() {
		return INCLUDE_UNIT_OF_PRESENTATION;
	}

	public int ensureTermsConform(Task t, Concept c, String X, CharacteristicType charType) throws TermServerScriptException {
		int changesMade = 0;

		//This function will split out the US / GB terms if the ingredients show variance where the product does not
		//The unit of presentation could also necessitate a variance
		//Do allow duplicates when checking variance as splitting US/GB into two will temporarily have same term
		validateUsGbVariance(t, c, charType, true);

		try {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				changesMade += ensureDrugTermConforms(t, c, d, charType);
			}
		} catch (Exception e) {
			String stack = ExceptionUtils.getStackTrace(e);
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Failed to ensure terms conform", stack);
			return NO_CHANGES_MADE;
		}
		//Now that the FSN is resolved, remove any redundant terms
		if (!ptOnly) {
			changesMade += removeRedundantTerms(t, c);
		} else {
			changesMade += removeOldPTs(t, c);
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
		if (!isPT && !isFSN) {
			return NO_CHANGES_MADE;
		}

		//If we're only doing PTs, return if it's the FSN
		if (ptOnly && isFSN) {
			return NO_CHANGES_MADE;
		}

		//Check the existing term has correct capitalization
		if (d.getTerm() != null) {
			ensureCaptialization(d);
		}
		String replacementTerm = calculateTermFromIngredients(c, isFSN, isPT, langRefset, charType);
		if (StringUtils.isEmpty(replacementTerm)) {
			throw new TermServerScriptException("Failed to create term for " + c);
		}

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

		//We might have a CS ingredient that starts the term.  Check for this, set and warn
		//OR a case ingredient that would cause the FSN to switch to cI
		changesMade += checkCaseSensitivitySetting(t, c, replacement, isFSN, charType, langRefset);

		//Have we made any changes?  Create a new description if so
		if (!replacementTerm.equals(d.getTerm())) {
			changesMade += replaceTerm(t, c, d, replacement, true);
		} else {
			//Are we seeing a difference in the case signficance?
			if (!d.getCaseSignificance().equals(replacement.getCaseSignificance())) {
				String before = SnomedUtils.translateCaseSignificanceFromEnum(d.getCaseSignificance());
				String after = SnomedUtils.translateCaseSignificanceFromEnum(replacement.getCaseSignificance());
				d.setCaseSignificance(replacement.getCaseSignificance());
				changesMade++;
				report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Case significance changed from " + before + "->" + after, d);
			}
		}

		//Validation check that we should never end up with the same word twice 
		//eg Product containing precisely methadone hydrochloride 40 milligram/1 tablet tablet for conventional release oral suspension (clinical drug)
		String[] words = replacementTerm.split(SPACE);
		String lastWord = "";
		for (String word : words) {
			if (lastWord.equals(word)) {
				throw new ValidationFailure(t, c, "Repetition of '" + word + "' in term '" + replacementTerm + "'");
			}
			lastWord = word;
		}

		//Validation, check that we have some acceptability for both US and GB
		if (replacement.getAcceptability(US_ENG_LANG_REFSET) == null || replacement.getAcceptability(GB_ENG_LANG_REFSET) == null) {
			LOGGER.warn("{} has unacceptable acceptability", d);
		}

		return changesMade;
	}

	private int checkCaseSensitivitySetting(Task t, Concept c, Description d, boolean isFSN,
											CharacteristicType charType, String langRefset) throws TermServerScriptException {
		int changesMade = 0;
		//Firstly, are there any absolute rules that would force the case signficance like a capital after the first letter
		//or starting with a lower case?
		if (StringUtils.initialLetterLowerCase(d.getTerm())) {
			return modifyIfRequired(d, CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
			//For the other settings, we need to check further rules below as lower case letters might not look case sensitive.
		} else if (StringUtils.isCaseSensitive(d.getTerm()) && !d.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
			d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
		} else if (!StringUtils.isCaseSensitive(d.getTerm()) && !d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
			d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
		}

		boolean hasCaseSensitiveIngredient = false;
		List<Concept> ingreds = DrugUtils.getIngredients(c, charType);
		boolean isFirstIngred = true;
		for (Concept ingred : ingreds) {
			ingred = GraphLoader.getGraphLoader().getConcept(ingred.getConceptId());
			Description ingredDesc = isFSN ? ingred.getFSNDescription() : ingred.getPreferredSynonym(langRefset);

			if (ingredDesc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE) ||
					ingredDesc.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
				hasCaseSensitiveIngredient = true;
			}

			if (d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE)) {
				if (ingredDesc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					//For the FSN, a CS ingredient will cause us to go to cI due to the "Product containing" prefix
					if (isFSN) {
						report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Set term to cI (from ci) in FSN due to CS present in ingredient term", ingredDesc);
						d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
					} else {
						if (isFirstIngred) {
							report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Set term to CS (from ci) due to CS present in first ingredient term", ingredDesc);
							d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
							break;  // Don't let subsequent ingredients undo this
						} else {
							report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Set term to cI (from ci) due to CS present in (not first) ingredient term", ingredDesc);
							d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
						}
					}
					changesMade++;
				} else if (ingredDesc.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
					//For the FSN, a cI ingredient will cause us to go to cI in the product
					report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Set term to cI (from ci) due to cI present in ingredient term", ingredDesc);
					d.setCaseSignificance(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE);
					changesMade++;
				}
			} else if (d.getCaseSignificance().equals(CaseSignificance.INITIAL_CHARACTER_CASE_INSENSITIVE)) {
				//If this is the PT and the FIRST ingredient is CS, then the term becomes CS
				if (!isFSN && isFirstIngred && ingredDesc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
					report(t, c, Severity.MEDIUM, ReportActionType.DESCRIPTION_CHANGE_MADE, "Set PT to CS (from cI) due to CS present in (first) ingredient term", ingredDesc);
					d.setCaseSignificance(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
					changesMade++;
				}
			}
			isFirstIngred = false;
		}
		//Watch that this doesn't allow for any case sensitivity in the dose form, units, or presentation.  Currently there is none...
		if (!hasCaseSensitiveIngredient && !d.getCaseSignificance().equals(CaseSignificance.CASE_INSENSITIVE) && !StringUtils.isCaseSensitive(d.getTerm())) {
			report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, "Set term to ci due to lack of case sensitivity in any ingredient");
			d.setCaseSignificance(CaseSignificance.CASE_INSENSITIVE);
			changesMade++;
		}
		return changesMade;
	}

	private int modifyIfRequired(Description d, CaseSignificance caseSig) {
		if (!d.getCaseSignificance().equals(caseSig)) {
			d.setCaseSignificance(caseSig);
			return CHANGE_MADE;
		}
		return NO_CHANGES_MADE;
	}

	public String calculateTermFromIngredients(Concept c, boolean isFSN, boolean isPT, String langRefset, CharacteristicType charType) throws TermServerScriptException {
		return calculateTermFromIngredients(c, isFSN, isPT, langRefset, charType, false);
	}

	protected String calculateTermFromIngredients(Concept c, boolean isFSN, boolean isPT, String langRefset, CharacteristicType charType, boolean isVaccine) throws TermServerScriptException {
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
			prefix = isVaccine?"Vaccine product containing " : "Product containing ";
			switch (c.getConceptType()) {
				case MEDICINAL_PRODUCT_ONLY: 
										prefix += "only ";
				case MEDICINAL_PRODUCT: 
										semTag = "(medicinal product)";
										break;
				case MEDICINAL_PRODUCT_FORM_ONLY : 
										prefix += "only ";  //No break here, we want to also process as per MPF
				case MEDICINAL_PRODUCT_FORM : suffix =  " in " + DrugUtils.getDosageForm(c, isFSN, langRefset);
										semTag = "(medicinal product form)";
										break;
				case CLINICAL_DRUG : 	prefix = "Product containing precisely ";
										//TODO Check that presentation is solid before adding 1 each
										suffix =  getCdSuffix(c, isFSN, langRefset);
										semTag = "(clinical drug)";
										break;
				case STRUCTURE_AND_DISPOSITION_GROUPER, STRUCTURAL_GROUPER,DISPOSITION_GROUPER :
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
				case STRUCTURE_AND_DISPOSITION_GROUPER ,STRUCTURAL_GROUPER, DISPOSITION_GROUPER :
											suffix = "containing product";
											ptContaining = true;
											semTag = "(product)";
											break;
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
		
		//We need to not capitalize ingredients like von Willebrand factor
		if (isFSN || !firstIngredientCS(c,langRefset, charType)) {
			proposedTerm = StringUtils.capitalize(proposedTerm);
		}
		return proposedTerm;
	}

	private boolean firstIngredientCS(Concept c, String langRefset, CharacteristicType charType) throws TermServerScriptException {
		
		List<Description> ingredDescs = getOrderedIngredientDescriptions(c, langRefset, charType);
		if (ingredDescs.isEmpty()) {
			throw new TermServerScriptException("Failed to find ingredient description for " + c);
		}
		return ingredDescs.get(0)
				.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE);
	}

	private String getCdSuffix(Concept c, boolean isFSN, String langRefset) throws TermServerScriptException {
		String suffix;
		String unitOfPresentation = DrugUtils.getAttributeType(c, HAS_UNIT_OF_PRESENTATION, isFSN, langRefset);
		String doseForm = DrugUtils.getDosageForm(c, isFSN, langRefset);
		boolean isActuation = unitOfPresentation.equals("actuation");
		
		if (INCLUDE_UNIT_OF_PRESENTATION ||
				(hasAttribute(c, HAS_UNIT_OF_PRESENTATION) 
						&& !doseForm.endsWith(unitOfPresentation)
						&& !doseForm.startsWith(unitOfPresentation)) ) {
			if ((isFSN  && !SPECIFY_DENOMINATOR) || isActuation ) {
				if (isActuation && !isFSN) {
					suffix = "/" + unitOfPresentation + " "  + doseForm;
				} else {
					suffix = "/1 " + unitOfPresentation + " "  + doseForm;
				}
			} else {
				suffix = " " + DrugUtils.getDosageForm(c, isFSN, langRefset) + " "  + unitOfPresentation;
			}
		} else {
			if (isFSN && !SPECIFY_DENOMINATOR && !hasAttribute(c, HAS_CONC_STRENGTH_DENOM_VALUE)) {
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
				//Note that we don't do FSN PT Counterparts here due to US/GB Issues
				boolean isInactivated = removeDescription(c,d);
				String msg = (isInactivated?"Inactivated redundant desc ":"Deleted redundant desc ") +  d;
				report(t, c, Severity.LOW, ReportActionType.DESCRIPTION_INACTIVATED, msg);
				changesMade++;
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
		String pt = c.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
		String badTerm = pt.replace(" product", "");
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

	private void validateUsGbVariance(Task t, Concept c, CharacteristicType charType, boolean allowDuplicates) throws TermServerScriptException {
		//If the ingredient names have US/GB variance, the Drug should too.
		//If the unit of presentation is present and has variance, the drug should too.
		//Do we have two preferred terms?
		boolean drugVariance = c.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1;
		boolean ingredientVariance = checkIngredientVariance(c, charType);
		Boolean doseFormVariance = checkTypeValueVariance(c, HAS_MANUFACTURED_DOSE_FORM, charType);
		Boolean presentationVariance = checkTypeValueVariance(c, HAS_UNIT_OF_PRESENTATION, charType);  //May be null if no presentation is specified
		boolean strengthUnitVariance = checkStrengthUnitsVariance(c, charType);
		
		if (drugVariance != ingredientVariance || 
				(presentationVariance != null && !drugVariance && presentationVariance) || 
				(doseFormVariance != null && drugVariance != doseFormVariance) ||
				drugVariance != strengthUnitVariance) {
			if (!drugVariance) {
				String msg = "Drug vs Ingredient vs Presentation US/GB term variance mismatch : Drug=" + drugVariance + 
						", Ingredients=" + ingredientVariance + 
						", Presentation=" + presentationVariance +
						", DoseForm=" + doseFormVariance + 
						", StrengthUnits=" + strengthUnitVariance;
				report(t, c, Severity.MEDIUM, ReportActionType.VALIDATION_CHECK, msg);
				splitPreferredTerm(t, c, allowDuplicates);
			}
		} 
	}

	private Boolean checkTypeValueVariance(Concept c, Concept relType, CharacteristicType charType) throws TermServerScriptException {
		Boolean relTypeVariance = null;  
		Set<Relationship> presentationRels = c.getRelationships(charType, relType, ActiveState.ACTIVE);
		if (presentationRels.size() > 0) {
			relTypeVariance = false;
		}
		for (Relationship r : presentationRels) {
			Concept target = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			//Does the target have more than one PT?
			if (target.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1 ) {
				relTypeVariance = true;
				break;
			}
		}
		return relTypeVariance;
	}
	
	

	private boolean checkIngredientVariance(Concept c, CharacteristicType charType) throws TermServerScriptException {
		boolean ingredientVariance = false;
		//Now check the ingredients
		Set<Relationship> ingredients = c.getRelationships(charType, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
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
	
	private boolean checkStrengthUnitsVariance(Concept c, CharacteristicType charType) throws TermServerScriptException {
		boolean strengthUnitsVariance = false;
		//Collect all possible strength units and work out if any should be us/gb specific
		//Watch that for never abbreviated cases, we might need to hard code since the FSN is US only
		Concept[] strengthTypes = new Concept[] { HAS_PRES_STRENGTH_UNIT, HAS_PRES_STRENGTH_DENOM_UNIT, 
												HAS_CONC_STRENGTH_UNIT, HAS_CONC_STRENGTH_DENOM_UNIT };
		Set<Concept> strengthUnits = SnomedUtils.getTargets(c, strengthTypes, charType);
		for (Concept unit : strengthUnits) {
			//Use in-memory copy so we have all descriptions
			unit = GraphLoader.getGraphLoader().getConcept(unit.getConceptId());
			//Does the unit have more than one PT?
			//OR is it never abbreviated (ie use FSN) and contains the word liter?
			if (unit.getDescriptions(Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE).size() > 1 || 
					(neverAbbrev.contains(unit) && unit.getFsn().contains(LITER)) ) {
				strengthUnitsVariance = true;
				break;
			} 
		}
		return strengthUnitsVariance;
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
			report(t, c, Severity.CRITICAL, ReportActionType.API_ERROR, "Unexpected number of preferred terms: " + preferredTerms.size());
		} else {
			Description usPT = preferredTerms.iterator().next();
			usPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(US_ENG_LANG_REFSET, GB_ENG_LANG_REFSET));
			Description gbPT = usPT.clone(null);
			gbPT.setTerm("GBTERM:" + gbPT.getTerm());
			gbPT.setAcceptabilityMap(SnomedUtils.createPreferredAcceptableMap(GB_ENG_LANG_REFSET, US_ENG_LANG_REFSET));
			report(t, c, Severity.HIGH, ReportActionType.DESCRIPTION_ADDED, "Split PT into US/GB variants: " + usPT + "/" + gbPT);
			c.addDescription(gbPT, allowDuplicates);
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
		Set<Relationship> ingredientRels = c.getRelationships(charType, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		ingredientRels.addAll(c.getRelationships(charType, HAS_PRECISE_INGRED, ActiveState.ACTIVE));
		Set<String> ingredientsWithStrengths = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);  //Will naturally sort in alphabetical order
		
		for (Relationship r : ingredientRels) {
			//Need to recover the full concept to have all descriptions, not the partial one stored as the target.
			Concept ingredient = GraphLoader.getGraphLoader().getConcept(r.getTarget().getConceptId());
			
			//Do we have a BoSS in the same group?
			Concept boSS = SnomedUtils.getTarget(c, new Concept[] {HAS_BOSS}, r.getGroupId(), charType);
			
			//Are we adding the strength?
			String strength = null;
			Object strengthObj = SnomedUtils.getConcreteValue(c, new Concept[] {HAS_PRES_STRENGTH_VALUE, HAS_CONC_STRENGTH_VALUE}, r.getGroupId(), charType);
			if (strengthObj != null) {
				strength = strengthObj.toString();
			}
			
			//Are we adding the denominator strength and units?
			String denominatorStr = "";
			if (SPECIFY_DENOMINATOR || hasAttribute(c, HAS_CONC_STRENGTH_DENOM_VALUE)) {
				denominatorStr = "/";
				String denStrenStr = SnomedUtils.getConcreteValue(c, new Concept[] {HAS_PRES_STRENGTH_DENOM_VALUE, HAS_CONC_STRENGTH_DENOM_VALUE}, r.getGroupId(), charType);
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
			ingredientsWithStrengths.add(ingredientWithStrengthTerm);
		}
		return ingredientsWithStrengths;
	}

	private List<Description> getOrderedIngredientDescriptions(Concept c, String langRefset, CharacteristicType charType) {
		Set<Relationship> ingredientRels = c.getRelationships(charType, HAS_ACTIVE_INGRED, ActiveState.ACTIVE);
		ingredientRels.addAll(c.getRelationships(charType, HAS_PRECISE_INGRED, ActiveState.ACTIVE));
		return ingredientRels.stream()
				.map(r -> GraphLoader.getGraphLoader().getConceptSafely(r.getTarget().getConceptId()))
				.map(i -> i.getPreferredSynonymSafely(langRefset))
				.sorted((d1, d2) -> d1.getTerm().compareTo(d2.getTerm()))
				.toList();
	}

	private boolean hasAttribute(Concept c, Concept attrib) {
		//Dose this concept specify a concentration denominator?
		return c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, attrib, ActiveState.ACTIVE).size() > 0;
	}

	private String formIngredientWithStrengthTerm(Concept ingredient, Concept boSS, String strength, Concept unit, String denominatorStr, boolean isFSN, String langRefset) throws TermServerScriptException {
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
			ingredientTerm += " " + strength;
		}
		
		if (unit != null) {
			String unitForConcat = getTermForConcat(unit, isFSN || neverAbbrev.contains(unit), langRefset);
			if (!isFSN && neverAbbrev.contains(unit) && unitForConcat.contains("liter") && langRefset.equals(GB_ENG_LANG_REFSET)) {
				unitForConcat = unitForConcat.replace("liter", "litre");
			}
			ingredientTerm += " " + unitForConcat;
		}
		
		ingredientTerm += denominatorStr;
		
		return ingredientTerm;
	}

	private String getTermForConcat(Concept c, boolean useFSN, String langRefset) throws TermServerScriptException {
		Description desc;
		String term;
		//Do we have a PT override for this concept?
		if (overridePTs.containsKey(c)) {
			return overridePTs.get(c);
		}

		if (useFSN) {
			desc = c.getFSNDescription();
			term = SnomedUtils.deconstructFSN(desc.getTerm())[0];
		} else {
			desc = c.getPreferredSynonym(langRefset);
			if (desc == null) {
				throw new TermServerScriptException("No preferred description found for " + c + " in " + langRefset);
			}
			term = desc.getTerm();
		}
		if (!desc.getCaseSignificance().equals(CaseSignificance.ENTIRE_TERM_CASE_SENSITIVE)) {
			term = StringUtils.deCapitalize(term);
		}
		return term;
	}

	@Override
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
	
	public void setPtOnly(boolean ptOnly) {
		this.ptOnly = ptOnly;
	}
	
}
