
package org.ihtsdo.termserver.scripting.domain;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.ComponentStore;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class Relationship extends Component implements IRelationship, ScriptConstants, Comparable<Relationship> {

	private static final Logger LOGGER = LoggerFactory.getLogger(Relationship.class);
	
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

	public Relationship(String id) {
		setId(id);
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
	
	public String toShortPrettyString() {
		return  type.toStringPref() + " -> " + target.toStringPref();
	}

	public String toShortString() {
		return "[S: " + sourceId + ", T: " + type.getConceptId() + ", D: " + target.getConceptId() + "]";
	}
	
	public String toLongString() {
		String charType = characteristicType.equals(CharacteristicType.STATED_RELATIONSHIP)?"S":"I";
		String activeIndicator = this.isActive()?"":"*";
		return "[" + activeIndicator +  charType + groupId + "] " + source + ": "+ type + " -> " + (isConcrete()?concreteValue:target);
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
		if (relationshipId != null && relationshipId.length() > 5) {
				return relationshipId.hashCode();
		}
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
				int hash = Objects.hash(characteristicType, groupId, getAxiomIdPart(), type.getId(), target.getId());
				return hash;
			}
		} catch (NullPointerException e) {
			LOGGER.debug("Null pointer here");
			throw e;
		}
	}
	
	public boolean equals(Object other, boolean ignoreAxiom) {
		return equals(other, ignoreAxiom, false);  //Include group by default
	}

	public boolean equals(Object other, boolean ignoreAxiom, boolean ignoreGroup) {
		if ((other instanceof Relationship) == false) {
			return false;
		}
		/*if (this.toString().equals("[S1:2a8120] 116686009 |Has specimen (attribute)| -> 119361006 |Plasma specimen (specimen)|")) {
			LOGGER.debug("Debug here");
		}*/
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
				(this.getAxiomEntry() != null && rhs.getAxiomEntry() != null &&
				!this.getAxiomEntry().getId().equals(rhs.getAxiomEntry().getId())))) {
			return false;
		}
		
		//If loading from the TS we have an Axiom rather than AxiomEntry
		//If we have two rels the same but coming from different axioms, they should be handled separately
		if (ignoreAxiom == false && (
				(this.getAxiom() != null && rhs.getAxiom() == null) ||
				(this.getAxiom() == null && rhs.getAxiom() != null) ||
				(this.getAxiom() != null && rhs.getAxiom() != null && !this.getAxiom().getId().equals(rhs.getAxiom().getId())))) {
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
	
	public Relationship cloneWithoutAxiom(String newSCTID) {
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
		clone.setIssues(this.getIssueList());
		return clone;
	}

	public Relationship clone(String newSCTID) {
		Relationship clone = cloneWithoutAxiom(newSCTID);
		clone.axiom = this.axiom;
		clone.axiomEntry = this.axiomEntry;
		clone.setReleased(this.isReleased());
		return clone;
	}
	
	public Relationship clone() {
		return clone(null);
	}

	@Override
	public Relationship instantiate(Concept concept, int groupId) {
		return this;
	}

	public Relationship cloneWithIds() {
		return clone(this.getId());
	}

	@Override
	//Sort on source id, type id, target id, group id
	public int compareTo(Relationship other) {
		//We might not have a source id yet if we're creating this concept, compare other fields if so.
 		if (this.sourceId != null && other.getSourceId() != null && !this.sourceId.equals(other.getSourceId())) {
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
			case ADDITIONAL_RELATIONSHIP: return ComponentType.ADDITIONAL_RELATIONSHIP;
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
	
	public boolean equalsTypeAndTargetValue(IRelationship rhs) {
		return this.type.equals(rhs.getType()) && equalsTargetOrValue(rhs);
	}
	
	public boolean equalsTargetOrValue(IRelationship b) {
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
		
		if ((this.isConcrete() && !otherR.isConcrete()) ||
				(!this.isConcrete() && otherR.isConcrete())) {
			differences.add("Target is different in " + name + ": " + (this.isConcrete()?"is concrete":"is not concrete") + " vs " + (otherR.isConcrete()?"is concrete":"is not concrete"));
		} else if (isConcrete()) {
			if (!this.getConcreteValue().equals(otherR.getConcreteValue())) {
				differences.add("Target is different in " + name + ": " + this.getConcreteValue() + " vs " + otherR.getConcreteValue());
			}
		} else if (!this.getTarget().equals(otherR.getTarget())) {
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

	public boolean hasActiveState(ActiveState activeState) {
		return (activeState.equals(ActiveState.BOTH) ||
			(active && activeState.equals(ActiveState.ACTIVE)) ||
			(!active && activeState.equals(ActiveState.INACTIVE)));
	}

	@Override
	public String[] getMutableFields() {
		String[] mutableFields = super.getMutableFields();
		int idx = super.getMutableFieldCount();
		mutableFields[idx] = this.sourceId;
		mutableFields[++idx] = this.type.getId();
		mutableFields[++idx] = isConcrete() ? this.getConcreteValue().getValueWithPrefix() : this.target.getId();
		mutableFields[++idx] = this.characteristicType.toString();
		return mutableFields;
	}
	
	public String toStringWithId() {
		return getId() + ": " + toString();
	}

	@Override
	public boolean matchesMutableFields(Component other) {
		Relationship otherRel = (Relationship) other;
		//We're not going to worry about groupId for now
		//Also new relationships won't yet know what their source concepts are
		return this.getCharacteristicType().equals(otherRel.getCharacteristicType())
				&& this.getType().equals(otherRel.getType())
				&& this.getTarget().equals(otherRel.getType());
	}

	@Override
	public List<Component> getReferencedComponents(ComponentStore cs) {
		if (this.isConcrete()) {
		return List.of(
				this.getSource(),
				this.getType());
		} else {
			return List.of(
				this.getSource(),
				this.getType(),
				this.getTarget());
		}
	}

}
