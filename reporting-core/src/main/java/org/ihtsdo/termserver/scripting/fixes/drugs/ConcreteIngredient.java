package org.ihtsdo.termserver.scripting.fixes.drugs;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.Concept;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConcreteIngredient {

	private static final Logger LOGGER = LoggerFactory.getLogger(ConcreteIngredient.class);

	public Concept doseForm;
	public Concept substance;
	public Concept boss;
	
	public String presStrength;
	public Concept presNumeratorUnit;
	public String presDenomQuantity;
	public Concept presDenomUnit;
	
	public String concStrength;
	public Concept concNumeratorUnit;
	public String concDenomQuantity;
	public Concept concDenomUnit;
	
	public Concept unitOfPresentation;

	public String presToString() throws TermServerScriptException {
		String str = "";
		if (presStrength != null) {
			str += presStrength;
			str += presNumeratorUnit.getPreferredSynonym();
		}
		
		if (presDenomQuantity != null) {
			str += "/" + presDenomQuantity;
			str += presDenomUnit.getPreferredSynonym();
		}
		return str;
	}
	
	public String concToString() throws TermServerScriptException {
		String str = "";
		if (concStrength != null) {
			str += concStrength;
			str += concNumeratorUnit.getPreferredSynonym();
		}
		
		if (concDenomQuantity != null) {
			str += "/" + concDenomQuantity;
			str += concDenomUnit.getPreferredSynonym();
		}
		return str;
	}
}
