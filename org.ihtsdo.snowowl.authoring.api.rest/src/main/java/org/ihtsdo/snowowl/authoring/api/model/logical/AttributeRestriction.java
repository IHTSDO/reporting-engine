package org.ihtsdo.snowowl.authoring.api.model.logical;

public class AttributeRestriction {

	private String domainConceptId;
	private RangeRelationType rangeRelationType;
	private String rangeConceptId;

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
}
