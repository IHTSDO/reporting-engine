package org.ihtsdo.termserver.scripting.template;

public class Cardinality {
	int min;
	int max;
	
	public int getMin() {
		return min;
	}
	public void setMin(int min) {
		this.min = min;
	}
	public int getMax() {
		return max;
	}
	public void setMax(int max) {
		this.max = max;
	}
	
	public String toString() {
		return "[" + min + ".." + (max==Integer.MAX_VALUE?"*":max) + "]";
	}
	
}
