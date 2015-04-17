package org.ihtsdo.snowowl.authoring.api.model.logical;

public class IsARestriction {

	private String conceptId;
	private RangeRelationType rangeRelationType;

	public IsARestriction() {
	}

	public IsARestriction(String conceptId, RangeRelationType rangeRelationType) {
		this.conceptId = conceptId;
		this.rangeRelationType = rangeRelationType;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}


	public RangeRelationType getRangeRelationType() {
		return rangeRelationType;
	}

	public void setRangeRelationType(RangeRelationType rangeRelationType) {
		this.rangeRelationType = rangeRelationType;
	}

	@Override
	public String toString() {
		return "IsARestriction{" +
				"conceptId='" + conceptId + '\'' +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		IsARestriction that = (IsARestriction) o;

		if (conceptId != null ? !conceptId.equals(that.conceptId) : that.conceptId != null) return false;
		return rangeRelationType == that.rangeRelationType;

	}

	@Override
	public int hashCode() {
		int result = conceptId != null ? conceptId.hashCode() : 0;
		result = 31 * result + (rangeRelationType != null ? rangeRelationType.hashCode() : 0);
		return result;
	}
}
