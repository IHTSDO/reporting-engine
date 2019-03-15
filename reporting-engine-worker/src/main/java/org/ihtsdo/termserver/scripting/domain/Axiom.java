package org.ihtsdo.termserver.scripting.domain;

import java.util.*;

import org.ihtsdo.termserver.scripting.*;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class Axiom extends Component implements RF2Constants, Comparable<Axiom>  {

	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	@SerializedName("active")
	@Expose
	private boolean active = true;
	@SerializedName("released")
	@Expose
	private Boolean released;
	@SerializedName(value="axiomId", alternate="id")
	@Expose
	private String axiomId;
	@SerializedName("definitionStatus")
	@Expose
	private DefinitionStatus definitionStatus;
	@SerializedName("relationships")
	@Expose
	private List<Relationship> relationships = new ArrayList<Relationship>();
	@SerializedName("namedConceptOnLeft")
	@Expose
	private Boolean namedConceptOnLeft;

	public String getModuleId() {
		return moduleId;
	}
	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}
	public Boolean getReleased() {
		return released;
	}
	public void setReleased(Boolean released) {
		this.released = released;
	}
	public String getAxiomId() {
		return axiomId;
	}
	public void setAxiomId(String axiomId) {
		this.axiomId = axiomId;
	}
	public DefinitionStatus getDefinitionStatus() {
		return definitionStatus;
	}
	public void setDefinitionStatus(DefinitionStatus definitionStatus) {
		this.definitionStatus = definitionStatus;
	}
	public List<Relationship> getRelationships() {
		return relationships;
	}
	public void setRelationships(List<Relationship> relationships) {
		this.relationships = relationships;
	}
	public Boolean getNamedConceptOnLeft() {
		return namedConceptOnLeft;
	}
	public void setNamedConceptOnLeft(Boolean namedConceptOnLeft) {
		this.namedConceptOnLeft = namedConceptOnLeft;
	}
	@Override
	public String getEffectiveTime() {
		return this.effectiveTime;
	}
	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}
	public void setActive(boolean active) {
		this.active = active;
	}
	
	@Override
	public int compareTo(Axiom o) {
		// TODO Auto-generated method stub
		return 0;
	}
	@Override
	public String getId() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public boolean isActive() {
		return active;
	}
	@Override
	public String getReportedName() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String getReportedType() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public ComponentType getComponentType() {
		// TODO Auto-generated method stub
		return null;
	}
	@Override
	public String[] toRF2() throws TermServerScriptException {
		// TODO Auto-generated method stub
		return null;
	}
}
