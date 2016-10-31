
package org.ihtsdo.termserver.scripting.domain;

import javax.annotation.Generated;

import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

@Generated("org.jsonschema2pojo")
public class Relationship implements RF2Constants, Comparable<Relationship> {

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
	private CharacteristicType characteristicType;
	@SerializedName("modifier")
	@Expose
	private Modifier modifier;
	
	private Concept source;
	
	public static final String[] rf2Header = new String[] {"id","effectiveTime","active","moduleId","sourceId","destinationId",
															"relationshipGroup","typeId","characteristicTypeId","modifierId"};

	/**
	 * No args constructor for use in serialization
	 * 
	 */
	public Relationship() {
	}

	/**
	 * @param groupId
	 * @param target
	 * @param active
	 * @param type
	 * @param sourceId
	 */
	public Relationship(Concept source, Concept type, Concept target, long groupId) {
		this.type = type;
		this.target = target;
		this.source = source;
		this.sourceId = source.getConceptId();
		this.groupId = groupId;
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
	public CharacteristicType getCharacteristicType() {
		return characteristicType;
	}

	/**
	 * 
	 * @param characteristicType
	 *	 The characteristicType
	 */
	public void setCharacteristicType(CharacteristicType characteristicType) {
		this.characteristicType = characteristicType;
	}

	/**
	 * 
	 * @return
	 *	 The modifier
	 */
	public Modifier getModifier() {
		return modifier;
	}

	/**
	 * 
	 * @param modifier
	 *	 The modifier
	 */
	public void setModifier(Modifier modifier) {
		this.modifier = modifier;
	}

	public String toShortString() {
		return "[S: " + sourceId + ", T: " + type.getConceptId() + ", D: " + target.getConceptId() + "]";
	}
	
	@Override
	public String toString() {
		return type + " - " + target;
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
	
	@Override
	public Relationship clone() {
		Relationship clone = new Relationship();
		clone.modifier = this.modifier;
		clone.groupId = this.groupId;
		clone.relationshipId = null; //New relationship needs own 
		clone.moduleId = this.moduleId;
		clone.target = this.target;
		clone.active = this.active;
		clone.effectiveTime = null; //New relationship is unpublished
		clone.type = this.type;
		clone.sourceId = this.sourceId;
		clone.source = this.source;
		return clone;
	}

	@Override
	//Sort on source id, type id, target id, group id
	public int compareTo(Relationship other) {
		if (!this.sourceId.equals(other.getSourceId())) {
			return sourceId.compareTo(other.getSourceId());
		} else {
			if (!this.getType().getConceptId().equals(other.getType().getConceptId())) {
				return this.getType().getConceptId().compareTo(other.getType().getConceptId());
			} else {
				if (!this.getTarget().getConceptId().equals(other.getTarget().getConceptId())) {
					return this.getTarget().getConceptId().compareTo(other.getTarget().getConceptId());
				} else {
					if (this.getGroupId() != other.getGroupId()) {
						return ((Long)this.getGroupId()).compareTo(other.getGroupId());
					} else {
						return 0;  //Equal in all four values
					}
				}
			}
		}
	}

	//"id","effectiveTime","active",
	//"moduleId","sourceId","destinationId",
	//"relationshipGroup","typeId",
	//"characteristicTypeId","modifierId"};
	public String[] toRF2() throws TermServerScriptException {
		return new String[] {relationshipId, effectiveTime, (active?"1":"0"), 
							moduleId, sourceId, target.getConceptId(),
							Long.toString(groupId), type.getConceptId(), 
							SnomedUtils.translateCharacteristicType(characteristicType), 
							SnomedUtils.translateModifier(modifier)};
	}

}
