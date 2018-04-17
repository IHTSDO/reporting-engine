package org.ihtsdo.termserver.scripting.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.*;

public class DrugUtils implements RF2Constants {
	
	public static final String CD = "(clinical drug)";
	public static final String MP = "(medicinal product)";
	public static final String MPF = "(medicinal product form)";
	
	static Map<String, Concept> numberConceptMap;
	static Map<String, Concept> doseFormConceptMap;
	static Map<String, Concept> unitOfPresentationConceptMap;
	static Map<String, Concept> unitOfMeasureConceptMap;
	
	public static void setConceptType(Concept c) {
		String semTag = SnomedUtils.deconstructFSN(c.getFsn())[1];
		switch (semTag) {
			case "(medicinal product form)" : c.setConceptType(ConceptType.MEDICINAL_PRODUCT_FORM);
												break;
			case "(product)" : c.setConceptType(ConceptType.PRODUCT);
								break;
			case "(medicinal product)" : c.setConceptType(ConceptType.MEDICINAL_PRODUCT);
										 break;
			case "(clinical drug)" : c.setConceptType(ConceptType.CLINICAL_DRUG);
										break;
			default : c.setConceptType(ConceptType.UNKNOWN);
		}
	}

	public static Concept getNumberAsConcept(String number) throws TermServerScriptException {
		if (numberConceptMap == null) {
			populateNumberConceptMap();
		}
		if (!numberConceptMap.containsKey(number)) {
			TermServerScript.warn("Check and possibly need to create concept to represent number: " + number);
		}
		return numberConceptMap.get(number);
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
	
	public static Concept findDoseForm(String fsn) throws TermServerScriptException {
		if (doseFormConceptMap == null) {
			populateDoseFormConceptMap();
		}
		if (!doseFormConceptMap.containsKey(fsn)) {
			throw new TermServerScriptException("Unable to identify dose form : " + fsn);
		}
		return doseFormConceptMap.get(fsn);
	}

	private static void populateDoseFormConceptMap() throws TermServerScriptException {
		doseFormConceptMap = new HashMap<>();
		Concept doseFormSubHierarchy = GraphLoader.getGraphLoader().getConcept("736542009", false, true); // |Pharmaceutical dose form (dose form)|
		for (Concept doseForm : doseFormSubHierarchy.getDescendents(NOT_SET)) {
			doseFormConceptMap.put(doseForm.getFsn(), doseForm);
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
		}
		
		//We can also measure just 'unit' of a substance
		unitOfMeasureConceptMap.put(UNIT.getFsn(), UNIT);
	}

	public static  String getDosageForm(Concept concept, boolean isFSN) throws TermServerScriptException {
		List<Relationship> doseForms = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_MANUFACTURED_DOSE_FORM, ActiveState.ACTIVE);
		if (doseForms.size() == 0) {
			return "NO STATED DOSE FORM DETECTED";
		} else if (doseForms.size() > 1) {
			return "MULTIPLE DOSE FORMS";
		} else {
			//Load full locally cached object since we may be working with some minimally defined thing
			Concept doseForm = GraphLoader.getGraphLoader().getConcept(doseForms.get(0).getTarget().getConceptId());
			String doseFormStr;
			if (isFSN) {
				doseFormStr = SnomedUtils.deconstructFSN(doseForm.getFsn())[0];
			} else {
				doseFormStr = doseForm.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			}
			
			doseFormStr = SnomedUtils.deCapitalize(doseFormStr);
			//Translate known issues
			switch (doseFormStr) {
				case "ocular dose form": doseFormStr =  "ophthalmic dosage form";
					break;
				case "inhalation dose form": doseFormStr = "respiratory dosage form";
					break;
				case "cutaneous AND/OR transdermal dosage form" : doseFormStr = "topical dosage form";
					break;
				case "oromucosal AND/OR gingival dosage form" : doseFormStr = "oropharyngeal dosage form";
					break;
			}
			
			//In the product we say "doseage form", so make that switch
			//doseForm = doseForm.replace(" dose ", " dosage ");
			
			return doseFormStr;
		}
	}
	
	public static  String getUnitOfPresentation(Concept concept, boolean isFSN) throws TermServerScriptException {
		List<Relationship> uopRels = concept.getRelationships(CharacteristicType.STATED_RELATIONSHIP, HAS_UNIT_OF_PRESENTATION, ActiveState.ACTIVE);
		if (uopRels.size() == 0) {
			return "NO UNIT OF PRESENTATION DETECTED";
		} else if (uopRels.size() > 1) {
			return "MULTIPLE UNIT OF PRESENTATION";
		} else {
			//Load full locally cached object since we may be working with some minimally defined thing
			Concept uop = GraphLoader.getGraphLoader().getConcept(uopRels.get(0).getTarget().getConceptId());
			String uopStr;
			if (isFSN) {
				uopStr = SnomedUtils.deconstructFSN(uop.getFsn())[0];
			} else {
				uopStr = uop.getPreferredSynonym(US_ENG_LANG_REFSET).getTerm();
			}
			return uopStr;
		}
	}

	public static boolean isModificationOf(Concept specific, Concept general) {
		//Check if the specific concept has a modification attribute of the more general substance
		//and if there is a Modification Of attribute, can also call recursively
		List<Relationship> modifications = specific.getRelationships(CharacteristicType.INFERRED_RELATIONSHIP, IS_MODIFICATION_OF, ActiveState.ACTIVE);
		for (Relationship modification : modifications) {
			if (modification.getTarget().equals(general) || isModificationOf(specific, modification.getTarget())) {
				return true;
			}
		}
		return false;
	}
}
