package org.ihtsdo.termserver.scripting.domain;

public class ConceptChange extends Concept {
	
	private String newTerm;
	private String currentTerm;
	public String caseSignificanceSctId;
	public String skipReason;
	
	public ConceptChange(String conceptId) {
		super(conceptId);
	}
	public ConceptChange(String conceptId, String newTerm) {
		super(conceptId);
		this.newTerm = newTerm;
	}
	public ConceptChange(String conceptId, String fsn, String newTerm) {
		super(conceptId);
		this.newTerm = newTerm;
		this.setFsn(fsn);
	}
	public String getNewTerm() {
		return newTerm;
	}
	public void setNewTerm(String newPreferredTerm) {
		this.newTerm = newPreferredTerm;
	}
	public String getCurrentTerm() {
		return currentTerm;
	}
	public void setCurrentTerm(
			String expectedCurrentPreferredTerm) {
		this.currentTerm = expectedCurrentPreferredTerm;
	}
	public String getCaseSensitivitySctId() {
		return caseSignificanceSctId;
	}
	public void setCaseSignificanceSctId(String caseSignificanceSctId) {
		this.caseSignificanceSctId = caseSignificanceSctId;
	}
	public String getSkipReason() {
		return skipReason;
	}
	public void setSkipReason(String skipReason) {
		this.skipReason = skipReason;
	}
}
