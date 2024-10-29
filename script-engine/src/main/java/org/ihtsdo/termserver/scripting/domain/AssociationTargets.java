package org.ihtsdo.termserver.scripting.domain;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.termserver.scripting.GraphLoader;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

/**
 * Note that this class is the representation in JSON whereas 
 * HistoricalAssociation is how it's done in RF2
 */
public class AssociationTargets implements Serializable {

	@SerializedName("REPLACED_BY")
	@Expose
	private Set<String> replacedBy = new HashSet<>();
	
	@SerializedName("POSSIBLY_EQUIVALENT_TO")
	@Expose
	private Set<String> possEquivTo = new HashSet<>();
	
	@SerializedName("PARTIALLY_EQUIVALENT_TO")
	@Expose
	private Set<String> partEquivTo = new HashSet<>();
	
	@SerializedName("SAME_AS")
	@Expose
	private Set<String> sameAs = new HashSet<>();
	
	@SerializedName("WAS_A")
	@Expose
	private Set<String> wasA = new HashSet<>();
	
	@SerializedName("MOVED_TO")
	@Expose
	private Set<String> movedTo = new HashSet<>();
	
	@SerializedName("ALTERNATIVE")
	@Expose
	private Set<String> alternative = new HashSet<>();
	
	@SerializedName("REFERS_TO")
	@Expose
	private Set<String> refersTo = new HashSet<>();

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
	
	public Set<String> getPartEquivTo() {
		return partEquivTo;
	}

	public void setPartEquivTo(Set<String> partEquivTo) {
		this.partEquivTo = partEquivTo;
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
	
	public Set<String> getMovedTo() {
		return movedTo;
	}
	
	public void setMovedTo(Set<String> movedTo) {
		this.movedTo = movedTo;
	}
	
	public Set<String> getAlternatives() {
		return alternative;
	}

	public void setAlternatives(Set<String> alternative) {
		this.alternative = alternative;
	}

	public static AssociationTargets possEquivTo(Concept c) {
		return possEquivTo(Collections.singleton(c));
	}
	
	public static AssociationTargets partEquivTo(Concept c) {
		return partEquivTo(Collections.singleton(c));
	}

	public static AssociationTargets possEquivTo(Set<Concept> replacements) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = replacements.stream()
				.map(c -> c.getId())
				.collect(Collectors.toSet());
		targets.setPossEquivTo(targetSet);
		return targets;
	}
	
	public static AssociationTargets partEquivTo(Set<Concept> replacements) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = replacements.stream()
				.map(c -> c.getId())
				.collect(Collectors.toSet());
		targets.setPartEquivTo(targetSet);
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
	
	public static AssociationTargets alternative(Concept c) {
		AssociationTargets targets = new AssociationTargets();
		Set<String> targetSet = new HashSet<>();
		targetSet.add(c.getId());
		targets.setAlternatives(targetSet);
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
		
		if (partEquivTo != null) {
			total += partEquivTo.size();
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
		
		if (alternative != null) {
			total += alternative.size();
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
		partEquivTo.remove(conceptId);
		sameAs.remove(conceptId);
		wasA.remove(conceptId);
		movedTo.remove(conceptId);
		alternative.remove(conceptId);
		int afterCount = size();
		return beforeCount - afterCount;
	}

	public void clear() {
		replacedBy.clear();
		possEquivTo.clear();
		partEquivTo.clear();
		sameAs.clear();
		wasA.clear();
		movedTo.clear();
		alternative.clear();
	}

	public String toString(GraphLoader gl) throws TermServerScriptException {
		String str = "";
		str += toString("WasA: ", wasA, gl, true);
		str += toString("PossEquivTo: ", possEquivTo, gl, (str.isEmpty()));
		str += toString("PartEquivTo: ", partEquivTo, gl, (str.isEmpty()));
		str += toString("SameAs: ", sameAs, gl, (str.isEmpty()));
		str += toString("ReplacedBy: ", replacedBy, gl, (str.isEmpty()));
		str += toString("Moved To: ", movedTo, gl, (str.isEmpty()));
		str += toString("Alternative: ", alternative, gl, (str.isEmpty()));
		str += toString("Refers To: ", refersTo, gl, (str.isEmpty()));
		return str;
	}

	private String toString(String assocType, Set<String> associations, GraphLoader gl, boolean firstRow) throws TermServerScriptException {
		StringBuilder str = new StringBuilder();
		boolean isFirst = true;
		for (String association : associations) {
			if (!isFirst) {
				str.append("\n    ");
			} else {
				isFirst = false;
				str.append((firstRow?"":"\n") + assocType);
			}
			//If the concept is not loaded, we'll assume it's active for not until we can load it propertly
			Concept assocConcept = gl.getConcept(association);
			if (assocConcept.getActive() == null) {
				str.append(association);
			} else {
				str.append(assocConcept.toStringWithIndicator());
			}
		}
		return str.toString();
	}

	public boolean isWasACombo() {
		//Return true if we have a WAS A association in combination with another association type
		return (!wasA.isEmpty() && (
				!sameAs.isEmpty() ||
				!replacedBy.isEmpty() ||
				!possEquivTo.isEmpty() ||
				!partEquivTo.isEmpty()));
	}

	public void clearWasA() {
		wasA.clear();
	}

	public Set<Concept> getWasAConcepts(GraphLoader gl) throws TermServerScriptException {
		Set<Concept> wasAConcepts = new HashSet<>();
		for (String wasAId : wasA) {
			wasAConcepts.add(gl.getConcept(wasAId));
		}
		return wasAConcepts;
	}

	public void addPossEquivTo(Set<String> equivs) {
		possEquivTo.addAll(equivs);
	}
	
	public void addPartEquivTo(Set<String> equivs) {
		partEquivTo.addAll(equivs);
	}

	public Set<String> getRefersTo() {
		return refersTo;
	}

	public void setRefersTo(Set<String> refersTo) {
		this.refersTo = refersTo;
	}

}
