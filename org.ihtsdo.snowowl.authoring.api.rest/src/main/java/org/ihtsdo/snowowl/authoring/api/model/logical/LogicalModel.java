package org.ihtsdo.snowowl.authoring.api.model.logical;

import java.util.ArrayList;
import java.util.List;

public class LogicalModel {

	private String name;
	private List<IsARestriction> isARestrictions;
	private List<AttributeRestriction> attributeRestrictions;

	public LogicalModel() {
		isARestrictions = new ArrayList<>();
	}

	public LogicalModel(String name, IsARestriction isARestriction) {
		this();
		this.name = name;
		isARestrictions.add(isARestriction);
	}

	public LogicalModel addIsARestriction(IsARestriction isARestriction) {
		isARestrictions.add(isARestriction);
		return this;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public List<IsARestriction> getIsARestrictions() {
		return isARestrictions;
	}

	public void setIsARestrictions(List<IsARestriction> isARestrictions) {
		this.isARestrictions = isARestrictions;
	}

	public List<AttributeRestriction> getAttributeRestrictions() {
		return attributeRestrictions;
	}

	public void setAttributeRestrictions(List<AttributeRestriction> attributeRestrictions) {
		this.attributeRestrictions = attributeRestrictions;
	}

	@Override
	public String toString() {
		return "LogicalModel{" +
				"name='" + name + '\'' +
				", isARestrictions=" + isARestrictions +
				", attributeRestrictions=" + attributeRestrictions +
				'}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		LogicalModel that = (LogicalModel) o;

		if (isARestrictions != null ? !isARestrictions.equals(that.isARestrictions) : that.isARestrictions != null) return false;
		return !(attributeRestrictions != null ? !attributeRestrictions.equals(that.attributeRestrictions) : that.attributeRestrictions != null);

	}

	@Override
	public int hashCode() {
		int result = isARestrictions != null ? isARestrictions.hashCode() : 0;
		result = 31 * result + (attributeRestrictions != null ? attributeRestrictions.hashCode() : 0);
		return result;
	}
}
