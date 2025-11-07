package org.ihtsdo.termserver.scripting.domain;

public class ConcreteIngredient {

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
	
	public String presToString() {
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
	
	public String concToString() {
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
