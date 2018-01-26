package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Note that this class is the representation in JSON whereas 
 * HistoricalAssociation is how it's done in RF2
 */
public class AssociationTargets {

	@SerializedName("REPLACED_BY")
	@Expose
	private List<String> replacedBy = null;
	
	@SerializedName("MAY_BE_A")
	@Expose
	private List<String> mayBeA = null;

	public List<String> getReplacedBy() {
	return replacedBy;
	}

	public void setReplacedBy(List<String> replacedBy) {
		this.replacedBy = replacedBy;
	}
	
	public List<String> getMayBeA() {
	return mayBeA;
	}

	public void setMayBeA(List<String> mayBeA) {
		this.mayBeA = mayBeA;
	}

	public static AssociationTargets mayBeA(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = new ArrayList<>();
		targetList.add(c.getId());
		targets.setMayBeA(targetList);
		return targets;
	}

}
