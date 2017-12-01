package org.ihtsdo.termserver.scripting.domain;

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

	public List<String> getReplacedBy() {
	return replacedBy;
	}

	public void setReplacedBy(List<String> replacedBy) {
		this.replacedBy = replacedBy;
	}

}
