package org.ihtsdo.termserver.scripting.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.lang.ArrayUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.*;
import org.ihtsdo.termserver.scripting.domain.*;

import org.ihtsdo.termserver.scripting.fixes.drugs.Ingredient;

public class DrugUtils implements RF2Constants {
	
	public static final String CD = "(clinical drug)";
	public static final String MP = "(medicinal product)";
	public static final String MPF = "(medicinal product form)";
	public static final String PRODUCT = "(product)";
	
	public static final Concept [] solidUnits = new Concept [] { PICOGRAM, NANOGRAM, MICROGRAM, MILLIGRAM, GRAM };
	public static final Concept [] liquidUnits = new Concept [] { MICROLITER, MILLILITER, LITER };
	public static final Concept [] equivUnits = new Concept [] { UEQ, MEQ };
	
	static Map<String, Concept> numberConceptMap;
	static Map<String, Concept> doseFormFSNConceptMap;
	static Map<String, Concept> doseFormSynonymConceptMap;
	static Map<String, Concept> unitOfPresentationConceptMap;
	static Map<String, Concept> unitOfMeasureConceptMap;
	static Map<String, Concept> substanceMap;
	
	//The danger here is that we can't name them uniquely
	static Set<Concept> dangerousSubstances = new HashSet<>();  
	
	public static void setConceptType(Concept c) {
		if (c.getConceptType() != null) {
			return;
		}
		String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		boolean hasBaseCount = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, COUNT_BASE_ACTIVE_INGREDIENT, ActiveState.ACTIVE).size() > 0;
		switch (semTag) {
			case "(medicinal product form)" : c.setConceptType(hasBaseCount ? ConceptType.MEDICINAL_PRODUCT_FORM_ONLY : ConceptType.MEDICINAL_PRODUCT_FORM);
												break;
			case "(product)" : c.setConceptType(ConceptType.PRODUCT);
								break;
			case "(medicinal product)" : c.setConceptType(hasBaseCount ? ConceptType.MEDICINAL_PRODUCT_ONLY : ConceptType.MEDICINAL_PRODUCT);
										 break;
			case "(clinical drug)" : c.setConceptType(ConceptType.CLINICAL_DRUG);
										break;
			default : c.setConceptType(ConceptType.UNKNOWN);
		}
	}

	public static Concept getNumberAsConcept(Object number) throws TermServerScriptException {
		String numberStr = number.toString();
		if (numberConceptMap == null) {
			populateNumberConceptMap();
		}
		if (!numberConceptMap.containsKey(numberStr)) {
			TermServerScript.warn("Check and possibly need to create concept to represent number: " + numberStr);
		}
		return numberConceptMap.get(numberStr);
	}
	
	public static void registerNewNumber(String numberStr, Concept number) {
		numberConceptMap.put(numberStr, number);
	}
	
	public static double getConceptAsNumber(Concept number) throws TermServerScriptException {
		String numStr = SnomedUtils.deconstructFSN(number.getFsn())[0];
		return Double.parseDouble(numStr);
	}
	
	public static String getConceptAsNumberStr(Concept number) throws TermServerScriptException {
		String numStr = SnomedUtils.deconstructFSN(number.getFsn())[0];
		double d = Double.parseDouble(numStr);
		return toString(d);
	}

	private static void populateNumberConceptMap() throws TermServerScriptException {
		numberConceptMap = new HashMap<>();
		Concept numberSubHierarchy = GraphLoader.getGraphLoader().getConcept("260299005", false, true); // |Number (qualifier value)|
		for (Concept number : numberSubHierarchy.getDescendents(NOT_SET)) {
			String numStr = SnomedUtils.deconstructFSN(number.getFsn())[0].trim();
			try {
				Double.parseDouble(numStr);
				numberConceptMap.put(numStr, number);
			} catch (Exception e) {}
		}
	}
	
	public static Concept findDoseFormFromFSN(String fsn) throws TermServerScriptException {
		if (doseFormFSNConceptMap == null) {
			populateDoseFormConceptMap();
		}
		if (!doseFormFSNConceptMap.containsKey(fsn)) {
			throw new TermServerScriptException("Unable to identify dose form : " + fsn);
		}
		return doseFormFSNConceptMap.get(fsn);
	}

	private static void populateDoseFormConceptMap() throws TermServerScriptException {
		doseFormFSNConceptMap = new HashMap<>();
		Concept doseFormSubHierarchy = GraphLoader.getGraphLoader().getConcept("736542009", false, true); // |Pharmaceutical dose form (dose form)|
		for (Concept doseForm : doseFormSubHierarchy.getDescendents(NOT_SET)) {
			doseFormFSNConceptMap.put(doseForm.getFsn(), doseForm);
		}
	}
	
	public static Concept findDoseFormFromSynonym(String term) throws TermServerScriptException {
		if (doseFormSynonymConceptMap == null) {
			populateDoseFormSynonymMap();
		}
		if (!doseFormSynonymConceptMap.containsKey(term)) {
			throw new TermServerScriptException("Unable to identify dose form : " + term);
		}
		return doseFormSynonymConceptMap.get(term);
	}

	private static void populateDoseFormSynonymMap() throws TermServerScriptException {
		doseFormSynonymConceptMap = new HashMap<>();
		Concept doseFormSubHierarchy = GraphLoader.getGraphLoader().getConcept("736542009", false, true); // |Pharmaceutical dose form (dose form)|
		for (Concept doseForm : doseFormSubHierarchy.getDescendents(NOT_SET)) {
			for (Description d : doseForm.getDescriptions(ActiveState.ACTIVE, Collections.singletonList(DescriptionType.SYNONYM))) {
				String term = d.getTerm().toLowerCase();
				if (!doseFormSynonymConceptMap.containsKey(term)) {
					doseFormSynonymConceptMap.put(term, doseForm);
				} else {
					TermServerScript.warn("Dose form map contains two concepts with term " + term + ": " + doseForm + " and " + doseFormSynonymConceptMap.get(term));
				}
			}
		}
	}
	
	public static Concept findUnitOfPresentation(String fsn) throws TermServerScriptException {
		if (unitOfPresentationConceptMap == null) {
			populateUnitOfPresentationConceptMap();
		}
		if (!unitOfPresentationConceptMap.containsKey(fsn)) {
			throw new TermServerScriptException("Unable to identify unit of presentation : " + fsn);
		}
		return unitOfPresentationConceptMap.get(fsn);
	}
	
	private static void populateUnitOfPresentationConceptMap() throws TermServerScriptException {
		unitOfPresentationConceptMap = new HashMap<>();
		Concept unitOfPresentationSubHierarchy = GraphLoader.getGraphLoader().getConcept("732935002", false, true); //|Unit of presentation (unit of presentation)|
		for (Concept unitOfPresenation : unitOfPresentationSubHierarchy.getDescendents(NOT_SET)) {
			unitOfPresentationConceptMap.put(unitOfPresenation.getFsn(), unitOfPresenation);
		}
	}
	
	public static Concept findUnitOfMeasure(String unit) throws TermServerScriptException {
		unit = unit.toLowerCase();
		//Try a straight lookup via the SCTID if we have one
		try {
			return GraphLoader.getGraphLoader().getConcept(unit);
		} catch (Exception e) {}
		
		if (unitOfMeasureConceptMap == null) {
			populateUnitOfMeasureConceptMap();
		}
		if (!unitOfMeasureConceptMap.containsKey(unit)) {
			throw new TermServerScriptException("Unable to identify unit of measure: '" + unit + "'");
		}
		return unitOfMeasureConceptMap.get(unit);
	}

	private static void populateUnitOfMeasureConceptMap() throws TermServerScriptException {
		unitOfMeasureConceptMap = new HashMap<>();
		//UAT workaround
		//Concept unitSubHierarchy = GraphLoader.getGraphLoader().getConcept("258666001", false, true); //  |Unit(qualifier value)|
		Concept unitSubHierarchy = GraphLoader.getGraphLoader().getConcept("767524001", false, true); //  |Unit of measure (qualifier value)|
		for (Concept unit : unitSubHierarchy.getDescendents(NOT_SET)) {
			unitOfMeasureConceptMap.put(unit.getFsn(), unit);
			unitOfMeasureConceptMap.put(unit.getPreferredSynonym().toLowerCase(), unit);
		}
		
		//We can also measure just 'unit' of a substance
		unitOfMeasureConceptMap.put(UNIT.getFsn(), UNIT);
	}
	
	public static Concept findSubstance(String substanceName) throws TermServerScriptException {
		if (substanceMap == null) {
			populateSubstanceMap();
		}
		Concept substance;
		substanceName = substanceName.toLowerCase().trim();
		if (!substanceMap.containsKey(substanceName)) {
			//Do we have an SCTID|FSN?
			substance = GraphLoader.getGraphLoader().getConcept(substanceName, false, false); //Don't create, don't validate
			if (substance == null) {
				throw new TermServerScriptException("Unable to identify substance : " + substanceName);
			}
		} else {
			substance = substanceMap.get(substanceName);
			if (dangerousSubstances.contains(substance)) {
				TermServerScript.warn("Lookup performed on substance that isn't uniquely named: '" + substanceName +"'. Using " + substance);
			}
		}
		return substance;
	}

	private static void populateSubstanceMap() throws TermServerScriptException {
		TermServerScript.info("Populating substance map");
		substanceMap = new HashMap<>();
		GraphLoader gl = GraphLoader.getGraphLoader();
		for (Concept c : gl.getDescendantsCache().getDescendents(SUBSTANCE)) {
			for (Description d : c.getDescriptions(ActiveState.ACTIVE)) {
				String term = d.getTerm().toLowerCase().trim();
				if (d.getType().equals(DescriptionType.FSN)) {
					term = SnomedUtils.deconstructFSN(term)[0];
				}
				//Do we already know about this term?  No problem if it's the same concept
				Concept existing = substanceMap.get(term);
				if (existing == null) {
					substanceMap.put(term, c);
				} else if (!c.equals(existing)) {
					//The one that's the preferred term will win
					if (d.isPreferred()) {
						substanceMap.put(term, c);
					}
					dangerousSubstances.add(c);
					dangerousSubstances.add(existing);
					//We won't worry about these unless someone tries to look one up
					//TermServerScript.warn("Collision between terms in concepts " + c + " and " + existing);
				}
			}
		}
		TermServerScript.info("Populated substance map with " + substanceMap.size() + " concepts.");
	}

	public static  String getDosageForm(Concept concept, boolean isFSN, String langRefset) throws TermServerScriptException {
		Set<Relationship> doseForms = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE);
		if (doseForms.size() == 0) {
			return "NO STATED DOSE FORM DETECTED";
		} else if (doseForms.size() > 1) {
			return "MULTIPLE DOSE FORMS";
		} else {
			//Load full locally cached object since we may be working with some minimally defined thing
			Concept doseForm = GraphLoader.getGraphLoader().getConcept(doseForms.iterator().next().getTarget().getConceptId());
			String doseFormStr;
			if (isFSN) {
				doseFormStr = SnomedUtils.deconstructFSN(doseForm.getFsn())[0];
			} else {
				doseFormStr = doseForm.getPreferredSynonym(langRefset).getTerm();
			}
			return StringUtils.deCapitalize(doseFormStr);
		}
	}
	
	public static  String getAttributeType(Concept concept, Concept type, boolean isFSN, String langRefset) throws TermServerScriptException {
		Set<Relationship> rels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, type, ActiveState.ACTIVE);
		Concept value;
		if (rels.size() == 0) {
			return "NO " + SnomedUtils.getPT(type.getConceptId()) + "DETECTED";
		} else if (rels.size() > 1) {
			//OK to return a value as long as they're all the same 
			value = rels.iterator().next().getTarget();
			for (Relationship rel : rels) {
				if (!rel.getTarget().equals(value)) {
					return "MULTIPLE DIFFERENT" + SnomedUtils.getPT(type.getConceptId()) + "DETECTED";
				}
			}
		} else {
			value = rels.iterator().next().getTarget();
		}

		//Load full locally cached object since we may be working with some minimally defined thing
		value = GraphLoader.getGraphLoader().getConcept(value.getConceptId());
		String valueStr;
		if (isFSN) {
			valueStr = SnomedUtils.deconstructFSN(value.getFsn())[0];
		} else {
			valueStr = value.getPreferredSynonym(langRefset).getTerm();
		}
		return StringUtils.deCapitalize(valueStr);
	}

	public static boolean isModificationOf(Concept specific, Concept general) {
		//Check if the specific concept has a modification attribute of the more general substance
		//and if there is a Modification Of attribute, can also call recursively
		Set<Relationship> modifications = specific.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE);
		for (Relationship modification : modifications) {
			if (modification.getTarget().equals(general) || isModificationOf(specific, modification.getTarget())) {
				return true;
			}
		}
		return false;
	}
	
	public static Set<Relationship> getIngredientRelationships (Concept c, CharacteristicType charType) throws TermServerScriptException {
		Set<Relationship> ingredientRels = new HashSet<>();
		for (Relationship r : c.getRelationships(charType, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
			ingredientRels.add(r);
		}
		for (Relationship r : c.getRelationships(charType, HAS_PRECISE_INGRED, ActiveState.ACTIVE)) {
			ingredientRels.add(r);
		}
		return ingredientRels;
	}
	

	public static List<Concept> getIngredients(Concept c, CharacteristicType charType) throws TermServerScriptException {
		Set<Concept> ingredients = getIngredientRelationships(c, charType)
				.stream()
				.map(r -> r.getTarget())
				.collect(Collectors.toSet());
		
		//With any duplicates dealt with in set, now sort on FSN 
		return ingredients.stream()
				.sorted(Comparator.comparing(Concept::getFsn))
				.collect(Collectors.toList());
	}

	public static Ingredient getIngredientDetails(Concept c, int groupId, CharacteristicType charType) throws TermServerScriptException {
		//Populate as much data as can be found in this group.
		Ingredient i = new Ingredient();
		i.substance = SnomedUtils.getTarget(c, new Concept[] { HAS_ACTIVE_INGRED,  HAS_PRECISE_INGRED}, groupId, charType);
		
		i.presStrength = SnomedUtils.getTarget(c, new Concept[] { HAS_PRES_STRENGTH_VALUE }, groupId, charType);
		i.presNumeratorUnit = SnomedUtils.getTarget(c, new Concept[] { HAS_PRES_STRENGTH_UNIT }, groupId, charType);
		i.presDenomQuantity = SnomedUtils.getTarget(c, new Concept[] {HAS_PRES_STRENGTH_DENOM_VALUE }, groupId, charType);
		i.presDenomUnit = SnomedUtils.getTarget(c, new Concept[] {HAS_PRES_STRENGTH_DENOM_UNIT }, groupId, charType);
		
		i.concStrength = SnomedUtils.getTarget(c, new Concept[] { HAS_CONC_STRENGTH_VALUE }, groupId, charType);
		i.concNumeratorUnit = SnomedUtils.getTarget(c, new Concept[] { HAS_CONC_STRENGTH_UNIT }, groupId, charType);
		i.concDenomQuantity = SnomedUtils.getTarget(c, new Concept[] {HAS_CONC_STRENGTH_DENOM_VALUE }, groupId, charType);
		i.concDenomUnit = SnomedUtils.getTarget(c, new Concept[] {HAS_CONC_STRENGTH_DENOM_UNIT }, groupId, charType);
		
		return i;
	}
	
	public static boolean normalizeStrengthUnit (StrengthUnit su) {
		boolean changeMade = false;
		Concept[] unitsArray = null;
		int currentIdx =  ArrayUtils.indexOf(solidUnits, su.getUnit());
		if (currentIdx != NOT_SET) {
			unitsArray = solidUnits;
		} else {
			currentIdx =  ArrayUtils.indexOf(liquidUnits, su.getUnit());
			if (currentIdx != NOT_SET) {
				unitsArray = liquidUnits;
			} else {
				currentIdx =  ArrayUtils.indexOf(equivUnits, su.getUnit());
				if (currentIdx != NOT_SET) {
					unitsArray = equivUnits;
				}
			}
		}
		GraphLoader gl = GraphLoader.getGraphLoader();
		if (currentIdx != NOT_SET) {
			if (su.getStrength() >= 1000) {
				su.setUnit(gl.getConceptSafely(unitsArray[currentIdx + 1].getConceptId()));
				su.setStrength(su.getStrength() / 1000D);
				changeMade = true;
			} else if (su.getStrength() <1) {
				su.setUnit(gl.getConceptSafely(unitsArray[currentIdx - 1].getConceptId()));
				su.setStrength(su.getStrength() * 1000D);
				changeMade = true;
			}
		}
		if (changeMade) {
			//See if we need to shift by another 1000 eg for 0.000313
			normalizeStrengthUnit(su);
		}
		return changeMade;
	}
	public static String toString(double d)
	{
		d = new BigDecimal(d).setScale(6, RoundingMode.HALF_UP).doubleValue();
		if(d == (long) d)
			return String.format("%d",(long)d);
		else
			return String.format("%s",d);
	}

	public static Concept getBase(Concept c) {
		List<Concept> bases = c.getRelationships(CharacteristicType.STATED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE)
				.stream()
				.map(rel -> rel.getTarget())
				.collect(Collectors.toList());
		if (bases.size() == 0) {
			return c;
		} else if (bases.size() > 1) {
			throw new IllegalArgumentException("Concept " + c + " has multiple modification attributes");
		} else if (bases.get(0).equals(c)) {
			throw new IllegalArgumentException("Concept " + c + " is a modification of itself.");
		} else  {
			//Call recursively to follow the transitive nature of modification
			return getBase(bases.get(0));
		}
	}

	public static Set<Concept> getSubstancesUsedInProducts() throws TermServerScriptException {
		Set<Concept> substancesUsedInProducts = new HashSet<>();
		for (Concept product : PHARM_BIO_PRODUCT.getDescendents(NOT_SET)) {
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_ACTIVE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
			for (Relationship r : product.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, HAS_PRECISE_INGRED, ActiveState.ACTIVE)) {
				substancesUsedInProducts.add(r.getTarget());
			}
		}
		return substancesUsedInProducts;
	}
	
}
