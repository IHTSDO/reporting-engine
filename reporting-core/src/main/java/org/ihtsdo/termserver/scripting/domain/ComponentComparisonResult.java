package org.ihtsdo.termserver.scripting.domain;

import java.util.List;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

public class ComponentComparisonResult {
	
	enum Result { NO_MATCH, DIFFERS, MATCHES }
	
	Component left;
	Component right;
	Result comparisonResult;
	
	public ComponentComparisonResult (Result comparisonResult, Component left, Component right) {
		this.comparisonResult = comparisonResult;
		this.left = left;
		this.right = right;
	}
	
	public ComponentComparisonResult (Component left, Component right) {
		this.left = left;
		this.right = right;
		
		if (left == null || right == null) {
			comparisonResult = Result.NO_MATCH;
		}
	}

	public Component getLeft() {
		return left;
	}
	public void setLeft(Component left) {
		this.left = left;
	}
	public Component getRight() {
		return right;
	}
	public void setRight(Component right) {
		this.right = right;
	}
	
	public ComponentComparisonResult matches() {
		this.comparisonResult = Result.MATCHES;
		return this;
	}

	public ComponentComparisonResult differs() {
		this.comparisonResult = Result.DIFFERS;
		return this;
	}

	public static boolean hasChanges(List<ComponentComparisonResult> componentComparisonResults) {
		for (ComponentComparisonResult comparison : componentComparisonResults) {
			if (!comparison.comparisonResult.equals(Result.MATCHES)) {
				return true;
			}
		}
		return false;
	}

	public String getComponentTypeStr() {
		if (left != null) {
			return left.getComponentType().toString();
		}
		return right.getComponentType().toString();
	}

	public boolean isMatch() {
		return comparisonResult.equals(Result.MATCHES);
	}
	
	@Override
	public String toString() {
		return getComponentTypeStr() + " " + left + " " + comparisonResult.name() + " " + right;
	}

}
