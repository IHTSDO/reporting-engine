package org.ihtsdo.termserver.scripting.domain;

public class ConceptChange extends Concept {
	
	private String newTerm;
	private String currentTerm;
	public String caseSignificanceSctId;
	
	public ConceptChange(String conceptId) {
		super(conceptId);
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

}
