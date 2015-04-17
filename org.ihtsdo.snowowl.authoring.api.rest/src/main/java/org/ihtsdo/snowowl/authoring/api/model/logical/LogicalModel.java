package org.ihtsdo.snowowl.authoring.api.model.logical;

public class LogicalModel {

	private IsARestriction isARestriction;

	public IsARestriction getIsARestriction() {
		return isARestriction;
	}

	public void setIsARestriction(IsARestriction isARestriction) {
		this.isARestriction = isARestriction;
	}

	@Override
	public String toString() {
		return "LogicalModel{" +
				"isARestriction=" + isARestriction +
				'}';
	}
}
