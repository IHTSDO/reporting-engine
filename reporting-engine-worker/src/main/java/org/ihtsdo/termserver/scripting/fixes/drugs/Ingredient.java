package org.ihtsdo.termserver.scripting.fixes.drugs;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;
import org.ihtsdo.termserver.scripting.domain.Description;
import org.ihtsdo.termserver.scripting.util.DrugUtils;

public class Ingredient {

	public Concept doseForm;
	public Concept substance;
	public Concept boss;
	
	public Concept presStrength;
	public Concept presNumeratorUnit;
	public Concept presDenomQuantity;
	public Concept presDenomUnit;
	
	public Concept concStrength;
	public Concept concNumeratorUnit;
	public Concept concDenomQuantity;
	public Concept concDenomUnit;
	
	public Concept unitOfPresentation;

	public String presToString() throws TermServerScriptException {
		String str = "";
		if (presStrength != null) {
			str += DrugUtils.getConceptAsNumberStr(presStrength);
			str += presNumeratorUnit.getPreferredSynonym();
		}
		
		if (presDenomQuantity != null) {
			str += "/" + DrugUtils.getConceptAsNumberStr(presDenomQuantity);
			str += presDenomUnit.getPreferredSynonym();
		}
		return str;
	}
	
	public String concToString() throws TermServerScriptException {
		String str = "";
		if (concStrength != null) {
			str += DrugUtils.getConceptAsNumberStr(concStrength);
			str += concNumeratorUnit.getPreferredSynonym();
		}
		
		if (concDenomQuantity != null) {
			str += "/" + DrugUtils.getConceptAsNumberStr(concDenomQuantity);
			str += concDenomUnit.getPreferredSynonym();
		}
		return str;
	}
}
