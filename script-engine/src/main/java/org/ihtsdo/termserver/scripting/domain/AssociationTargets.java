package org.ihtsdo.termserver.scripting.domain;

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
	
	@SerializedName("ALTERNATIVE")
	@Expose
	private Set<String> alternative = new HashSet<>();

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
	
	public static AssociationTargets Alternative(Concept c) {
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
		sameAs.clear();
		wasA.clear();
		movedTo.clear();
		alternative.clear();
	}

	public String toString(GraphLoader gl) throws TermServerScriptException {
		String str = "";
		str += toString("WasA: ", wasA, gl, true);
		str += toString("PossEquivTo: ", possEquivTo, gl, (str.isEmpty()));
		str += toString("SameAs: ", sameAs, gl, (str.isEmpty()));
		str += toString("ReplacedBy: ", replacedBy, gl, (str.isEmpty()));
		str += toString("Moved To: ", movedTo, gl, (str.isEmpty()));
		str += toString("Alternative: ", alternative, gl, (str.isEmpty()));
		return str;
	}

	private String toString(String assocType, Set<String> associations, GraphLoader gl, boolean firstRow) throws TermServerScriptException {
		String str = "";
		boolean isFirst = true;
		for (String association : associations) {
			if (!isFirst) {
				str += "\n    ";
			} else {
				isFirst = false;
				str = (firstRow?"":"\n") + assocType;
			}
			str += gl.getConcept(association).toStringWithIndicator();
		}
		return str;
	}

	public boolean isWasACombo() {
		//Return true if we have a WAS A association in combination with another association type
		return (wasA.size() > 0 && (
				sameAs.size() > 0 ||
				replacedBy.size() > 0 ||
				possEquivTo.size() > 0));
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

}
