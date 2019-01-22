package org.ihtsdo.termserver.scripting.util;

import org.ihtsdo.termserver.scripting.domain.Concept;

public class StrengthUnit {
	private Double strength;
	private Concept unit;
	
	public StrengthUnit (Double strength, Concept unit) {
		this.strength = strength;
		this.unit = unit;
	}
	
	public String getStrengthStr() {
		return DrugUtils.toString(strength);
	}
	
	public Double getStrength() {
		return strength;
	}
	
	public Concept getUnit() {
		return unit;
	}

	public void setStrength(Double strength) {
		this.strength = strength;
	}

	public void setUnit(Concept unit) {
		this.unit = unit;
	}
	
	public String toString () {
		return getStrengthStr() + " " + unit.getPreferredSynonym();
	}

}
