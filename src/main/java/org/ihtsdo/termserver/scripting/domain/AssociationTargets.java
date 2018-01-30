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
	
	@SerializedName("POSSIBLY_EQUIVALENT_TO")
	@Expose
	private List<String> possEquivTo = null;

	public List<String> getReplacedBy() {
	return replacedBy;
	}

	public void setReplacedBy(List<String> replacedBy) {
		this.replacedBy = replacedBy;
	}
	
	public List<String> getPossEquivTo() {
	return possEquivTo;
	}

	public void setPossEquivTo(List<String> possEquivTo) {
		this.possEquivTo = possEquivTo;
	}

	public static AssociationTargets possEquivTo(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = new ArrayList<>();
		targetList.add(c.getId());
		targets.setPossEquivTo(targetList);
		return targets;
	}

}
