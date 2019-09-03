
package org.ihtsdo.termserver.scripting.domain;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Relationship extends Component implements IRelationshipTemplate, RF2Constants, Comparable<Relationship> {

	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private Boolean active = null;
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
	private int groupId;
	@SerializedName("characteristicType")
	@Expose
	private CharacteristicType characteristicType = CharacteristicType.STATED_RELATIONSHIP;
	@SerializedName("modifier")
	@Expose
	private Modifier modifier;
	@SerializedName("released")
	@Expose
	private Boolean released = null;
	
	private Concept source;
	
	private boolean dirty = false;
	
	private boolean isDeleted = false;
	private String deletionEffectiveTime;
	
	public static final String[] rf2Header = new String[] {"id","effectiveTime","active","moduleId","sourceId","destinationId",
															"relationshipGroup","typeId","characteristicTypeId","modifierId"};

	public Relationship() {
	}

	public Relationship(Concept source, Concept type, Concept target, int groupId) {
		this.type = type;
		this.target = target;
		this.source = source;
		if (source != null) {
			this.sourceId = source.getConceptId();
		}
		this.groupId = groupId;
		
		//Default values
		this.active = true;
		this.characteristicType = CharacteristicType.STATED_RELATIONSHIP;
		this.modifier = Modifier.EXISTENTIAL;
		this.moduleId = SCTID_CORE_MODULE;
	}

	public Relationship(Concept type, Concept value) {
		this.type = type;
		this.target = value;
		
		//Default values
		this.groupId = UNGROUPED;
		this.active = true;
		this.characteristicType = CharacteristicType.STATED_RELATIONSHIP;
		this.modifier = Modifier.EXISTENTIAL;
		this.moduleId = SCTID_CORE_MODULE;
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
		if (this.moduleId != null && !this.moduleId.equals(moduleId)) {
			setDirty();
			this.effectiveTime = null;
		}
		this.moduleId = moduleId;
	}

	public boolean isActive() {
		return active;
	}

	public void setActive(boolean newActiveState) {
		if (this.active != null && !this.active == newActiveState) {
			this.effectiveTime = null;
			setDirty();
		}
		this.active = newActiveState;
	}

	public String getRelationshipId() {
		return relationshipId;
	}

	public void setRelationshipId(String relationshipId) {
		this.relationshipId = relationshipId;
	}

	public Concept getType() {
		return type;
	}

	public void setType(Concept type) {
		this.type = type;
	}

	public Concept getTarget() {
		return target;
	}

	public void setTarget(Concept target) {
		this.target = target;
	}

	public String getSourceId() {
		return sourceId;
	}

	public void setSourceId(String sourceId) {
		this.sourceId = sourceId;
	}

	public int getGroupId() {
		return groupId;
	}

	public void setGroupId(int groupId) {
		this.groupId = groupId;
	}

	public CharacteristicType getCharacteristicType() {
		return characteristicType;
	}

	public void setCharacteristicType(CharacteristicType characteristicType) {
		this.characteristicType = characteristicType;
	}

	public Modifier getModifier() {
		return modifier;
	}

	public void setModifier(Modifier modifier) {
		this.modifier = modifier;
	}

	public String toShortString() {
		return "[S: " + sourceId + ", T: " + type.getConceptId() + ", D: " + target.getConceptId() + "]";
	}
	
	public String toLongString() {
		String charType = characteristicType.equals(CharacteristicType.STATED_RELATIONSHIP)?"S":"I";
		String activeIndicator = this.isActive()?"":"*";
		return "[" + activeIndicator +  charType + groupId + "] " + source + ": "+ type + " -> " + target;
	}
	
	@Override
	public String toString() {
		return toString(false);
	}
	
	public String toString(boolean includeRelIds) {
		//Is this just a reference to a relationship?  Just use ID if so
		if (type==null && target==null) {
			return relationshipId;
		}
		String charType = characteristicType.equals(CharacteristicType.STATED_RELATIONSHIP)?"S":"I";
		String activeIndicator = this.isActive()?"":"*";
		String relId = includeRelIds ? ":" + relationshipId : "";
		return "[" + activeIndicator +  charType + groupId + relId + "] " + type + " -> " + target;
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
		
		//Must be of the same characteristic type
		if (!this.getCharacteristicType().equals(rhs.characteristicType)) {
			return false;
		}
		
		//If both sides have an SCTID, then compare that
		if (this.getRelationshipId() != null && rhs.getRelationshipId() != null) {
			return this.getRelationshipId().equals(rhs.getRelationshipId());
		}
		//Otherwise compare type / target / group 
		return (this.type.equals(rhs.type) && this.target.equals(rhs.target) && this.groupId == rhs.groupId);
	}
	
	public Relationship clone(String newSCTID) {
		Relationship clone = new Relationship();
		clone.modifier = this.modifier;
		clone.groupId = this.groupId;
		clone.relationshipId = newSCTID; 
		clone.moduleId = this.moduleId;
		clone.target = this.target;
		clone.active = this.active;
		clone.effectiveTime = null; //New relationship is unpublished
		clone.type = this.type;
		clone.sourceId = this.sourceId;
		clone.source = this.source;
		clone.characteristicType = this.characteristicType;
		clone.dirty = true;
		return clone;
	}
	
	public Relationship clone() {
		return clone(null);
	}
	
	public Relationship cloneWithIds() {
		return clone(this.getId());
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
						return ((Integer)this.getGroupId()).compareTo(other.getGroupId());
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
	
	public String[] toRF2Deletion() throws TermServerScriptException {
		return new String[] {relationshipId, effectiveTime, deletionEffectiveTime,
							(active?"1":"0"), 
							"1",
							moduleId, sourceId, target.getConceptId(),
							Long.toString(groupId), type.getConceptId(), 
							SnomedUtils.translateCharacteristicType(characteristicType), 
							SnomedUtils.translateModifier(modifier)};
	}


	public boolean isDirty() {
		return dirty;
	}
	
	public void setDirty() {
		dirty = true;
	}
	
	public void setClean() {
		dirty = false;
	}

	public Concept getSource() {
		return source;
	}

	public void setSource(Concept source) {
		this.source = source;
		if (source != null) {
			this.sourceId = source.getConceptId();
		}
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void delete(String deletionEffectiveTime) {
		this.isDeleted = true;
		this.deletionEffectiveTime = deletionEffectiveTime;
	}

	@Override
	public String getId() {
		return relationshipId;
	}

	@Override
	public ComponentType getComponentType() {
		switch (characteristicType) {
			case STATED_RELATIONSHIP : return ComponentType.STATED_RELATIONSHIP;
			case INFERRED_RELATIONSHIP : return ComponentType.RELATIONSHIP;
		default:
			throw new NotImplementedException();
		}
	}

	@Override
	public String getReportedName() {
		return this.toString();
	}

	@Override
	public String getReportedType() {
		return getComponentType().toString();
	}
	
	
	public Boolean isReleased() {
		return released;
	}

	public void setReleased(Boolean released) {
		this.released = released;
	}

	public boolean equalsTypeValue(Relationship rhs) {
		return this.type.equals(rhs.type) && this.target.equals(rhs.target);
	}

}
