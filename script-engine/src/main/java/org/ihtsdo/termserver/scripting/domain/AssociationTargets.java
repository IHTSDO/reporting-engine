package org.ihtsdo.termserver.scripting.domain;

import java.util.HashSet;
import java.util.Collections;
import java.util.Set;
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
	private Set<String> replacedBy = new HashSet<>();
	
	@SerializedName("POSSIBLY_EQUIVALENT_TO")
	@Expose
	private Set<String> possEquivTo = new HashSet<>();
	
	@SerializedName("SAME_AS")
	@Expose
	private Set<String> sameAs = new HashSet<>();
	
	@SerializedName("WAS_A")
	@Expose
	private Set<String> wasA = new HashSet<>();
	
	@SerializedName("MOVED_TO")
	@Expose
	private Set<String> movedTo = new HashSet<>();

	public Set<String> getReplacedBy() {
		return replacedBy;
	}

	public void setReplacedBy(Set<String> replacedBy) {
		this.replacedBy = replacedBy;
	}
	
	public Set<String> getPossEquivTo() {
		return possEquivTo;
	}

	public void setPossEquivTo(Set<String> possEquivTo) {
		this.possEquivTo = possEquivTo;
	}
	
	public Set<String> getSameAs() {
		return sameAs;
	}

	public void setSameAs(Set<String> sameAs) {
		this.sameAs = sameAs;
	}
	
	public Set<String> getWasA() {
		return wasA;
	}

	public void setWasA(Set<String> wasA) {
		this.wasA = wasA;
	}
	
	public void setMovedTo(Set<String> movedTo) {
		this.movedTo = movedTo;
	}

	public static AssociationTargets possEquivTo(Concept c) {
		return possEquivTo(Collections.singleton(c));
	}
	

	public static AssociationTargets possEquivTo(Set<Concept> replacements) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = replacements.stream()
				.map(c -> c.getId())
				.collect(Collectors.toSet());
		targets.setPossEquivTo(targetSet);
		return targets;
	}
	
	public static AssociationTargets sameAs(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = new HashSet<>();
		targetSet.add(c.getId());
		targets.setSameAs(targetSet);
		return targets;
	}
	
	public static AssociationTargets replacedBy(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = new HashSet<>();
		targetSet.add(c.getId());
		targets.setReplacedBy(targetSet);
		return targets;
	}
	
	public static AssociationTargets wasA(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = new HashSet<>();
		targetSet.add(c.getId());
		targets.setWasA(targetSet);
		return targets;
	}
	
	public static AssociationTargets movedTo(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = new HashSet<>();
		targetSet.add(c.getId());
		targets.setMovedTo(targetSet);
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
