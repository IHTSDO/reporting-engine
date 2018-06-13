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
	private List<String> replacedBy = new ArrayList<>();
	
	@SerializedName("POSSIBLY_EQUIVALENT_TO")
	@Expose
	private List<String> possEquivTo = new ArrayList<>();
	
	@SerializedName("SAME_AS")
	@Expose
	private List<String> sameAs = new ArrayList<>();

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
	
	public List<String> getSameAs() {
		return sameAs;
	}

	public void setSameAs(List<String> sameAs) {
		this.sameAs = sameAs;
	}

	public static AssociationTargets possEquivTo(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = new ArrayList<>();
		targetList.add(c.getId());
		targets.setPossEquivTo(targetList);
		return targets;
	}
	
	public static AssociationTargets sameAs(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = new ArrayList<>();
		targetList.add(c.getId());
		targets.setSameAs(targetList);
		return targets;
	}
	
	public int size() {
		int total = 0;
		
		if (replacedBy != null) {
			total += replacedBy.size();
		}
		
		if (possEquivTo != null) {
			total += possEquivTo.size();
		}
		
		if (sameAs != null) {
			total += sameAs.size();
		}
		return total;
	}

	public void remove(String conceptId) {
		replacedBy.remove(conceptId);
		possEquivTo.remove(conceptId);
		sameAs.remove(conceptId);
	}

}
