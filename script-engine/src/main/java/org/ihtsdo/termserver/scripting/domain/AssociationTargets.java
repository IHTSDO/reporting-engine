package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
	
	@SerializedName("WAS_A")
	@Expose
	private List<String> wasA = new ArrayList<>();
	
	@SerializedName("MOVED_TO")
	@Expose
	private List<String> movedTo = new ArrayList<>();

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
	
	public List<String> getWasA() {
		return wasA;
	}

	public void setWasA(List<String> wasA) {
		this.wasA = wasA;
	}
	
	public void setMovedTo(List<String> movedTo) {
		this.movedTo = movedTo;
	}

	public static AssociationTargets possEquivTo(Concept c) {
		return possEquivTo(Collections.singleton(c));
	}
	

	public static AssociationTargets possEquivTo(Set<Concept> replacements) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = replacements.stream()
				.map(c -> c.getId())
				.collect(Collectors.toList());
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
	
	public static AssociationTargets replacedBy(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = new ArrayList<>();
		targetList.add(c.getId());
		targets.setReplacedBy(targetList);
		return targets;
	}
	
	public static AssociationTargets wasA(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = new ArrayList<>();
		targetList.add(c.getId());
		targets.setWasA(targetList);
		return targets;
	}
	
	public static AssociationTargets movedTo(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		List<String> targetList = new ArrayList<>();
		targetList.add(c.getId());
		targets.setMovedTo(targetList);
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
		
		if (wasA != null) {
			total += wasA.size();
		}
		
		if (movedTo != null) {
			total += movedTo.size();
		}
		return total;
	}

	/**
	 * @return the number of associations successfully removed
	 */
	public int remove(String conceptId) {
		int beforeCount = size();
		replacedBy.remove(conceptId);
		possEquivTo.remove(conceptId);
		sameAs.remove(conceptId);
		wasA.remove(conceptId);
		movedTo.remove(conceptId);
		int afterCount = size();
		return beforeCount - afterCount;
	}


}
