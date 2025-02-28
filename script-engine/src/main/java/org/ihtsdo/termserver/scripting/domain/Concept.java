package org.ihtsdo.termserver.scripting.domain;

import java.util.*;
import java.util.stream.Collectors;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.apache.commons.lang.StringUtils;
import org.ihtsdo.otf.exception.TermServerScriptException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.RefsetMember;
import org.ihtsdo.otf.utils.SnomedUtilsBase;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.otf.owltoolkit.domain.ObjectPropertyAxiomRepresentation;
import org.snomed.otf.script.Script;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Concept extends Expressable implements ScriptConstants, Comparable<Concept>  {

	private static final Logger LOGGER = LoggerFactory.getLogger(Concept.class);

	@SerializedName(value="conceptId", alternate="id")
	@Expose
	private String conceptId;
	
	@SerializedName("fsn")
	@Expose
	private Object fsn;
	
	@SerializedName("definitionStatus")
	@Expose
	private DefinitionStatus definitionStatus;
	
	@SerializedName("preferredSynonym")
	@Expose
	private String preferredSynonym;
	
	@SerializedName("descriptions")
	@Expose
	private List<Description> descriptions = new ArrayList<>();
	
	@SerializedName("relationships")
	@Expose
	private Set<Relationship> relationships = new HashSet<>();
	
	@SerializedName("isLeafStated")
	@Expose
	private boolean isLeafStated;
	
	@SerializedName("isLeafInferred")
	@Expose
	private boolean isLeafInferred;
	
	@SerializedName("inactivationIndicator")
	@Expose
	private InactivationIndicator inactivationIndicator;
	
	@SerializedName("associationTargets")
	@Expose
	private AssociationTargets associationTargets;
	
	@SerializedName("classAxioms")
	@Expose
	private List<Axiom> classAxioms;
	
	@SerializedName("additionalAxioms")
	@Expose
	private List<Axiom> additionalAxioms;
	
	@SerializedName("gciAxioms")
	@Expose
	private Set<Axiom> gciAxioms;
	
	@SerializedName("alternateIdentifiers")
	@Expose
	private Set<AlternateIdentifier> alternateIdentifiers;

	private boolean isLoaded = false;
	private int originalFileLineNumber;
	private ConceptType conceptType;
	private List<String> assertionFailures = new ArrayList<>();
	private String assignedAuthor;
	private String reviewer;
	boolean isModified = false; //indicates if has been modified in current processing run
	private String deletionEffectiveTime;
	private boolean isDeleted = false;
	private int depth = NOT_SET;
	private Long statedAttribSum = null;  //Allows cached quick comparison of relationships
	
	//Note that these values are used when loading from RF2 where multiple entries can exist.
	//When interacting with the TS, only one inactivation indicator is used (see above).
	List<InactivationIndicatorEntry> inactivationIndicatorEntries;
	List<AssociationEntry> associationEntries;
	List<AxiomEntry> axiomEntries;
	ObjectPropertyAxiomRepresentation objectPropertyAxiom;

	Set<Concept> statedParents = new HashSet<>();
	Set<Concept> inferredParents = new HashSet<>();
	Set<Concept> statedChildren;  //Lazy create Set to reduce memory footprint
	Set<Concept> inferredChildren;
	
	List<RelationshipGroup> statedRelationshipGroups;
	List<RelationshipGroup> inferredRelationshipGroups;
	private Set<RefsetMember> otherRefsetMembers;

	public void reset() {
		assertionFailures = new ArrayList<>();
		statedRelationshipGroups = null;
		inferredRelationshipGroups = null;
		descriptions = new ArrayList<>();
		relationships = new HashSet<>();
		statedParents.clear();
		inferredParents.clear();
		if (statedChildren != null) {
			statedChildren.clear();
		}
		if (inferredChildren != null) {
			inferredChildren.clear();
		}
		if (otherRefsetMembers != null) {
			otherRefsetMembers.clear();
		}
	}
	
	public String getReviewer() {
		return reviewer;
	}

	public void setReviewer(String reviewer) {
		this.reviewer = reviewer;
	}
	
	public Concept(String conceptId) {
		setId(conceptId);
		
		//default values
		this.definitionStatus = DefinitionStatus.PRIMITIVE;
	}
	
	public Concept(String conceptId, String fsn) {
		this(conceptId);
		this.fsn = fsn;
	}
	
	public Concept(Long conceptId) {
		this(conceptId.toString());
	}
	
	public Concept(Concept c) {
		this(c.getConceptId());
		this.fsn = c.getFsn();
	}

	public Concept(String conceptId, int originalFileLineNumber) {
		this(conceptId);
		this.originalFileLineNumber = originalFileLineNumber;
	}
	
	public static Concept withDefaults (String conceptId) {
		Concept c = new Concept(conceptId);
		c.setModuleId(SCTID_CORE_MODULE);
		c.setActive(true);
		c.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		return c;
	}
	
	public static Concept withDefaultsFromSctIdFsn (String sctIdFSN) throws TermServerScriptException {
		String[] parts = SnomedUtils.deconstructSCTIDFsn(sctIdFSN);
		Concept c = new Concept(parts[0]);
		c.setModuleId(SCTID_CORE_MODULE);
		c.setActive(true);
		c.setDefinitionStatus(DefinitionStatus.PRIMITIVE);
		String fsnStr = parts[1];
		String[] fsnParts = SnomedUtilsBase.deconstructFSN(fsnStr);
		Description fsn = Description.withDefaults(fsnStr, DescriptionType.FSN, Acceptability.PREFERRED);
		Description pt = Description.withDefaults(fsnParts[0], DescriptionType.SYNONYM, Acceptability.PREFERRED);
		c.addDescription(fsn);
		c.addDescription(pt);
		return c;
	}
	
	public boolean hasEffectiveTime() {
		return effectiveTime != null && !effectiveTime.isEmpty();
	}

	@Override
	public void setActive(boolean newActiveState) {
		super.setActive(newActiveState);
		if (!newActiveState) {
			//If the concept has been made active, then set DefnStatus
			setDefinitionStatus(DefinitionStatus.PRIMITIVE);
			
			//And inactivate all relationships
			for (Relationship r : relationships) {
				r.setActive(false);
			}
		}
	}

	public String getConceptId() {
		if (conceptId == null && id != null) {
			conceptId = id;
		}
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		setId(conceptId);
	}

	public String getFsn() {
		if (fsn != null) {
			if (fsn instanceof String) {
				return fsn.toString();
			} else if (fsn instanceof Map){
				Map<?,?> fsnMap = (Map<?,?>)fsn;
				if (!fsnMap.containsKey("term")) {
					LOGGER.error("Check unexpected map here: {}", fsn);
					return fsnMap.toString();
				} else {
					return ((Map<?,?>)fsn).get("term").toString();
				}
			}else {
				return fsn.toString();
			}
		} else {
			Description d = getFSNDescription();
			if (d != null) {
				return d.getTerm();
			}
		}
		return null;
	}

	public String getFsnSafely() {
		if (fsn == null) {
			return "";
		}
		return getFsn();
	}

	public void setFsn(String fsn) {
		this.fsn = fsn;
		this.semTag = null;
	}

	public DefinitionStatus getDefinitionStatus() {
		return definitionStatus;
	}

	public void setDefinitionStatus(DefinitionStatus definitionStatus) {
		this.definitionStatus = definitionStatus;
	}

	/**
	 * This doesn't make any sense without saying which dialect to work in.  It must
	 * come from the json representation which is requested with a dialect setting
	 * @return
	 * @throws TermServerScriptException 
	 */
	public String getPreferredSynonym() {
		try {
			if (preferredSynonym == null) {
				Description pt = getPreferredSynonym(US_ENG_LANG_REFSET);
				return pt == null ? null : pt.getTerm();
			}
			return preferredSynonym;
		} catch (Exception e) {
			LOGGER.warn("{}: {}", this, e.getMessage());
			return "";
		}
	}
	
	public Description getPreferredSynonym(String refsetId) throws TermServerScriptException {
		List<Description> pts = getDescriptions(refsetId, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
		return pts.isEmpty() ? null : pts.iterator().next();
	}
	
	public List<Description> getPreferredSynonyms() throws TermServerScriptException {
		return getDescriptions(null, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
	}
	
	public Description getPreferredSynonymSafely(String refsetId) {
		String debug = "";
		try {
			List<Description> pts = getDescriptions(refsetId, Acceptability.PREFERRED, DescriptionType.SYNONYM, ActiveState.ACTIVE);
			return pts.isEmpty() ? null : pts.iterator().next();
		} catch (Exception e) {
			LOGGER.error("Exception encountered",e);
			debug = e.getMessage();
		}
		return new Description("Exception recovering PT: " + debug);
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

	public Set<Relationship> getRelationships() {
		return relationships;
	}
	
	public Set<Relationship> getRelationships(CharacteristicType characteristicType, ActiveState activeState, String effectiveTime) {
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r : relationships) {
			if (effectiveTime == null || r.getEffectiveTime().equals(effectiveTime)) {
				if (characteristicType.equals(CharacteristicType.ALL) || r.getCharacteristicType().equals(characteristicType)) {
					if (r.hasActiveState(activeState)) {
						matches.add(r);
					}
				}
			}
		}
		return matches;
	}
	
	public Set<Relationship> getRelationships(CharacteristicType characteristicType, ActiveState activeState) {
		return getRelationships(characteristicType, activeState, null);
	}
	
	//Gets relationships that match the triple + group + charType
	public Set<Relationship> getRelationships(Relationship r) {
		return getRelationships(r.getCharacteristicType(), r.getType(), r.getTarget(), r.getGroupId(), ActiveState.ACTIVE);
	}
	
	//Gets relationships that match the triple + group
	public Set<Relationship> getRelationships(CharacteristicType charType, Relationship r) {
		return getRelationships(charType, r.getType(), r.getTarget(), r.getGroupId(), ActiveState.ACTIVE);
	}
	
	public Set<Relationship> getRelationships(CharacteristicType characteristicType, RelationshipTemplate t) {
		return getRelationships(characteristicType, t.getType(), t.getTarget(), ActiveState.ACTIVE);
	}
	
	public Relationship getRelationship(RelationshipTemplate r, int groupId) {
		Set<Relationship> rels = getRelationships(r.getCharacteristicType(), r.getType(), r.getTarget(), groupId, ActiveState.ACTIVE);
		if (rels == null || rels.isEmpty()) {
			return null;
		} else if (groupId != NOT_SET && rels.size() > 1) {
			throw new IllegalArgumentException(this + " group " + groupId + " contained > 1 " + r);
		}
		return rels.iterator().next();
	}
	
	public Set<Relationship> getRelationships(Relationship r, ActiveState activeState) {
		return getRelationships(r.getCharacteristicType(), r.getType(), r.getTarget(), r.getGroupId(), activeState);
	}
	
	public Set<Relationship> getRelationships(RelationshipTemplate r, ActiveState activeState) {
		return getRelationships(r.getCharacteristicType(), r.getType(), r.getTarget(), activeState);
	}
	
	public Set<Relationship> getRelationships(CharacteristicType characteristicType, Concept type, ActiveState activeState) {
		Set<Relationship> potentialMatches = getRelationships(characteristicType, activeState);
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r : potentialMatches) {
			if (type == null || r.getType().equals(type)) {
				matches.add(r);
			}
		}
		return matches;
	}

	public Set<Relationship> getRelationships(CharacteristicType charType, Concept[] targets, ActiveState state) {
		Set<Relationship> matchingRels = new HashSet<>();
		for (Concept target : targets) {
			matchingRels.addAll(getRelationships(charType, null, target, state));
		}
		return matchingRels;
	}
	
	public Set<Relationship> getRelationships(CharacteristicType characteristicType, Concept type, Concept target, ActiveState activeState) {
		Set<Relationship> potentialMatches = getRelationships(characteristicType, type, activeState);
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r : potentialMatches) {
			if (target == null || r.getTarget().equals(target)) {
				matches.add(r);
			}
		}
		return matches;
	}

	public Set<Relationship> getRelationships(CharacteristicType characteristicType, Concept type, ConcreteValue concreteValue, ActiveState activeState) {
		Set<Relationship> potentialMatches = getRelationships(characteristicType, type, activeState);
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r : potentialMatches) {
			if (concreteValue == null || r.getConcreteValue().equals(concreteValue)) {
				matches.add(r);
			}
		}
		return matches;
	}

	public Set<Relationship> getRelationships(CharacteristicType characteristicType, Concept type, Concept target, int groupId, ActiveState activeState) {
		Set<Relationship> potentialMatches = getRelationships(characteristicType, type, target, activeState);
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r : potentialMatches) {
			if (groupId == NOT_SET || r.getGroupId() == groupId) {
				matches.add(r);
			}
		}
		return matches;
	}

	public Set<Relationship> getRelationships(CharacteristicType characteristicType, Concept type, ConcreteValue concreteValue, int groupId, ActiveState activeState) {
		Set<Relationship> potentialMatches = getRelationships(characteristicType, type, concreteValue, activeState);
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r : potentialMatches) {
			if (groupId == NOT_SET || r.getGroupId() == groupId) {
				matches.add(r);
			}
		}
		return matches;
	}
	
	public Set<Relationship> getRelationships(CharacteristicType characteristicType, Concept type, int groupId) {
		Set<Relationship> potentialMatches = getRelationships(characteristicType, type, ActiveState.ACTIVE);
		Set<Relationship> matches = new HashSet<>();
		for (Relationship r : potentialMatches) {
			if (groupId == NOT_SET || r.getGroupId() == groupId) {
				matches.add(r);
			}
		}
		return matches;
	}

	public Relationship getRelationship(String id) {
		for (Relationship r : relationships) {
			if (r.getRelationshipId() != null && r.getRelationshipId().equals(id)) {
				return r;
			}
		}
		return null;
	}

	/**
	 * Return all relationships that belong to the specified axiom
	 */
	public Set<Relationship> getRelationships(AxiomEntry a) {
		return relationships.stream()
				.filter(r -> r.getAxiomEntry() != null && r.getAxiomEntry().equals(a))
				.collect(Collectors.toSet());
	}

	public void setRelationships(Set<Relationship> relationships) {
		this.relationships = relationships;
	}
	
	public void removeRelationship(Relationship r) {
		removeRelationship(r, false);
	}
	
	public void removeRelationship(Relationship r, boolean force) {
		if (!StringUtils.isEmpty(r.getEffectiveTime()) && !force) {
			throw new IllegalArgumentException("Attempt to deleted published relationship " + r);
		}

		int sizeBefore = relationships.size();
		Set<Relationship> newRelationshipSet = relationships.stream()
				.filter(rel -> !rel.equals(r))
				.collect(Collectors.toSet());
		this.relationships = newRelationshipSet;
		if (this.relationships.size() == sizeBefore) {
			LOGGER.debug("Failed to remove relationship: {}", r);
		}
		
		removeParentIfRequired(r);
		recalculateGroups();
	}
	
	public void inactivateRelationship(Relationship r) {
		r.setEffectiveTime(null);
		r.setActive(false);
		removeParentIfRequired(r);
		recalculateGroups();
	}
	
	private void removeParentIfRequired(Relationship r) {
		CharacteristicType charType = r.getCharacteristicType();
		//Do I need to adjust parent/child?  Might still exist in another axiom
		if (r.getType().equals(IS_A) && 
				getRelationships(charType, IS_A, r.getTarget(), ActiveState.ACTIVE).size() == 0) {
			Concept parent = r.getTarget();
			parent.removeChild(charType, this);
			removeParent(charType, parent);
		}
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
	
	public boolean isLeaf (CharacteristicType c) {
		if (c.equals(CharacteristicType.STATED_RELATIONSHIP)) {
			return getStatedChildren().isEmpty();
		} else {
			return getInferredChildren().isEmpty();
		}
	}

	private Set<Concept> getStatedChildren() {
		if (statedChildren == null) {
			statedChildren = new HashSet<>();
		}
		return statedChildren;
	}

	private Set<Concept> getInferredChildren() {
		if (inferredChildren == null) {
			inferredChildren = new HashSet<>();
		}
		return inferredChildren;
	}

	@Override
	public String toString() {
		return conceptId + " |" + getFsn() + "|";
	}
	
	public String toStringWithIndicator() {
		return (isActiveSafely()?"":"*") + conceptId + " |" + getFsn() + "|";
	}


	public String toStringPref() {
		return conceptId + " |" + getPreferredSynonym() + "|";
	}

	@Override
	public int hashCode() {
		if (conceptId != null)
			return conceptId.hashCode();
		
		//Where a conceptId does not exist, hash the FSN
		if (fsn !=null && !getFsn().trim().isEmpty()) {
			return fsn.hashCode();
		}
		
		//Where we don't have either, hash the expression
		return toExpression(CharacteristicType.STATED_RELATIONSHIP).hashCode();
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof Concept)) {
			return false;
		}
		Concept rhs = ((Concept) other);
		//If both concepts have Ids, compare those
		if (this.conceptId != null && rhs.conceptId != null) {
			return (this.conceptId.compareTo(rhs.conceptId) == 0);
		}
		
		//Otherwise, compare FSNs or expressions
		if (this.fsn != null && !this.getFsn().isEmpty() && rhs.fsn != null && !rhs.getFsn().isEmpty()) {
			return (this.fsn.equals(rhs.fsn));
		}
		String thisExpression = this.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		String rhsExpression = rhs.toExpression(CharacteristicType.STATED_RELATIONSHIP);
		return thisExpression.equals(rhsExpression);
	}
	
	public void addRelationship(Concept type, Concept target) {
		addRelationship(type, target, 0);
	}
	
	public void addRelationship(RelationshipTemplate rt, int groupId) {
		addRelationship(rt.getType(), rt.getTarget(), groupId);
	}

	public void addRelationship(Concept type, Concept target, int groupId) {
		Relationship r = new Relationship();
		r.setActive(true);
		r.setGroupId(groupId);
		r.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		r.setSourceId(this.getConceptId());
		r.setType(type);
		r.setTarget(target);
		r.setModifier(Modifier.EXISTENTIAL);
		relationships.add(r);
		recalculateGroups();
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
	
	public int addOrReactivateRelationship(Relationship r) {
		//Do we already have an inactive version of this relationship to reactivate?
		//Only relevant if this relationship is new
		if (r.getId() == null) {
			//Before considering inactive rels, check we don't already have this relationship
			Set<Relationship> activeRels = getRelationships(r);
			if (activeRels.size() > 0) {
				LOGGER.warn("Ignoring relationship add in {}, triple + group already present and active {}", this, activeRels.iterator().next());
				return NO_CHANGES_MADE;
			}
			for (Relationship match : getRelationships(r, ActiveState.INACTIVE)) {
				LOGGER.warn("Reactivating {} in {}", match, this);
				match.setActive(true);
				return CHANGE_MADE;
			}
		}
		addRelationship(r);
		return CHANGE_MADE;
	}
	
	public void addRelationship(Relationship r) {
		//Interesting.  If a relationship has a new active state, then "add"
		//may not replace it, because it thinks its the same object.  Remove first.
		relationships.remove(r);
		relationships.add(r);
		recalculateGroups();
	}
	
	public void addChild(CharacteristicType charType, Concept c) {
		getChildren(charType).add(c);
	}
	
	public void removeChild(CharacteristicType charType, Concept c) {
		if (getChildren(charType) != null) {
			getChildren(charType).remove(c);
		}
	}
	
	public void addParent(CharacteristicType charType, Concept p) {
		getParents(charType).add(p);
	}
	
	public void removeParent(CharacteristicType charType, Concept p) {
		getParents(charType).remove(p);
	}

	public ConceptType getConceptType() {
		return conceptType;
	}

	public void setConceptType(ConceptType conceptType) {
		this.conceptType = conceptType;
	}
	
	public Set<Concept> getDescendants(int depth) throws TermServerScriptException {
		return getDescendants(depth, CharacteristicType.INFERRED_RELATIONSHIP);
	}
	
	public Set<Concept> getDescendants(int depth, CharacteristicType characteristicType) throws TermServerScriptException {
		return getDescendants(depth, characteristicType, false);
	}
	
	public Set<Concept> getDescendants(int depth, CharacteristicType characteristicType, boolean includeSelf) throws TermServerScriptException {
		Set<Concept> allDescendants = new HashSet<Concept>();
		this.populateAllDescendants(allDescendants, depth, characteristicType);
		if (includeSelf) {
			allDescendants.add(this);
		}
		return allDescendants;
	}
	
	private void populateAllDescendants(Set<Concept> descendants, int depth, CharacteristicType characteristicType) throws TermServerScriptException {
		for (Concept thisChild : getChildren(characteristicType)) {
			descendants.add(thisChild);
			if (depth == NOT_SET || depth > 1) {
				int newDepth = depth == NOT_SET ? NOT_SET : depth - 1;
				thisChild.populateAllDescendants(descendants, newDepth, characteristicType);
			}
		}
	}
	
	public Set<Concept> getAncestors(int depth) throws TermServerScriptException {
		return getAncestors(depth, CharacteristicType.INFERRED_RELATIONSHIP, false);
	}
	
	public Set<Concept> getAncestors(int depth, CharacteristicType characteristicType, boolean includeSelf) throws TermServerScriptException {
		Set<Concept> allAncestors = new HashSet<>();
		this.populateAllAncestors(allAncestors, depth, characteristicType);
		if (includeSelf) {
			allAncestors.add(this);
		}
		return allAncestors;
	}
	
	private void populateAllAncestors(Set<Concept> ancestors, int depth, CharacteristicType characteristicType) throws TermServerScriptException {
		for (Concept thisParent : getParents(characteristicType)) {
			ancestors.add(thisParent);
			if (depth == NOT_SET || depth > 1) {
				int newDepth = depth == NOT_SET ? NOT_SET : depth - 1;
				thisParent.populateAllAncestors(ancestors, newDepth, characteristicType);
			}
	}
	}
	
	public Set<Concept> getChildren(CharacteristicType characteristicType) {
		switch (characteristicType) {
			case STATED_RELATIONSHIP : return getStatedChildren();
			case INFERRED_RELATIONSHIP : return getInferredChildren();
			default:
		}
		return null;
	}

	//A preferred description can be preferred in either dialect, but if we're looking for an acceptable one, 
	//then it must not also be preferred in the other dialect
	public List<Description> getDescriptions(Acceptability acceptability, DescriptionType descriptionType, ActiveState activeState) throws TermServerScriptException {
		List<Description> matchingDescriptions = new ArrayList<>();
		for (Description thisDescription : getDescriptions(activeState)) {
			//Is this description of the right type?
			if (descriptionType == null || thisDescription.getType().equals(descriptionType)) {
				//Are we working with JSON representation and acceptability map, or an RF2 representation
				//with language refset entries?
				if (thisDescription.getAcceptabilityMap() != null) {
					if (acceptability == null || acceptability.equals(Acceptability.BOTH) || thisDescription.getAcceptabilityMap().containsValue(acceptability)) {
						if (acceptability == null || acceptability.equals(Acceptability.BOTH)) {
							matchingDescriptions.add(thisDescription);
						} else if (acceptability.equals(Acceptability.PREFERRED) || !thisDescription.getAcceptabilityMap().containsValue(Acceptability.PREFERRED)) {
							matchingDescriptions.add(thisDescription);
						}
					}
				} else if (!thisDescription.getLangRefsetEntries().isEmpty()) {
					boolean match = false;
					boolean preferredFound = false;
					for (LangRefsetEntry l : thisDescription.getLangRefsetEntries(ActiveState.ACTIVE)) {
						if (acceptability == null || acceptability.equals(Acceptability.BOTH) || 
							acceptability.equals(SnomedUtils.translateAcceptability(l.getAcceptabilityId()))) {
							match = true;
						} 
						
						if (l.getAcceptabilityId().equals(SCTID_PREFERRED_TERM)) {
							preferredFound = true;
						}
					}
					//Did we find one, and if it's acceptable, did we also not find another preferred
					if (match) {
						if (acceptability != null && acceptability.equals(Acceptability.ACCEPTABLE)) {
							if (!preferredFound) {
								matchingDescriptions.add(thisDescription);
							}
						} else {
							matchingDescriptions.add(thisDescription);
						}
					}
				} else {
					LOGGER.warn("{} is active with no Acceptability map or Language Refset entries (since {}).", thisDescription, thisDescription.getEffectiveTime());
				}
			}
		}
		return matchingDescriptions;
	}
	
	public List<Description> getDescriptions(String langRefsetId, Acceptability targetAcceptability, DescriptionType descriptionType, ActiveState active) throws TermServerScriptException {
		//Get the matching terms, and then pick the ones that have the appropriate Acceptability for the specified Refset
		List<Description> matchingDescriptions = new ArrayList<>();
		for (Description d : getDescriptions(targetAcceptability, descriptionType, active)) {
			//We might have this acceptability either from a Map (JSON) or Langrefset entry (RF2)
			if (SnomedUtils.hasAcceptabilityInDialect(d, langRefsetId, targetAcceptability)) {
				//Need to check the Acceptability because the first function might match on some other language
				matchingDescriptions.add(d);
			}
		}
		return matchingDescriptions;
	}
	
	public List<Description> getDescriptions(ActiveState a) {
		if (descriptions == null) {
			throw new IllegalStateException("Concept " + this + " has no descriptions");
		}
		return descriptions.stream()
				.filter(d -> SnomedUtils.descriptionHasActiveState(d, a))
				.collect(Collectors.toList());
	}
	
	public List<Description> getDescriptions(String lang, ActiveState a) {
		return descriptions.stream()
				.filter(d -> d.getLang().equals(lang))
				.filter(d -> SnomedUtils.descriptionHasActiveState(d, a))
				.collect(Collectors.toList());
	}
	
	public List<Description> getDescriptions(ActiveState a, List<DescriptionType> types) {
		List<Description> results = new ArrayList<>();
		for (Description d : descriptions) {
			if (SnomedUtils.descriptionHasActiveState(d, a) &&
					types.contains(d.getType())) {
				results.add(d);
			}
		}
		return results;
	}
	
	public Description getDescription(String term, ActiveState a) {
		Description bestMatch = null;
		for (Description d : getDescriptions(a)) {
			if (d.getTerm().equals(term)) {
				//In the case of both active states, return the active one by preference
				if (a.equals(ActiveState.BOTH)) {
					if (d.isActiveSafely() || bestMatch == null) {
						bestMatch = d;
					} 
				} else {
					return d;
				}
			}
		}
		return bestMatch;
	}

	public Description getDescription(String descriptionId) {
		for (Description d : descriptions) {
			if (d.getDescriptionId().equals(descriptionId)) {
				return d;
			}
		}
		return null;
	}
	
	public void addDescription(Description d) {
		addDescription(d, false); //Don't allow duplicates by default
	}
	
	public void addDescription(Description d, boolean allowDuplicateTerms) {
		//Do we already have a description with this SCTID?
		//But don't check if description id is null otherwise we can only have one
		if (d.getId() != null && !allowDuplicateTerms && descriptions.contains(d)) {
			descriptions.remove(d);
		}
		
		descriptions.add(d);
		if (d.getType().equals(DescriptionType.FSN)) {
			if (d.isActiveSafely() && (getFsn() == null || d.getLang().equals("en"))) {
				this.setFsn(d.getTerm());
			} else if (!d.isActiveSafely() && (getFsn() == null || d.getLang().equals("en"))) {
				List<Description> fsns = getDescriptions(ActiveState.ACTIVE, Collections.singletonList(DescriptionType.FSN));
				if (fsns.size() == 1) {
					this.setFsn(fsns.get(0).getTerm());
				} else {
					//Set en if we have one otherwise any
					for (Description fsnDesc : fsns) {
						if (fsnDesc.getLang().equals("en")) {
							this.setFsn(fsnDesc.getTerm());
						}
					}
				}
			}
		}
	}
	
	public void removeDescription (Description d) {
		descriptions.remove(d);
	}

	public Set<Concept> getParents(CharacteristicType characteristicType) {
		//Concepts loaded from TS would not get these arrays populated.  Populate.
		Set<Concept> parents = null;
		switch (characteristicType) {
			case STATED_RELATIONSHIP : parents = statedParents;
										break;
			case INFERRED_RELATIONSHIP: parents = inferredParents;
										break;
			default: throw new IllegalArgumentException("Cannot have " + characteristicType + " parents.");
		}
		
		if (parents == null || parents.isEmpty()) {
			if (parents == null) {
				parents = new HashSet<>();
				if (characteristicType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
					statedParents = parents;
				} else {
					inferredParents = parents;
				}
			}
			populateParents(parents, characteristicType);
		}
		return parents;
	}
	
	public Set<Concept> getParents(CharacteristicType characteristicType, DefinitionStatus defStatus) {
		Set<Concept> parents = new HashSet<>(getParents(characteristicType));
		return parents.stream()
				.filter(p -> p.getDefinitionStatus().equals(defStatus))
				.collect(Collectors.toSet());
	}
	
	private void populateParents(Set<Concept> parents, CharacteristicType characteristicType) {
		parents.clear();
		for (Relationship parentRel : getRelationships(characteristicType, IS_A, ActiveState.ACTIVE)) {
			parents.add(parentRel.getTarget());
		}
	}
	
	public boolean hasParent(Concept parent) {
		return hasParent(parent, CharacteristicType.STATED_RELATIONSHIP);
	}
	
	public boolean hasParent(Concept parent, CharacteristicType charType) {
		return getParents(charType).contains(parent);
	}
	
	public Concept getFirstParent() {
		Iterator<Concept> i = getParents(CharacteristicType.STATED_RELATIONSHIP).iterator();
		if (i.hasNext()) {
			return getParents(CharacteristicType.STATED_RELATIONSHIP).iterator().next();
		}
		return null;
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
		return getFSNDescription("en");
	}

	public Description getFSNDescription(String lang) {
		if (descriptions == null) {
			String err = "Concept " + conceptId + " |" + getFsn() + "| has no descriptions";
			throw new IllegalArgumentException(err);
		}
		for (Description d : descriptions) {
			if (d.isActiveSafely()
					&& d.getType().equals(DescriptionType.FSN)
					&& d.getLang().equals(lang)) {
				return d;
			}
		}
		return null;
	}
	
	public List<Description> getSynonyms(Acceptability acceptability) {
		List<Description> synonyms = new ArrayList<>();
		for (Description d : descriptions) {
			if (d.isActiveSafely() && d.getAcceptabilityMap().values().contains(acceptability) && d.getType().equals(DescriptionType.SYNONYM)) {
				synonyms.add(d);
			}
		}
		return synonyms;
	}

	public boolean hasTerm(String term, String langCode) {
		boolean hasTerm = false;
		for (Description d : descriptions) {
			if (d.getTerm().equals(term) && d.getLang().equals(langCode)) {
				hasTerm = true;
				break;
			}
		}
		return hasTerm;
	}
	
	public Description findTerm(String term) {
		return findTerm (term, null, false, true);
	}
	
	public Description findTerm(String term , String lang) {
		return findTerm (term, lang, false, true);
	}

	public Description findTerm(String term , String lang, boolean caseInsensitive, boolean includeInactive) {
		//First look for a match in the active terms, then try inactive
		for (Description d : getDescriptions(ActiveState.ACTIVE)) {
			if ((lang == null || lang.equals(d.getLang()))) {
				if (caseInsensitive) {
					term = term.toLowerCase();
					String desc = d.getTerm().toLowerCase();
					if (term.equals(desc)) {
						return d;
					}
				} else if (d.getTerm().equals(term)) {
						return d;
				}
			}
		}
		
		if (includeInactive) {
			for (Description d : getDescriptions(ActiveState.INACTIVE)) {
				if ((lang == null || lang.equals(d.getLang()))) {
					if (caseInsensitive) {
						term = term.toLowerCase();
						String desc = d.getTerm().toLowerCase();
						if (term.equals(desc)) {
							return d;
						}
					} else if (d.getTerm().equals(term)) {
							return d;
					}
				}
			}
		}
		return null;
	}

	public void setModified() {
		isModified = true;
	}
	
	public boolean isModified() {
		return isModified;
	}
	
	public int getDepth() {
		return depth;
	}
	
	public void setDepth(int depth) {
		// We'll maintain the shortest possible path, so don't allow depth to increase
		if (this.depth == NOT_SET || depth < this.depth) {
			this.depth = depth;
		}
	}

	@Override
	public int compareTo(Concept c) {
		return getConceptId().compareTo(c.getConceptId());
	}

	public List<InactivationIndicatorEntry> getInactivationIndicatorEntries() {
		if (inactivationIndicatorEntries == null) {
			inactivationIndicatorEntries = new ArrayList<>();
		}
		return inactivationIndicatorEntries;
	}
	
	public List<InactivationIndicatorEntry> getInactivationIndicatorEntries(ActiveState activeState) {
		if (activeState.equals(ActiveState.BOTH)) {
			return getInactivationIndicatorEntries();
		} else {
			boolean isActive = activeState.equals(ActiveState.ACTIVE);
			List<InactivationIndicatorEntry> selectedInactivationIndicatortEntries = new ArrayList<>();
			for (InactivationIndicatorEntry i : getInactivationIndicatorEntries()) {
				if (i.isActiveSafely() == isActive) {
					selectedInactivationIndicatortEntries.add(i);
				}
			}
			return selectedInactivationIndicatortEntries;
		}
	}
	
	public List<AssociationEntry> getAssociationEntries() {
		if (associationEntries == null) {
			associationEntries = new ArrayList<>();
		}
		return associationEntries;
	}
	
	public List<AssociationEntry> getAssociationEntries(ActiveState activeState) {
		return getAssociationEntries(activeState, false); //All associations by default
	}
	
	public List<AssociationEntry> getAssociationEntries(ActiveState activeState, String refsetId, boolean historicalAssociationsOnly) {
		return getAssociationEntries().stream()
				.filter(a -> a.hasActiveState(activeState))
				.filter(a -> refsetId == null || a.getRefsetId().contentEquals(refsetId))
				.filter(a -> !historicalAssociationsOnly || Script.isHistoricalRefset(a.getRefsetId()))
				.collect(Collectors.toList());
	}

	public List<AssociationEntry> getAssociationEntries(ActiveState activeState, boolean historicalAssociationsOnly) {
		return getAssociationEntries().stream()
				.filter(a -> a.hasActiveState(activeState))
				.filter(a -> !historicalAssociationsOnly || Script.isHistoricalRefset(a.getRefsetId()))
				.collect(Collectors.toList());
	}
	
	public List<AxiomEntry> getAxiomEntries() {
		if (axiomEntries == null) {
			axiomEntries = new ArrayList<>();
		}
		return axiomEntries;
	}

	public void setAxiomEntries(List<AxiomEntry> axiomEntries) {
		this.axiomEntries = axiomEntries;
	}
	
	public List<AxiomEntry> getAxiomEntries(ActiveState activeState, boolean includeGCIs) {
		switch (activeState) {
			case BOTH: return getAxiomEntries();
			case ACTIVE: return getAxiomEntries().stream()
					.filter(a -> a.isActiveSafely())
					.filter(a -> includeGCIs || !a.isGCI())
					.collect(Collectors.toList());
			case INACTIVE: return getAxiomEntries().stream()
					.filter(a -> !a.isActiveSafely())
					.filter(a -> includeGCIs || !a.isGCI())
					.collect(Collectors.toList());
			default: throw new IllegalStateException("Unknown state " + activeState);
		}
	}
	
	//id	effectiveTime	active	moduleId	definitionStatusId
	public String[] toRF2() throws TermServerScriptException {
		if (active == null) {
			LOGGER.debug("Check {} for null active flag", this);
		}
		return new String[] {conceptId, 
				effectiveTime, 
				(active?"1":"0"), 
				moduleId, 
				SnomedUtils.translateDefnStatusToSctid(definitionStatus)};
	}
	
	public String[] toRF2Deletion() throws TermServerScriptException {
		return new String[] {conceptId, 
				effectiveTime, 
				deletionEffectiveTime,
				(active?"1":"0"), 
				"1",  //Deletion is active
				moduleId, 
				SnomedUtils.translateDefnStatusToSctid(definitionStatus)};
	}

	public void setInactivationIndicatorEntries(
			List<InactivationIndicatorEntry> inactivationIndicatorEntries) {
		this.inactivationIndicatorEntries = inactivationIndicatorEntries;
	}

	public boolean isDeleted() {
		return isDeleted;
	}

	public void delete(String deletionEffectiveTime) {
		this.isDeleted = true;
		this.deletionEffectiveTime = deletionEffectiveTime;
	}

	public InactivationIndicator getInactivationIndicator() {
		return inactivationIndicator;
	}

	public void setInactivationIndicator(InactivationIndicator inactivationIndicator) {
		this.inactivationIndicator = inactivationIndicator;
	}

	public static ConceptChange fromRf2Delta(String[] lineItems) {
		return (ConceptChange) fillFromRf2(new ConceptChange(lineItems[CON_IDX_ID]), lineItems);
	}
	
	public static Concept fillFromRf2(Concept c, String[] lineItems) {
		c.setActive(lineItems[CON_IDX_ACTIVE].equals("1"));
		c.setModuleId(lineItems[CON_IDX_MODULID]);
		c.setDefinitionStatus(SnomedUtils.translateDefnStatus(lineItems[CON_IDX_DEFINITIONSTATUSID]));
		//Set the given effective time last incase we've lost it by changing the moduleId
		c.setEffectiveTime(lineItems[CON_IDX_EFFECTIVETIME]);
		return c;
	}

	public List<Concept> getSiblings(CharacteristicType cType) {
		List<Concept> siblings = new ArrayList<>();
		//Get all the immediate children of the immediate parents
		for (Concept thisParent : getParents(cType)) {
			siblings.addAll(thisParent.getChildren(cType));
		}
		return siblings;
	}
	
	public void setId(String id) {
		super.setId(id);
		conceptId = id;
	}

	@Override
	public String getId() {
		if (id == null && conceptId != null) {
			super.setId(conceptId);
		}
		return super.getId();
	}

	@Override
	public String getReportedName() {
		return getFsn();
	}

	@Override
	public String getReportedType() {
		return conceptType==null?"": conceptType.toString();
	}

	public void addInactivationIndicator(InactivationIndicatorEntry i) {
		//Remove inactivation indicator first incase we're replacing it
		getInactivationIndicatorEntries().remove(i);
		getInactivationIndicatorEntries().add(i);
		if (i.isActiveSafely()) {
			setInactivationIndicator(SnomedUtils.translateInactivationIndicator(i.getInactivationReasonId()));
		}
	}

	public AssociationTargets getAssociationTargets() {
		if (associationTargets == null) {
			associationTargets = new AssociationTargets();
		}
		return associationTargets;
	}

	public void setAssociationTargets(AssociationTargets associationTargets) {
		this.associationTargets = associationTargets;
	}
	
	public Concept clone() {
		return clone(null, false);
	}
	
	public Concept cloneWithIds() {
		return clone(null, true);
	}
	
	public Concept cloneWithUUIDs() {
		return clone(null, true, true, true);
	}
	
	public Concept clone(String sctid) {
		return clone(sctid, false);
	}
	
	private Concept clone(String sctid, boolean keepIds) {
		//Do include inactive components by default - never know what we might want this for
		return clone(sctid, keepIds, true, false);
	}
	
	private Concept clone(String sctid, boolean keepIds, boolean includeInactiveComponents, boolean populateUUIDs) {
		Concept clone = new Concept(keepIds?conceptId:sctid, getFsn());
		if (populateUUIDs && clone.getId() == null) {
			clone.setId(UUID.randomUUID().toString());
		}
		clone.setEffectiveTime(keepIds?effectiveTime:null);
		clone.setActive(active);
		clone.setDefinitionStatus(getDefinitionStatus());
		clone.setModuleId(getModuleId());
		clone.setConceptType(conceptType);
		clone.setInactivationIndicator(inactivationIndicator);
		clone.setReleased(released);
		clone.setFsn(getFsn());
		clone.setDepth(depth);
		
		//Copy all descriptions
		ActiveState activeState = includeInactiveComponents ? ActiveState.BOTH : ActiveState.ACTIVE;
		for (Description d : getDescriptions(activeState)) {
			//We need to null out the conceptId since the clone is a new concept
			Description dClone = d.clone(keepIds?d.getDescriptionId():null, keepIds);
			dClone.setConceptId(keepIds?conceptId:null);
			dClone.setEffectiveTime(keepIds?d.getEffectiveTime():null);
			clone.addDescription(dClone);
			//If we're keeping IDs, copy any inactivation indicators also.
			if (keepIds) {
				dClone.setInactivationIndicatorEntries(new ArrayList<>(d.getInactivationIndicatorEntries()));
			}
			if (populateUUIDs && d.getId() == null) {
				dClone.setDescriptionId(UUID.randomUUID().toString());
			}
			dClone.setInactivationIndicator(d.getInactivationIndicator());
			dClone.setAcceptabilityMap(d.getAcceptabilityMap());
		}
		
		//Copy all stated relationships, or in the case of an exact clone (keepIds = true) also inferred
		Set<Relationship> selectedRelationships = keepIds ? relationships : getRelationships(CharacteristicType.STATED_RELATIONSHIP, activeState);
		for (Relationship r : selectedRelationships) {
			//We need to null out the sourceId since the clone is a new concept
			Relationship rClone = r.clone(keepIds?r.getRelationshipId():null);
			rClone.setEffectiveTime(keepIds?r.getEffectiveTime():null);
			rClone.setSourceId(null);
			//We don't want any hierarchy modifications done, so just add the clone directly
			clone.relationships.add(rClone);
			if (populateUUIDs && StringUtils.isEmpty(r.getId())) {
				rClone.setRelationshipId(UUID.randomUUID().toString());
			}
		}
		
		//Copy class axioms
		List<Axiom> axioms = getClassAxioms();
		for (Axiom axiom : axioms) {
			//We need to null out the sourceId since the clone is a new concept
			Axiom aClone = axiom.clone(keepIds?axiom.getId():null, clone);
			aClone.setEffectiveTime(keepIds?axiom.getEffectiveTime():null);
			clone.getClassAxioms().add(aClone);
			if (populateUUIDs && axiom.getId() == null) {
				aClone.setAxiomId(UUID.randomUUID().toString());
			}
		}
		
		//Copy axioms entries
		List<AxiomEntry> axiomEntries = getAxiomEntries();
		for (AxiomEntry axiom : axiomEntries) {
			//We need to null out the sourceId since the clone is a new concept
			AxiomEntry aClone = axiom.clone(keepIds?axiom.getId():null, keepIds);
			clone.getAxiomEntries().add(aClone);
		}
		
		//Copy Parent/Child arrays
		clone.inferredChildren = inferredChildren == null? new HashSet<>() : new HashSet<>(getInferredChildren());
		clone.statedChildren = statedChildren == null? new HashSet<>() : new HashSet<>(getStatedChildren());
		clone.inferredParents = inferredParents == null? new HashSet<>() : new HashSet<>(inferredParents);
		clone.statedParents = statedParents == null? new HashSet<>() : new HashSet<>(statedParents);
		
		//If we're keeping IDs, copy any inactivation indicators and historical associations also.
		if (keepIds) {
			clone.inactivationIndicatorEntries = getInactivationIndicatorEntries().stream()
					.map(i -> i.clone(keepIds))
					.collect(Collectors.toList());
					
			clone.associationEntries = getAssociationEntries().stream()
				.map(a -> a.clone(keepIds))
				.collect(Collectors.toList());
			
			for (AssociationEntry entry : clone.getAssociationEntries()) {
				SnomedUtils.addHistoricalAssociationInTsForm(clone, entry);
			}
		}

		//If we're not keeping ids, then mark everything as dirty
		if (!keepIds) {
			SnomedUtils.getAllComponents(clone).forEach(c -> c.setDirty());
		}
		
		return clone;
	}

	@Override
	public ComponentType getComponentType() {
		return ComponentType.CONCEPT;
	}
	
	public RelationshipGroup getRelationshipGroup(CharacteristicType charType, int groupId ) {
		for (RelationshipGroup g : getRelationshipGroups(charType)) {
			if (g.getGroupId() == groupId) {
				return g;
			}
		}
		return null;
	}
	
	public RelationshipGroup getRelationshipGroupSafely(CharacteristicType charType, int groupId ) {
		RelationshipGroup group = getRelationshipGroup(charType, groupId);
		if (group == null) {
			group = new RelationshipGroup(groupId);
		}
		return group;
	}
	
	/**
	 * Relationship groups will not include IS A relationships by default
	 */
	public Collection<RelationshipGroup> getRelationshipGroups(CharacteristicType characteristicType) {
		return getRelationshipGroups(characteristicType, false);
	}
	
	public Collection<RelationshipGroup> getRelationshipGroups(CharacteristicType characteristicType, boolean includeIsA) {
		List<RelationshipGroup> relationshipGroups = characteristicType.equals(CharacteristicType.STATED_RELATIONSHIP) ? statedRelationshipGroups : inferredRelationshipGroups;
		if (relationshipGroups == null) {
			boolean flatten = characteristicType.equals(CharacteristicType.INFERRED_RELATIONSHIP);
			//RelationshipGroups will be distinct from the axioms they came from
			Map<String, Map<Integer, RelationshipGroup>> axiomGroupMap = new HashMap<>();
			//If we're including group 0, always add that in any event
			for (Relationship r : getRelationships(characteristicType, ActiveState.ACTIVE)) {
				if (!includeIsA && r.getType().equals(IS_A)) {
					continue;
				}
				//Do we know about this axiom yet? Or if null, flatten
				Map<Integer, RelationshipGroup> axiomGroups = axiomGroupMap.get(flatten || r.getAxiomEntry() == null ? "N/A" : r.getAxiomEntry().getId());
				if (axiomGroups == null) {
					axiomGroups = new HashMap<>();
					axiomGroupMap.put((flatten || r.getAxiomEntry() == null ? "N/A" : r.getAxiomEntry().getId()), axiomGroups);
				}
				
				//Do we know about this Relationship Group yet?
				RelationshipGroup group = axiomGroups.get(r.getGroupId());
				if (group == null) {
					group = new RelationshipGroup(r.getGroupId() , r);
					axiomGroups.put(r.getGroupId(), group);
				} else {
					group.addRelationship(r);
				}
			}
			
			relationshipGroups = axiomGroupMap.values().stream()
					.flatMap(a -> a.values().stream())
					.collect(Collectors.toList());

			relationshipGroups.sort(Comparator.comparing(RelationshipGroup::getGroupId));

			if (characteristicType.equals(CharacteristicType.STATED_RELATIONSHIP)) {
				statedRelationshipGroups = relationshipGroups;
			} else {
				inferredRelationshipGroups = relationshipGroups;
			}
		}
		return relationshipGroups;
	}

	public int addRelationshipGroup(RelationshipGroup group, Set<Relationship> availableForReuse) {
		int changesMade = 0;
		if (availableForReuse == null) {
			availableForReuse = new HashSet<>();
		}

		for (IRelationship ir : group.getIRelationships()) {
			//Now these might be relationshipTemplates that need to be instantiated as proper relationships
			Relationship r = null;
			if (ir instanceof RelationshipTemplate) {
				r = ir.instantiate(this, group.getGroupId());
			} else {
				r = (Relationship) ir;
			}
			//Do we have one of these relationships available to be reused?
			for (Relationship reuseMe : new ArrayList<>(availableForReuse)) {
				if (reuseMe.getType().equals(r.getType()) && reuseMe.getTarget().equals(r.getTarget())) {
					//Check we actually need this relationship before reusing
					if (getRelationships(r, ActiveState.ACTIVE).size() == 0) { 
						System.out.println("** Reusing: " + reuseMe + " in group " + r.getGroupId());
						availableForReuse.remove(reuseMe);
						reuseMe.setGroupId(r.getGroupId());
						reuseMe.setActive(true);
						r = reuseMe;
					}
					break;
				}
			}
			changesMade += addOrReactivateRelationship(r);
		}
		recalculateGroups();
		return changesMade;
	}
	
	public void recalculateGroups() {
		//Force recalculation of groups next time they're requested
		statedRelationshipGroups = null;
		inferredRelationshipGroups = null;
	}

	public int getMaxGroupId(CharacteristicType charType) {
		int maxGroupId = 0;
		for (RelationshipGroup g : getRelationshipGroups(charType)) {
			if (g.getGroupId() > maxGroupId) {
				maxGroupId = g.getGroupId();
			}
		}
		return maxGroupId;
	}
	
	public List<Axiom> getClassAxioms() {
		if (classAxioms == null) {
			classAxioms = new ArrayList<>();
		}
		return classAxioms;
	}

	public void setClassAxioms(List<Axiom> classAxioms) {
		this.classAxioms = classAxioms;
	}
	
	public Axiom getClassAxiom(String id) {
		for (Axiom a : classAxioms) {
			if (a.getId().equals(id)) {
				return a;
			}
		}
		return null;
	}

	public List<Axiom> getAdditionalAxioms() {
		if (additionalAxioms == null) {
			additionalAxioms = new ArrayList<>();
		}
		return additionalAxioms;
	}

	public void setAdditionalAxioms(List<Axiom> additionalAxioms) {
		this.additionalAxioms = additionalAxioms;
	}

	public Set<Axiom> getGciAxioms() {
		if (gciAxioms == null) {
			gciAxioms = new HashSet<>();
		}
		return gciAxioms;
	}

	public void setGciAxioms(Set<Axiom> gciAxioms) {
		this.gciAxioms = gciAxioms;
	}

	public AxiomEntry getAxiom(String id) {
		if (axiomEntries != null) {
			for (AxiomEntry e : axiomEntries) {
				if (e.getId().equals(id)) {
					return e;
				}
			}
		}
		return null;
	}
	
	/**
	 * Cache a sum of relationship target / values for quick comparison
	 */
	public long getStatedAttribSum() {
		if (statedAttribSum == null) {
			statedAttribSum = 0L;
			for (Relationship r : getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
				//Drugs modelling doesn't consider 766939001 |Plays role (attribute)|
				//Don't add IS_A because we pick up extra of those when processing additional attributes
				//which throws this calculation out
				if (!r.getType().equals(PLAYS_ROLE) && !r.getType().equals(IS_A)) {
					if (r.isConcrete()) {
						statedAttribSum += Long.parseLong(r.getType().getId()) + Long.parseLong(r.getConcreteValue().getValue());
					} else {
						statedAttribSum += Long.parseLong(r.getType().getId()) + Long.parseLong(r.getTarget().getId());
					}
				}
			}
		}
		return statedAttribSum;
	}

	public Axiom getFirstActiveClassAxiom() {
		if (classAxioms == null) {
			classAxioms = new ArrayList<>();
		}
		for (Axiom axiom : classAxioms) {
			if (axiom.isActiveSafely()) {
				return axiom;
			}
		}

		//All existing concepts should have an active axiom
		//So no need to reactivate inactive ones.  
		//We will only add a new one if this is a new concept to create
		Axiom axiom = new Axiom(this);
		classAxioms.add(axiom);
		return axiom;
	}

	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) {
		if (!(other instanceof Concept)) {
			throw new IllegalStateException("Comparison of " + other + " failed.  It's not a concept");
		}
		Concept otherConcept = (Concept)other;
		List<String> differences = new ArrayList<>();
		String name = this.getClass().getSimpleName(); 
		commonFieldComparison(other, differences, ignoreEffectiveTime);
		
		if (!this.getDefinitionStatus().equals(otherConcept.getDefinitionStatus())) {
			differences.add("Definition Status is different in " + name + ": " + this.getDefinitionStatus() + " vs " + otherConcept.getDefinitionStatus());
		}
		return differences;
	}
	
	
	
	public List<Description> findDescriptionsContaining(String targetWord) {
		return findDescriptionsContaining(Collections.singletonList(targetWord));
	}

	public List<Description> findDescriptionsContaining(List<String> words) {
		return findDescriptionsContaining(words, false);
	}

	public List<Description> findDescriptionsContaining(List<String> words, boolean onlyPref) {
		if (words == null || words.isEmpty()) {
			return new ArrayList<>();
		}
		
		return descriptions.stream()
				.filter(d -> d.isActiveSafely())
				.filter(d -> onlyPref == false || d.isPreferred())
				.filter(d -> words.stream()
						.anyMatch(w -> StringUtils.containsIgnoreCase(d.getTerm(), w)))
				.collect(Collectors.toList());
	}

	public ObjectPropertyAxiomRepresentation getObjectPropertyAxiomRepresentation() {
		return objectPropertyAxiom;
	}
	
	public void setObjectPropertyAxiomRepresentation(ObjectPropertyAxiomRepresentation rep) {
		this.objectPropertyAxiom = rep;
	}

	public void mergeObjectPropertyAxiomRepresentation(ObjectPropertyAxiomRepresentation rep) {
		if (this.objectPropertyAxiom == null) {
			this.objectPropertyAxiom = rep;
		} else {
			this.objectPropertyAxiom.mergeProperties(rep);
		}
		
	}

	public InactivationIndicatorEntry getInactivationIndicatorEntry(String indicatorId) {
		for (InactivationIndicatorEntry i : getInactivationIndicatorEntries()) {
			if (i.getId().equals(indicatorId)) {
				return i;
			}
		}
		return null;
	}

	public AssociationEntry getAssociationEntry(String assocId) {
		for (AssociationEntry a : getAssociationEntries()) {
			if (a.getId().equals(assocId)) {
				return a;
			}
		}
		return null;
	}

	public boolean fsnContainsAny(List<String> words) {
		if (words == null) {
			return false;
		}
		String lowerFsn = getFsn().toLowerCase();
		for (String word : words) {
			if (lowerFsn.contains(word)) {
				return true;
			}
		}
		return false;
	}

	public List<Relationship> getRelationshipsFromAxiom(String axiomId, ActiveState activeState) {
		List<Relationship> fromAxiom = new ArrayList<>();
		for (Relationship r : relationships) {
			if (activeState.equals(ActiveState.BOTH) || 
					(activeState.equals(ActiveState.ACTIVE) && r.isActiveSafely()) ||
					(activeState.equals(ActiveState.INACTIVE) && !r.isActiveSafely())) {
				if (r.getAxiom() != null && r.getAxiom().getId().equals(axiomId)) {
					fromAxiom.add(r);
				} else if (r.getAxiomEntry() != null && r.getAxiomEntry().getId().equals(axiomId)) {
					fromAxiom.add(r);
				}
			}
		}
		return fromAxiom;
	}
	
	transient String semTag = null;
	public String getSemTag() {
		
		if (semTag != null) {
			return semTag;
		}
		
		if (!SnomedUtils.isEmpty(fsn)) {
			String[] parts = SnomedUtilsBase.deconstructFSN(fsn.toString());
			if (!StringUtils.isEmpty(parts[1])) {
				semTag = parts[1];
				return semTag;
			}
		}
		return "";
	}

	public boolean isPrimitive() {
		return definitionStatus.equals(DefinitionStatus.PRIMITIVE);
	}
	
	public void setDefinitionStatusId(String definitionStatusId) {
		definitionStatus = SnomedUtils.translateDefnStatus(definitionStatusId);
	}

	public void addGciAxiom(Axiom axiom) {
		//Watch that this is a set, so we will replace any existing axiom with the same id
		//Ah, but we have to explicitly remove it first, otherwise it doesn't consider the 
		//updated object to be any different (since only the ID is checked for equality), 
		//and so doesn't see the need to make any changes to the set.
		getGciAxioms().remove(axiom);
		getGciAxioms().add(axiom);
	}

	@Override
	public String[] getMutableFields() {
		String[] mutableFields = super.getMutableFields();
		mutableFields[getMutableFieldCount() - 1] = this.definitionStatus.toString();
		return mutableFields;
	}

	@Override
	public int getMutableFieldCount() {
		return super.getMutableFieldCount() + 1;
	}

	//Return any relationship groups that contain an attribute with the targetType
	public List<RelationshipGroup> getRelationshipGroups(CharacteristicType charType, Concept type) {
		return getRelationshipGroups(charType).stream()
				.filter(g -> g.containsType(type))
				.collect(Collectors.toList());
	}
	
	//Return any relationship groups that contain an attribute with the targetType
	public List<RelationshipGroup> getRelationshipGroups(CharacteristicType charType, Concept type, Concept value) {
		return getRelationshipGroups(charType).stream()
				.filter(g -> g.containsTypeValue(type, value))
				.collect(Collectors.toList());
	}
	
	//Return any relationship groups that contain an attribute with the targetType
	public List<RelationshipGroup> getRelationshipGroups(CharacteristicType charType, RelationshipTemplate rt) {
		return getRelationshipGroups(charType).stream()
				.filter(g -> g.containsTypeValue(rt))
				.collect(Collectors.toList());
	}

	public Concept cloneAsNewConcept() {
		return cloneAsNewConcept(null);
	}
	/**
	 * Create a clone that can be used as a new concept.  So only stated modelling and 
	 * no references back to the original axioms
	 */
	public Concept cloneAsNewConcept(String sctid) {
		Concept clone = this.clone();  //Will not copy inferred rels unless keepIds = true
		clone.setId(sctid);
		//remove any inactive components
		clone.removeInactiveComponents();
		
		//Strip axiom data from relationships
		for (Relationship r : clone.getRelationships()) {
			r.setAxiom(null);
			r.setAxiomEntry(null);
		}
		return clone;
	}

	private void removeInactiveComponents() {
		descriptions.removeIf(c -> !c.isActiveSafely());
		relationships.removeIf(c -> !c.isActiveSafely());
		if (inactivationIndicatorEntries != null) {
			inactivationIndicatorEntries.removeIf(c -> !c.isActiveSafely());
		}
		
		if (associationEntries != null) {
			associationEntries.removeIf(c -> !c.isActiveSafely());
		}
		
		if (axiomEntries != null) {
			axiomEntries.removeIf(c -> !c.isActiveSafely());
		}
		
		for (Description d : descriptions) {
			d.getLangRefsetEntries().removeIf(c -> !c.isActiveSafely());
			d.getInactivationIndicatorEntries().removeIf(c -> !c.isActiveSafely());
			d.getAssociationEntries().removeIf(c -> !c.isActiveSafely());
		}
	}

	public Map<String, List<RelationshipGroup>> getRelationshipGroupsByAxiom() {
		Map<String, List<RelationshipGroup>> groupsByAxiom = new HashMap<>();
		//For now we'll assume that the first Axiom encountered in a group is the owning axiom
		for (RelationshipGroup g : getRelationshipGroups(CharacteristicType.STATED_RELATIONSHIP, true)) {
			AxiomEntry a = g.getAxiomEntry();
			List<RelationshipGroup> axiomGroups = groupsByAxiom.get(a.getId());
			if (axiomGroups == null) {
				axiomGroups = new ArrayList<>();
				groupsByAxiom.put(a.getId(), axiomGroups);
			}
			axiomGroups.add(g);
		}
		return groupsByAxiom;
	}

	public Set<AlternateIdentifier> getAlternateIdentifiers() {
		return alternateIdentifiers == null ? new HashSet<>() : alternateIdentifiers;
	}

	public void setAlternateIdentifiers(Set<AlternateIdentifier> alternateIdentifiers) {
		this.alternateIdentifiers = alternateIdentifiers;
	}

	public void addAlternateIdentifier(String id, String schemeId) {
		if (alternateIdentifiers == null) {
			alternateIdentifiers = new HashSet<>();
		}
		AlternateIdentifier altId = new AlternateIdentifier();
		altId.setReferencedComponentId(this.getId());
		altId.setAlternateIdentifier(id);
		altId.setActive(true);
		altId.setModuleId(getModuleId());
		altId.setIdentifierSchemeId(schemeId);
		altId.setDirty();
		alternateIdentifiers.add(altId);
	}

	public Set<RefsetMember> getOtherRefsetMembers() {
		if (otherRefsetMembers == null) {
			otherRefsetMembers = new HashSet<>();
		}
		return otherRefsetMembers;
	}

	public void setOtherRefsetMembers(Set<RefsetMember> otherRefsetMembers) {
		this.otherRefsetMembers = otherRefsetMembers;
	}

	public void addOtherRefsetMember(RefsetMember rm) {
		//Remove any existing instance of this member first, as a change of state
		//will not be stored, as the refset member will have the same id and so
		//looks like it's already been stored.
		getOtherRefsetMembers().remove(rm);
		getOtherRefsetMembers().add(rm);
	}

	public RefsetMember getOtherRefsetMember(String id) {
		for (RefsetMember m : getOtherRefsetMembers()) {
			if (m.getId().equals(id)) {
				return m;
			}
		}
		return null;
	}

	//Set the same axiom details on all stated relationships - if possible
	public void normalizeStatedRelationships() {
		AxiomEntry axiomEntry = null;
		for (Relationship r : getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getAxiomEntry() != null) {
				if (axiomEntry == null) {
					axiomEntry = r.getAxiomEntry();
				} else if (!axiomEntry.equals(r.getAxiomEntry())) {
					throw new IllegalStateException("Concept " + conceptId + " has multiple axioms in use");
				}
			}
		}
		//Now set all axioms that don't know about that axiom to the same one
		for (Relationship r : getRelationships(CharacteristicType.STATED_RELATIONSHIP, ActiveState.ACTIVE)) {
			if (r.getAxiomEntry() == null) {
				r.setAxiomEntry(axiomEntry);
			}
		}
	}

	@Override
	public boolean matchesMutableFields(Component other) {
		Concept otherConcept = (Concept) other;
		return getDefinitionStatus().equals(otherConcept.getDefinitionStatus());
	}

	public boolean hasAlternateIdentifier(String codeSystemSctId) {
		return getAlternateIdentifiers().stream()
				.anyMatch(a -> a.getIdentifierSchemeId().equals(codeSystemSctId));
	}

	public boolean appearsInRefset(Concept refset) {
		return getOtherRefsetMembers().stream()
				.filter(RefsetMember::isActiveSafely)
				.anyMatch(m -> m.getRefsetId().equals(refset.getId()));
	}
}
