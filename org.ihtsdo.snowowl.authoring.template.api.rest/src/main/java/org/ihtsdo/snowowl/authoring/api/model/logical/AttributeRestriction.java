package org.ihtsdo.snowowl.authoring.single.api.model.logical;

import com.fasterxml.jackson.annotation.JsonProperty;

public class AttributeRestriction {

	private String typeConceptId;
	private RangeRelationType rangeRelationType;
	private String rangeConceptId;
	private String defaultValue;

	public AttributeRestriction() {
	}

	public AttributeRestriction(String typeConceptId, RangeRelationType rangeRelationType, String rangeConceptId) {
		this.typeConceptId = typeConceptId;
		this.rangeRelationType = rangeRelationType;
		this.rangeConceptId = rangeConceptId;
	}

	@JsonProperty(value = "attribute")
	public String getTypeConceptId() {
		return typeConceptId;
	}

	public void setTypeConceptId(String typeConceptId) {
		this.typeConceptId = typeConceptId;
	}

	@JsonProperty(value = "range")
	public RangeRelationType getRangeRelationType() {
		return rangeRelationType;
	}

	public void setRangeRelationType(RangeRelationType rangeRelationType) {
		this.rangeRelationType = rangeRelationType;
	}

	@JsonProperty(value = "value")
	public String getRangeConceptId() {
		return rangeConceptId;
	}

	public void setRangeConceptId(String rangeConceptId) {
		this.rangeConceptId = rangeConceptId;
	}

	public String getDefaultValue() {
		return defaultValue;
	}

	public void setDefaultValue(String defaultValue) {
		this.defaultValue = defaultValue;
	}

	@Override
	public String toString() {
		return "AttributeRestriction{" +
				"typeConceptId='" + typeConceptId + '\'' +
				", rangeType=" + rangeRelationType +
				", rangeConceptId='" + rangeConceptId + '\'' +
				", defaultValue='" + defaultValue + '\'' +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		AttributeRestriction that = (AttributeRestriction) o;

		if (typeConceptId != null ? !typeConceptId.equals(that.typeConceptId) : that.typeConceptId != null) return false;
		if (rangeRelationType != that.rangeRelationType) return false;
		return !(rangeConceptId != null ? !rangeConceptId.equals(that.rangeConceptId) : that.rangeConceptId != null);

	}

	@Override
	public int hashCode() {
		int result = typeConceptId != null ? typeConceptId.hashCode() : 0;
		result = 31 * result + (rangeRelationType != null ? rangeRelationType.hashCode() : 0);
		result = 31 * result + (rangeConceptId != null ? rangeConceptId.hashCode() : 0);
		return result;
	}
}
