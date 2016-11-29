
package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Generated;

import org.ihtsdo.termserver.scripting.TermServerScript;
import org.ihtsdo.termserver.scripting.TermServerScriptException;
import org.ihtsdo.termserver.scripting.domain.RF2Constants.ActiveState;
import org.ihtsdo.termserver.scripting.util.SnomedUtils;

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
	private DefinitionStatus definitionStatus;
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

	List<Concept> statedParents = new ArrayList<Concept>();
	List<Concept> inferredParents = new ArrayList<Concept>();
	List<Concept> statedChildren = new ArrayList<Concept>();
	List<Concept> inferredChildren = new ArrayList<Concept>();
	
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

	public DefinitionStatus getDefinitionStatus() {
		return definitionStatus;
	}

	public void setDefinitionStatus(DefinitionStatus definitionStatus) {
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
	
	public List<Relationship> getRelationships(CharacteristicType characteristicType, ActiveState state, String effectiveTime) {
		List<Relationship> matches = new ArrayList<Relationship>();
		for (Relationship r : relationships) {
			if (effectiveTime == null || r.getEffectiveTime().equals(effectiveTime)) {
				if (characteristicType.equals(CharacteristicType.ALL) || r.getCharacteristicType().equals(characteristicType)) {
					if (state.equals(ActiveState.BOTH) || (state.equals(ActiveState.ACTIVE) && r.isActive()) ||
							(state.equals(ActiveState.INACTIVE) && !r.isActive())) {
						matches.add(r);
					}
				}
			}
		}
		return matches;
	}
	
	public List<Relationship> getRelationships(CharacteristicType characteristicType, ActiveState state) {
		return getRelationships(characteristicType, state, null);
	}
	
	public List<Relationship> getRelationships(CharacteristicType characteristicType, Concept type, ActiveState state) {
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
		if (!(other instanceof Concept)) {
			return false;
		}
		Concept rhs = ((Concept) other);
		return (this.conceptId.compareTo(rhs.conceptId) == 0);
	}

	public void addRelationship(Concept type, Concept target) {
		Relationship r = new Relationship();
		r.setActive(true);
		r.setGroupId(0);
		r.setCharacteristicType(CharacteristicType.STATED_RELATIONSHIP);
		r.setSourceId(this.getConceptId());
		r.setType(type);
		r.setTarget(target);
		r.setModifier(Modifier.EXISTENTIAL);
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
	
	public void addChild(CharacteristicType characteristicType, Concept c) {
		getChildren(characteristicType).add(c);
	}
	
	public void addParent(CharacteristicType characteristicType, Concept p) {
		getParents(characteristicType).add(p);
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
	
	public Set<Concept> getDescendents(int depth) throws TermServerScriptException {
		return getDescendents(depth, CharacteristicType.INFERRED_RELATIONSHIP, ActiveState.ACTIVE);
	}
	
	public Set<Concept> getDescendents(int depth, CharacteristicType characteristicType, ActiveState activeState) throws TermServerScriptException {
		Set<Concept> allDescendents = new HashSet<Concept>();
		this.populateAllDescendents(allDescendents, depth, characteristicType, activeState);
		return allDescendents;
	}
	
	private void populateAllDescendents(Set<Concept> descendents, int depth, CharacteristicType characteristicType, ActiveState activeState) throws TermServerScriptException {
		for (Concept thisChild : getChildren(characteristicType)) {
			if (activeState.equals(ActiveState.BOTH) || thisChild.active == SnomedUtils.translateActive(activeState)) {
				descendents.add(thisChild);
				if (depth == NOT_SET || depth > 1) {
					int newDepth = depth == NOT_SET ? NOT_SET : depth - 1;
					thisChild.populateAllDescendents(descendents, newDepth, characteristicType, activeState);
				}
			}
		}
	}
	
	private List<Concept> getChildren(CharacteristicType characteristicType) {
		switch (characteristicType) {
			case STATED_RELATIONSHIP : return statedChildren;
			case INFERRED_RELATIONSHIP : return inferredChildren;
			default:
		}
		return null;
	}

	public List<Description> getDescriptions(Acceptability Acceptability, DescriptionType descriptionType, ActiveState activeState) throws TermServerScriptException {
		List<Description> matchingDescriptions = new ArrayList<Description>();
		for (Description thisDescription : getDescriptions(activeState)) {
			if (	( thisDescription.getAcceptabilityMap() != null && thisDescription.getAcceptabilityMap().containsValue(Acceptability)) &&
					( descriptionType == null || thisDescription.getType().equals(descriptionType) )
				) {
				//A preferred description can be preferred in either dialect, but if we're looking for an acceptable one, 
				//then it must not also be preferred in the other dialect
				if (Acceptability.equals(Acceptability.PREFERRED) || !thisDescription.getAcceptabilityMap().containsValue(Acceptability.PREFERRED)) {
					matchingDescriptions.add(thisDescription);
				}
			} else {
				if (thisDescription.getAcceptabilityMap() == null && thisDescription.isActive()) {
					TermServerScript.warn (thisDescription + " is active with no Acceptability map");
				}
			}
		}
		return matchingDescriptions;
	}
	
	public List<Description> getDescriptions(String langRefsetId, Acceptability targetAcceptability, DescriptionType descriptionType, ActiveState active) throws TermServerScriptException {
		//Get the matching terms, and then pick the ones that have the appropriate Acceptability for the specified Refset
		List<Description> matchingDescriptions = new ArrayList<Description>();
		for (Description d : getDescriptions(targetAcceptability, descriptionType, active)) {
			Acceptability Acceptability = d.getAcceptabilityMap().get(langRefsetId);
			if (Acceptability!= null && Acceptability.equals(targetAcceptability)) {
				//Need to check the Acceptability because the first function might match on some other language
				matchingDescriptions.add(d);
			}
		}
		return matchingDescriptions;
	}
	
	public List<Description> getDescriptions(ActiveState a) {
		List<Description> results = new ArrayList<Description>();
		for (Description d : descriptions) {
			if (SnomedUtils.descriptionHasActiveState(d, a)) {
					results.add(d);
			}
		}
		return results;
	}

	public void addDescription(Description description) {
		descriptions.add(description);
		
	}

	public List<Concept> getParents(CharacteristicType characteristicType) {
		switch (characteristicType) {
			case STATED_RELATIONSHIP : return new ArrayList<Concept>(statedParents);
			case INFERRED_RELATIONSHIP: return new ArrayList<Concept>(inferredParents);
			default: return null;
		}
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
			if (d.isActive() && d.getType().equals(DescriptionType.FSN)) {
				return d;
			}
		}
		return null;
	}
	
	public List<Description> getSynonyms(Acceptability Acceptability) {
		List<Description> synonyms = new ArrayList<Description>();
		for (Description d : descriptions) {
			if (d.isActive() && d.getAcceptabilityMap().values().contains(Acceptability) && d.getType().equals(DescriptionType.SYNONYM)) {
				synonyms.add(d);
			}
		}
		return synonyms;
	}

	public boolean hasTerm(String term) {
		boolean hasTerm = false;
		for (Description d : descriptions) {
			if (d.getTerm().equals(term)) {
				hasTerm = true;
				break;
			}
		}
		return hasTerm;
	}

	public Description findTerm(String term) {
		//First look for a match in the active terms, then try inactive
		for (Description d : getDescriptions(ActiveState.ACTIVE)) {
			if (d.getTerm().equals(term)) {
				return d;
			}
		}
		
		for (Description d : getDescriptions(ActiveState.INACTIVE)) {
			if (d.getTerm().equals(term)) {
				return d;
			}
		}
		return null;
	}

}
