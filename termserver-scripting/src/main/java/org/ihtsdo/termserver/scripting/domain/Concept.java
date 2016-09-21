
package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Generated;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Concept implements RF2Constants {

	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private boolean active = true;
	@SerializedName("conceptId")
	@Expose
	private String conceptId;
	@SerializedName("fsn")
	@Expose
	private String fsn;
	@SerializedName("definitionStatus")
	@Expose
	private String definitionStatus;
	@SerializedName("preferredSynonym")
	@Expose
	private String preferredSynonym;
	@SerializedName("descriptions")
	@Expose
	private List<Description> descriptions = new ArrayList<Description>();
	@SerializedName("relationships")
	@Expose
	private List<Relationship> relationships = new ArrayList<Relationship>();
	@SerializedName("isLeafStated")
	@Expose
	private boolean isLeafStated;
	@SerializedName("isLeafInferred")
	@Expose
	private boolean isLeafInferred;
	
	private boolean isLoaded = false;
	private int originalFileLineNumber;
	private ConceptType conceptType = ConceptType.UNKNOWN;
	private List<String> assertionFailures = new ArrayList<String>();
	private String assignedAuthor;
	private String reviewer;
	
	public String getReviewer() {
		return reviewer;
	}

	public void setReviewer(String reviewer) {
		this.reviewer = reviewer;
	}

	List<Concept> parents = new ArrayList<Concept>();
	List<Concept> children = new ArrayList<Concept>();
	
	public Concept(String conceptId) {
		this.conceptId = conceptId;
	}
	
	public Concept(String conceptId, String fsn) {
		this.conceptId = conceptId;
		this.fsn = fsn;
	}

	public Concept(String conceptId, int originalFileLineNumber) {
		this.conceptId = conceptId;
		this.originalFileLineNumber = originalFileLineNumber;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean active) {
		this.active = active;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public String getFsn() {
		return fsn;
	}

	public void setFsn(String fsn) {
		this.fsn = fsn;
	}

	public String getDefinitionStatus() {
		return definitionStatus;
	}

	public void setDefinitionStatus(String definitionStatus) {
		this.definitionStatus = definitionStatus;
	}

	public String getPreferredSynonym() {
		return preferredSynonym;
	}

	public void setPreferredSynonym(String preferredSynonym) {
		this.preferredSynonym = preferredSynonym;
	}

	public List<Description> getDescriptions() {
		return descriptions;
	}

	public void setDescriptions(List<Description> descriptions) {
		this.descriptions = descriptions;
	}

	public List<Relationship> getRelationships() {
		return relationships;
	}
	
	public List<Relationship> getRelationships(CHARACTERISTIC_TYPE characteristicType, ACTIVE_STATE state, String effectiveTime) {
		List<Relationship> matches = new ArrayList<Relationship>();
		for (Relationship r : relationships) {
			if (effectiveTime == null || r.getEffectiveTime().equals(effectiveTime)) {
				if (characteristicType.equals(CHARACTERISTIC_TYPE.ALL) || r.getCharacteristicType().equals(characteristicType)) {
					if (state.equals(ACTIVE_STATE.BOTH) || (state.equals(ACTIVE_STATE.ACTIVE) && r.isActive()) ||
							(state.equals(ACTIVE_STATE.INACTIVE) && !r.isActive())) {
						matches.add(r);
					}
				}
			}
		}
		return matches;
	}
	
	public List<Relationship> getRelationships(CHARACTERISTIC_TYPE characteristicType, ACTIVE_STATE state) {
		return getRelationships(characteristicType, state, null);
	}
	
	public List<Relationship> getRelationships(CHARACTERISTIC_TYPE characteristicType, Concept type, ACTIVE_STATE state) {
		List<Relationship> potentialMatches = getRelationships(characteristicType, state);
		List<Relationship> matches = new ArrayList<Relationship>();
		for (Relationship r : potentialMatches) {
			if (r.getType().equals(type)) {
				matches.add(r);
			}
		}
		return matches;
	}

	public void setRelationships(List<Relationship> relationships) {
		this.relationships = relationships;
	}

	public boolean isIsLeafStated() {
		return isLeafStated;
	}

	public void setIsLeafStated(boolean isLeafStated) {
		this.isLeafStated = isLeafStated;
	}

	public boolean isIsLeafInferred() {
		return isLeafInferred;
	}

	public void setIsLeafInferred(boolean isLeafInferred) {
		this.isLeafInferred = isLeafInferred;
	}

	@Override
	public String toString() {
		return conceptId + " |" + this.fsn + "|";
	}

	@Override
	public int hashCode() {
		return conceptId.hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if ((other instanceof Concept) == false) {
			return false;
		}
		Concept rhs = ((Concept) other);
		return (this.conceptId.compareTo(rhs.conceptId) == 0);
	}

	public void addRelationship(Concept type, Concept target) {
		Relationship r = new Relationship();
		r.setActive(true);
		r.setGroupId(0);
		r.setCharacteristicType(CHARACTERISTIC_TYPE.STATED_RELATIONSHIP);
		r.setSourceId(this.getConceptId());
		r.setType(type);
		r.setTarget(target);
		r.setModifier(MODIFER.EXISTENTIAL);
		relationships.add(r);
	}

	public boolean isLoaded() {
		return isLoaded;
	}

	public void setLoaded(boolean isLoaded) {
		this.isLoaded = isLoaded;
	}

	public int getOriginalFileLineNumber() {
		return originalFileLineNumber;
	}
	
	public void addRelationship(Relationship r) {
		relationships.add(r);
	}
	
	public void addChild(Concept c) {
		children.add(c);
	}
	
	public void addParent(Concept p) {
		parents.add(p);
	}

	public ConceptType getConceptType() {
		return conceptType;
	}

	public void setConceptType(ConceptType conceptType) {
		this.conceptType = conceptType;
	}
	
	public void setConceptType(String conceptTypeStr) {
		if (conceptTypeStr.contains("Strength")) {
			this.setConceptType(ConceptType.PRODUCT_STRENGTH);
		} else if (conceptTypeStr.contains("Entity")) {
			this.setConceptType(ConceptType.MEDICINAL_ENTITY);
		} else if (conceptTypeStr.contains("Form")) {
			this.setConceptType(ConceptType.MEDICINAL_FORM);
		} else if (conceptTypeStr.contains("Grouper")) {
			this.setConceptType(ConceptType.GROUPER);
		} else {
			this.setConceptType(ConceptType.UNKNOWN);
		}
	}
	
	public Set<Concept> getDescendents(int depth) {
		Set<Concept> allDescendents = new HashSet<Concept>();
		this.populateAllDescendents(allDescendents, depth);
		return allDescendents;
	}
	
	private void populateAllDescendents(Set<Concept> descendents, int depth) {
		for (Concept thisChild : children) {
			descendents.add(thisChild);
			if (depth == NOT_SET || depth > 1) {
				int newDepth = depth == NOT_SET ? NOT_SET : depth - 1;
				thisChild.populateAllDescendents(descendents, newDepth);
			}
		}
	}

	public List<Description> getDescriptions(ACCEPTABILITY acceptability, DESCRIPTION_TYPE descriptionType, ACTIVE_STATE active) throws TermServerScriptException {
		List<Description> matchingDescriptions = new ArrayList<Description>();
		for (Description thisDescription : descriptions) {
			if (
					( active.equals(ACTIVE_STATE.BOTH) || thisDescription.isActive() == translateActive(active)) &&
					( thisDescription.getAcceptabilityMap() != null && thisDescription.getAcceptabilityMap().containsValue(acceptability)) &&
					( descriptionType == null || thisDescription.getType().equals(descriptionType) )
				) {
				//A preferred description can be preferred in either dialect, but if we're looking for an acceptable one, 
				//then it must not also be preferred in the other dialect
				if (acceptability.equals(ACCEPTABILITY.PREFERRED) || !thisDescription.getAcceptabilityMap().containsValue(ACCEPTABILITY.PREFERRED)) {
					matchingDescriptions.add(thisDescription);
				}
			} else {
				if (thisDescription.getAcceptabilityMap() == null && thisDescription.isActive()) {
					TermServerScript.warn (thisDescription + " is active with no acceptability map");
				}
			}
		}
		return matchingDescriptions;
	}

	private static boolean translateActive(ACTIVE_STATE active) throws TermServerScriptException {
		switch (active) {
			case ACTIVE : return true;
			case INACTIVE : return false;
			default: throw new TermServerScriptException("Unable to translate " + active + " into boolean state");
		}
	}

	public void addDescription(Description description) {
		descriptions.add(description);
		
	}

	public List<Concept> getParents() {
		return new ArrayList<Concept>(parents);
	}
	
	public List<String>getAssertionFailures() {
		return assertionFailures;
	}
	
	public void addAssertionFailure(String failure) {
		assertionFailures.add(failure);
	}

	public String getAssignedAuthor() {
		return assignedAuthor;
	}

	public void setAssignedAuthor(String assignedAuthor) {
		this.assignedAuthor = assignedAuthor;
	}

	public Description getFSNDescription() {
		for (Description d : descriptions) {
			if (d.isActive() && d.getType().equals(DESCRIPTION_TYPE.FSN)) {
				return d;
			}
		}
		return null;
	}
	
	public List<Description> getPrefTerms() {
		List<Description> prefTerms = new ArrayList<Description>();
		for (Description d : descriptions) {
			if (d.isActive() && d.getAcceptabilityMap().values().contains(ACCEPTABILITY.PREFERRED) && d.getType().equals(DESCRIPTION_TYPE.SYNONYM)) {
				prefTerms.add(d);
			}
		}
		return prefTerms;
	}

}
