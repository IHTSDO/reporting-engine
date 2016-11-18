package org.ihtsdo.termserver.scripting.domain;

public class ConceptChange extends Concept {
	
	private String newPreferredTerm;
	private String expectedCurrentPreferredTerm;
	public String caseSignificanceSctId;
	
	public ConceptChange(String conceptId) {
		super(conceptId);
	}
	public String getNewPreferredTerm() {
		return newPreferredTerm;
	}
	public void setNewPreferredTerm(String newPreferredTerm) {
		this.newPreferredTerm = newPreferredTerm;
	}
	public String getExpectedCurrentPreferredTerm() {
		return expectedCurrentPreferredTerm;
	}
	public void setExpectedCurrentPreferredTerm(
			String expectedCurrentPreferredTerm) {
		this.expectedCurrentPreferredTerm = expectedCurrentPreferredTerm;
	}
	public String getCaseSensitivitySctId() {
		return caseSignificanceSctId;
	}
	public void setCaseSignificanceSctId(String caseSignificanceSctId) {
		this.caseSignificanceSctId = caseSignificanceSctId;
	}

}
