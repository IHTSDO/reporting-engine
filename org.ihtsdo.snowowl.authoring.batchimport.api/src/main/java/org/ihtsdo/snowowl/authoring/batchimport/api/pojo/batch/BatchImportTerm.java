package org.ihtsdo.snowowl.authoring.batchimport.api.pojo.batch;

public class BatchImportTerm {
	private String term;
	private boolean isFSN;
	private static char NOT_ACCEPTABLE = 'N';
	private char acceptabilityUS=NOT_ACCEPTABLE;
	private char acceptabilityGB=NOT_ACCEPTABLE;
	private String caseSensitivity;
	
	public BatchImportTerm(String term, boolean isFSN, char usAccept, char gbAccept) {
		this.term = term;
		this.isFSN = isFSN;
		this.acceptabilityUS = (usAccept == 0) ? NOT_ACCEPTABLE : usAccept;
		this.acceptabilityGB = (gbAccept == 0) ? NOT_ACCEPTABLE : gbAccept;
	}
	
	public String getTerm() {
		return term;
	}
	public void setTerm(String term) {
		this.term = term;
	}
	public boolean isFSN() {
		return isFSN;
	}
	public void setFSN(boolean isFSN) {
		this.isFSN = isFSN;
	}
	public char getAcceptabilityUS() {
		return acceptabilityUS;
	}
	public void setAcceptabilityUS(char acceptabilityUS) {
		this.acceptabilityUS = acceptabilityUS;
	}
	public char getAcceptabilityGB() {
		return acceptabilityGB;
	}
	public void setAcceptabilityGB(char acceptabilityGB) {
		this.acceptabilityGB = acceptabilityGB;
	}
	public String getCaseSensitivity() {
		return caseSensitivity;
	}
	public void setCaseSensitivity(String caseSensitivity) {
		this.caseSensitivity = caseSensitivity;
	}

}
