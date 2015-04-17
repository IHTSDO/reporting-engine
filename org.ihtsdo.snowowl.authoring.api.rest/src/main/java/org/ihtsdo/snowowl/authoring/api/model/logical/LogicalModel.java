package org.ihtsdo.snowowl.authoring.api.model.logical;

import java.util.ArrayList;
import java.util.List;

public class LogicalModel {

	private List<IsARestriction> isARestrictions;
	private List<AttributeRestriction> attributeRestriction;

	public LogicalModel() {
		isARestrictions = new ArrayList<>();
	}

	public LogicalModel(IsARestriction isARestriction) {
		this();
		isARestrictions.add(isARestriction);
	}

	public LogicalModel addIsARestriction(IsARestriction isARestriction) {
		isARestrictions.add(isARestriction);
		return this;
	}

	public List<IsARestriction> getIsARestrictions() {
		return isARestrictions;
	}

	public void setIsARestrictions(List<IsARestriction> isARestrictions) {
		this.isARestrictions = isARestrictions;
	}

	public List<AttributeRestriction> getAttributeRestriction() {
		return attributeRestriction;
	}

	public void setAttributeRestriction(List<AttributeRestriction> attributeRestriction) {
		this.attributeRestriction = attributeRestriction;
	}

	@Override
	public String toString() {
		return "LogicalModel{" +
				"isARestrictions=" + isARestrictions +
				", attributeRestriction=" + attributeRestriction +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		LogicalModel that = (LogicalModel) o;

		if (isARestrictions != null ? !isARestrictions.equals(that.isARestrictions) : that.isARestrictions != null) return false;
		return !(attributeRestriction != null ? !attributeRestriction.equals(that.attributeRestriction) : that.attributeRestriction != null);

	}

	@Override
	public int hashCode() {
		int result = isARestrictions != null ? isARestrictions.hashCode() : 0;
		result = 31 * result + (attributeRestriction != null ? attributeRestriction.hashCode() : 0);
		return result;
	}
}
