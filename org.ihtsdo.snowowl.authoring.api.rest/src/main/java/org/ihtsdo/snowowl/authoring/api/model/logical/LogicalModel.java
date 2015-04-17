package org.ihtsdo.snowowl.authoring.api.model.logical;

import java.util.List;

public class LogicalModel {

	private IsARestriction isARestriction;
	private List<AttributeRestriction> attributeRestriction;

	public IsARestriction getIsARestriction() {
		return isARestriction;
	}

	public void setIsARestriction(IsARestriction isARestriction) {
		this.isARestriction = isARestriction;
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
				"isARestriction=" + isARestriction +
				", attributeRestriction=" + attributeRestriction +
				'}';
	}
}
