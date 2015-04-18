package org.ihtsdo.snowowl.authoring.api.model.logical;

public class AttributeRestriction {

	private String domainConceptId;
	private RangeRelationType rangeRelationType;
	private String rangeConceptId;

	public AttributeRestriction(String domainConceptId, RangeRelationType rangeRelationType, String rangeConceptId) {
		this.domainConceptId = domainConceptId;
		this.rangeRelationType = rangeRelationType;
		this.rangeConceptId = rangeConceptId;
	}

	public String getDomainConceptId() {
		return domainConceptId;
	}

	public void setDomainConceptId(String domainConceptId) {
		this.domainConceptId = domainConceptId;
	}

	public RangeRelationType getRangeRelationType() {
		return rangeRelationType;
	}

	public void setRangeRelationType(RangeRelationType rangeRelationType) {
		this.rangeRelationType = rangeRelationType;
	}

	public String getRangeConceptId() {
		return rangeConceptId;
	}

	public void setRangeConceptId(String rangeConceptId) {
		this.rangeConceptId = rangeConceptId;
	}

	@Override
	public String toString() {
		return "AttributeRestriction{" +
				"domainConceptId='" + domainConceptId + '\'' +
				", rangeType=" + rangeRelationType +
				", rangeConceptId='" + rangeConceptId + '\'' +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AttributeRestriction that = (AttributeRestriction) o;

		if (domainConceptId != null ? !domainConceptId.equals(that.domainConceptId) : that.domainConceptId != null) return false;
		if (rangeRelationType != that.rangeRelationType) return false;
		return !(rangeConceptId != null ? !rangeConceptId.equals(that.rangeConceptId) : that.rangeConceptId != null);

	}

	@Override
	public int hashCode() {
		int result = domainConceptId != null ? domainConceptId.hashCode() : 0;
		result = 31 * result + (rangeRelationType != null ? rangeRelationType.hashCode() : 0);
		result = 31 * result + (rangeConceptId != null ? rangeConceptId.hashCode() : 0);
		return result;
	}
}
