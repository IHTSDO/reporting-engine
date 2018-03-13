package org.ihtsdo.termserver.scripting.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.apache.commons.lang.StringUtils;
import org.ihtsdo.termserver.scripting.GraphLoader;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.TermServerScript.ReportActionType;
import org.ihtsdo.termserver.scripting.TermServerScript.Severity;
import org.ihtsdo.termserver.scripting.domain.*;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.Acceptability;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CaseSignificance;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.CharacteristicType;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.DescriptionType;

public class DrugUtils implements RF2Constants {
	
	public static final String CD = "(clinical drug)";
	public static final String MP = "(medicinal product)";
	public static final String MPF = "(medicinal product form)";
	
	static Map<String, Concept> numberConceptMap;
	static Map<String, Concept> doseFormConceptMap;
	static Map<String, Concept> unitConceptMap;
	
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
		return numberConceptMap.get(number);
	}

	private static void populateNumberConceptMap() throws TermServerScriptException {
		numberConceptMap = new HashMap<>();
		Concept numberSubHierarchy = GraphLoader.getGraphLoader().getConcept("260299005"); // |Number (qualifier value)|
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
		return doseFormConceptMap.get(fsn);
	}

	private static void populateDoseFormConceptMap() throws TermServerScriptException {
		doseFormConceptMap = new HashMap<>();
		Concept doseFormSubHierarchy = GraphLoader.getGraphLoader().getConcept("736542009"); // |Pharmaceutical dose form (dose form)|
		for (Concept doseForm : doseFormSubHierarchy.getDescendents(NOT_SET)) {
			doseFormConceptMap.put(doseForm.getFsn(), doseForm);
		}
	}
	
	public static Concept findUnit(String unit) throws TermServerScriptException {
		if (unitConceptMap == null) {
			populateUnitConceptMap();
		}
		return unitConceptMap.get(unit);
	}

	private static void populateUnitConceptMap() throws TermServerScriptException {
		unitConceptMap = new HashMap<>();
		Concept unitSubHierarchy = GraphLoader.getGraphLoader().getConcept("258666001"); // |Unit (qualifier value)|
		for (Concept unit : unitSubHierarchy.getDescendents(NOT_SET)) {
			unitConceptMap.put(unit.getFsn(), unit);
		}
	}

	public static  String getDosageForm(Concept concept) {
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
