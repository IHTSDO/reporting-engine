
package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Generated;

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
	private boolean active;
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
	
	List<Concept> parents = new ArrayList<Concept>();
	List<Concept> children = new ArrayList<Concept>();
	
	public Concept(String conceptId) {
		this.conceptId = conceptId;
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
	
	public List<Relationship> getRelationships(CHARACTERISTIC_TYPE characteristicType) {
		List<Relationship> matches = new ArrayList<Relationship>();
		for (Relationship r : relationships) {
			if (r.getCharacteristicType().equals(characteristicType)) {
				matches.add(r);
			}
		}
		return matches;
	}
	
	public List<Relationship> getRelationships(CHARACTERISTIC_TYPE characteristicType, Concept type) {
		List<Relationship> potentialMatches = getRelationships(characteristicType);
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
		return this.conceptId.equals(rhs.conceptId);
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

}
