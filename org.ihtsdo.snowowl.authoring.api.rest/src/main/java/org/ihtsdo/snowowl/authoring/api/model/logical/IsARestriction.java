package org.ihtsdo.snowowl.authoring.api.model.logical;

public class IsARestriction {

	private String conceptId;

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	@Override
	public String toString() {
		return "IsARestriction{" +
				"conceptId='" + conceptId + '\'' +
				'}';
	}
}
