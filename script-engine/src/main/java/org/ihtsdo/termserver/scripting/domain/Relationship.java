
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.utils.StringUtils;
import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

import java.util.*;

public class Relationship extends Component implements IRelationshipTemplate, ScriptConstants, Comparable<Relationship> {

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
	
	private boolean isDeleted = false;
	
	private AxiomEntry axiomEntry;  //Used when loading from RF2
	
	private Axiom axiom; //Used when loading from TS
	
	private String deletionEffectiveTime;
	
	@SerializedName("concreteValue")
	@Expose
	private ConcreteValue concreteValue;
	
	public boolean isConcrete() {
		return concreteValue != null;
	}

	public boolean isNotConcrete() {
		return !isConcrete();
	}
	
	private transient String axiomIdPart;
	
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

	public Relationship(Concept source, Concept type, String value, int groupId, ConcreteValue.ConcreteValueType cvType) {
		this.type = type;
		this.concreteValue = new ConcreteValue(cvType, value);
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

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		if (StringUtils.isEmpty(effectiveTime)) {
			this.effectiveTime = null;
		} else {
			this.effectiveTime = effectiveTime;
			this.setDirty();
		}
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
		return toString(includeRelIds, true);
	}
	
	public String toString(boolean includeRelIds, boolean includeInactiveIndicator) {
		//Is this just a reference to a relationship?  Just use ID if so
		if (type==null && target==null) {
			return relationshipId;
		}
		String charType = characteristicType.equals(CharacteristicType.STATED_RELATIONSHIP)?"S":"I";
		String activeIndicator = "";
		if (includeInactiveIndicator && !this.isActive()) {
			activeIndicator = "*";
		}
		String relId = "";
		if (includeRelIds && relationshipId != null) {
			relId = ":" + relationshipId;
		}
		
		if (isConcrete()) {
			return "[" + activeIndicator +  charType + groupId + relId + getAxiomIdPart() + "] " + type + " -> " + concreteValue.getValueWithPrefix();
		}
		return "[" + activeIndicator +  charType + groupId + relId + getAxiomIdPart() + "] " + type + " -> " + target;
	}

	private String getAxiomIdPart() {
		if (axiomIdPart == null) {
			axiomIdPart = getAxiomEntry() == null ? "" : ":" + getAxiomEntry().getId().substring(0,6);
			if (axiomIdPart.isEmpty() && getAxiom() != null && getAxiom().getId() != null) {
				axiomIdPart = ":" + getAxiom().getId().substring(0,6);
			}
		}
		return axiomIdPart;
	}

	@Override
	public int hashCode() {
		if (StringUtils.isEmpty(relationshipId)) {
			//Do not include the inactivation indicator, otherwise we might not
			//be able to recognise the object in a set if it changes after being created.
			//return toString(true, false).hashCode();
			try {
				if (isConcrete()) {
					return Objects.hash(characteristicType, groupId, getAxiomIdPart(), type.getId(), concreteValue);
				} else {
					if (target == null) {
						throw new IllegalArgumentException("Non-concrete relationship '" + this.toString() + "' encountered with no attribute target");
					}
					return Objects.hash(characteristicType, groupId, getAxiomIdPart(), type.getId(), target.getId());
				}
			} catch (NullPointerException e) {
				TermServerScript.debug("Null pointer here");
				throw e;
			}
		} else {
			return relationshipId.hashCode();
		}
	}
	
	public boolean equals(Object other, boolean ignoreAxiom) {
		return equals(other, ignoreAxiom, false);  //Include group by default
	}

	public boolean equals(Object other, boolean ignoreAxiom, boolean ignoreGroup) {
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
		
		//If we have two rels the same but coming from different axioms, they should be handled separately
		if (ignoreAxiom == false && (
				(this.getAxiomEntry() != null && rhs.getAxiomEntry() == null) ||
				(this.getAxiomEntry() == null && rhs.getAxiomEntry() != null) ||
				(this.getAxiomEntry() != null && 
				rhs.getAxiomEntry() != null && 
				!this.getAxiomEntry().getId().equals(rhs.getAxiomEntry().getId())))) {
			return false;
		}
		
		//Otherwise compare type / target (or Value) / group 
		if (ignoreGroup) {
			return this.equalsTypeAndTargetValue(rhs);
		} else {
			return this.equalsTypeAndTargetValue(rhs) && (this.groupId == rhs.groupId);
		}
	}
	
	@Override
	public boolean equals(Object other) {
		return equals(other, false);  //By default, include Axiom comparison
	}
	
	public Relationship clone(String newSCTID) {
		Relationship clone = new Relationship();
		clone.modifier = this.modifier;
		clone.groupId = this.groupId;
		clone.relationshipId = newSCTID; 
		clone.moduleId = this.moduleId;
		clone.target = this.target;
		clone.concreteValue = this.concreteValue;
		clone.active = this.active;
		clone.effectiveTime = null; //New relationship is unpublished
		clone.type = this.type;
		clone.sourceId = this.sourceId;
		clone.source = this.source;
		clone.characteristicType = this.characteristicType;
		clone.isDirty = true;
		clone.axiom = this.axiom;
		clone.axiomEntry = this.axiomEntry;
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
							moduleId, sourceId, 
							isConcrete()? concreteValue.getValueWithPrefix() : target.getConceptId(),
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
			case INFERRED_RELATIONSHIP : return ComponentType.INFERRED_RELATIONSHIP;
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
		if (released == null) {
			return !(effectiveTime == null || effectiveTime.isEmpty());
		}
		return released;
	}

	public void setReleased(Boolean released) {
		this.released = released;
	}

	public boolean equalsTypeAndTargetValue(IRelationshipTemplate rhs) {
		return this.type.equals(rhs.getType()) && equalsTargetOrValue(rhs);
	}
	
	public boolean equalsTargetOrValue(IRelationshipTemplate b) {
		if (isConcrete() && b.isConcrete()) {
			return getConcreteValue().toString().equals(b.getConcreteValue().toString());
		} else if (!isConcrete() && !b.isConcrete()) {
			return getTarget().equals(b.getTarget());
		} else {
			return false;
		}
	}

	
	public boolean fromAxiom() {
		return axiomEntry != null || axiom != null;
	}

	public AxiomEntry getAxiomEntry() {
		return axiomEntry;
	}

	public void setAxiomEntry(AxiomEntry axiom) {
		this.axiomEntry = axiom;
		this.axiomIdPart = null;
	}
	
	public Axiom getAxiom() {
		return axiom;
	}

	public void setAxiom(Axiom axiom) {
		this.axiom = axiom;
		this.axiomIdPart = null;
	}
	
	public ConcreteValue getConcreteValue() {
		return concreteValue;
	}

	public void setConcreteValue(ConcreteValue value) {
		this.concreteValue = value;
	}

	public boolean fromSameAxiom(Relationship r) {
		//If neither relationship is from an axiom, that's effectively
		//the pre-axiom condition ie yes the same axiom
		if (this.getAxiomEntry() == null && r.getAxiomEntry() == null) {
			return true;
		} else if (this.getAxiomEntry() != null & r.getAxiomEntry() != null) {
			return this.getAxiomEntry().equals(r.getAxiomEntry());
		}
		return false;
	}
	
	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) {
		Relationship otherR = (Relationship)other;
		List<String> differences = new ArrayList<>();
		String name = this.getClass().getSimpleName(); 
		commonFieldComparison(otherR, differences, ignoreEffectiveTime);
		
		if (!this.getSource().equals(otherR.getSource())) {
			differences.add("Source is different in " + name + ": " + this.getSource() + " vs " + otherR.getSource());
		}
		
		if (!this.getType().equals(otherR.getType())) {
			differences.add("Type is different in " + name + ": " + this.getType() + " vs " + otherR.getType());
		}
		
		if (!this.getTarget().equals(otherR.getTarget())) {
			differences.add("Target is different in " + name + ": " + this.getTarget() + " vs " + otherR.getTarget());
		}
		
		if (!this.getCharacteristicType().equals(otherR.getCharacteristicType())) {
			differences.add("CharacteristicType is different in " + name + ": " + this.getCharacteristicType() + " vs " + otherR.getCharacteristicType());
		}
		return differences;
	}

	@Override
	public void setId(String id) {
		setRelationshipId(id);
	}

}
