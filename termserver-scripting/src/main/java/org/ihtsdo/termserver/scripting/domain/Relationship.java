
package org.ihtsdo.termserver.scripting.domain;

import javax.annotation.Generated;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Relationship {

	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private boolean active;
	@SerializedName("relationshipId")
	@Expose
	private String relationshipId;
	@SerializedName("type")
	@Expose
	private Concept type;
	@SerializedName("target")
	@Expose
	private Concept target;
	@SerializedName("sourceId")
	@Expose
	private String sourceId;
	@SerializedName("groupId")
	@Expose
	private long groupId;
	@SerializedName("characteristicType")
	@Expose
	private String characteristicType;
	@SerializedName("modifier")
	@Expose
	private String modifier;

	/**
	 * No args constructor for use in serialization
	 * 
	 */
	public Relationship() {
	}

	/**
	 * 
	 * @param modifier
	 * @param groupId
	 * @param relationshipId
	 * @param moduleId
	 * @param target
	 * @param characteristicType
	 * @param active
	 * @param effectiveTime
	 * @param type
	 * @param sourceId
	 */
	public Relationship(String effectiveTime, String moduleId, boolean active, String relationshipId, Concept type, Concept target, String sourceId, long groupId, String characteristicType, String modifier) {
		this.effectiveTime = effectiveTime;
		this.moduleId = moduleId;
		this.active = active;
		this.relationshipId = relationshipId;
		this.type = type;
		this.target = target;
		this.sourceId = sourceId;
		this.groupId = groupId;
		this.characteristicType = characteristicType;
		this.modifier = modifier;
	}

	/**
	 * 
	 * @return
	 *	 The effectiveTime
	 */
	public String getEffectiveTime() {
		return effectiveTime;
	}

	/**
	 * 
	 * @param effectiveTime
	 *	 The effectiveTime
	 */
	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	/**
	 * 
	 * @return
	 *	 The moduleId
	 */
	public String getModuleId() {
		return moduleId;
	}

	/**
	 * 
	 * @param moduleId
	 *	 The moduleId
	 */
	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	/**
	 * 
	 * @return
	 *	 The active
	 */
	public boolean isActive() {
		return active;
	}

	/**
	 * 
	 * @param active
	 *	 The active
	 */
	public void setActive(boolean active) {
		this.active = active;
	}

	/**
	 * 
	 * @return
	 *	 The relationshipId
	 */
	public String getRelationshipId() {
		return relationshipId;
	}

	/**
	 * 
	 * @param relationshipId
	 *	 The relationshipId
	 */
	public void setRelationshipId(String relationshipId) {
		this.relationshipId = relationshipId;
	}

	/**
	 * 
	 * @return
	 *	 The type
	 */
	public Concept getType() {
		return type;
	}

	/**
	 * 
	 * @param type
	 *	 The type
	 */
	public void setType(Concept type) {
		this.type = type;
	}

	/**
	 * 
	 * @return
	 *	 The target
	 */
	public Concept getTarget() {
		return target;
	}

	/**
	 * 
	 * @param target
	 *	 The target
	 */
	public void setTarget(Concept target) {
		this.target = target;
	}

	/**
	 * 
	 * @return
	 *	 The sourceId
	 */
	public String getSourceId() {
		return sourceId;
	}

	/**
	 * 
	 * @param sourceId
	 *	 The sourceId
	 */
	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
	}

	/**
	 * 
	 * @return
	 *	 The groupId
	 */
	public long getGroupId() {
		return groupId;
	}

	/**
	 * 
	 * @param groupId
	 *	 The groupId
	 */
	public void setGroupId(long groupId) {
		this.groupId = groupId;
	}

	/**
	 * 
	 * @return
	 *	 The characteristicType
	 */
	public String getCharacteristicType() {
		return characteristicType;
	}

	/**
	 * 
	 * @param characteristicType
	 *	 The characteristicType
	 */
	public void setCharacteristicType(String characteristicType) {
		this.characteristicType = characteristicType;
	}

	/**
	 * 
	 * @return
	 *	 The modifier
	 */
	public String getModifier() {
		return modifier;
	}

	/**
	 * 
	 * @param modifier
	 *	 The modifier
	 */
	public void setModifier(String modifier) {
		this.modifier = modifier;
	}

	@Override
	public String toString() {
		return "[S: " + sourceId + ", T: " + type.getConceptId() + ", D: " + target.getConceptId() + "]";
	}

	@Override
	public int hashCode() {
		return  toString().hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if ((other instanceof Relationship) == false) {
			return false;
		}
		Relationship rhs = ((Relationship) other);
		return this.hashCode() == rhs.hashCode();
	}

}
