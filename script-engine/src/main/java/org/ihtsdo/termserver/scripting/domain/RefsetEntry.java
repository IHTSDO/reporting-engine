
package org.ihtsdo.termserver.scripting.domain;

import java.util.List;

import org.apache.commons.lang.NotImplementedException;
import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;
import org.ihtsdo.otf.exception.TermServerScriptException;

import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

public class RefsetEntry extends Component {

	@SerializedName("id")
	@Expose
	private String id;
	
	@SerializedName("effectiveTime")
	@Expose
	private String effectiveTime;
	
	@SerializedName("released")
	@Expose
	private Boolean released;
	
	@SerializedName("active")
	@Expose
	private Boolean active;
	
	@SerializedName("moduleId")
	@Expose
	private String moduleId;
	
	@SerializedName("referencedComponentId")
	@Expose
	private String referencedComponentId;
	
	@SerializedName("refsetId")
	@Expose
	private String refsetId;
	
	@SerializedName("valueId")
	@Expose
	private String valueId;
	
	@SerializedName("commitComment")
	@Expose
	private String commitComment = "TermserverScript update";

	/**
	 * No args constructor for use in serialization
	 * 
	 */
	public RefsetEntry() {
	}

	/**
	 * 
	 * @param released
	 * @param id
	 * @param refsetId
	 * @param moduleId
	 * @param valueId
	 * @param active
	 * @param referencedComponent
	 */
	public RefsetEntry(String id, Boolean released, Boolean active, String moduleId, String referencedComponentId, String refsetId, String valueId) {
		super();
		this.id = id;
		this.released = released;
		this.active = active;
		this.moduleId = moduleId;
		this.referencedComponentId = referencedComponentId;
		this.refsetId = refsetId;
		this.valueId = valueId;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public Boolean getReleased() {
		return released;
	}

	public void setReleased(Boolean released) {
		this.released = released;
	}

	public Boolean getActive() {
		return active;
	}
	
	public boolean isActive() {
		return active;
	}

	public void setActive(Boolean active) {
		this.active = active;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public String getReferencedComponentId() {
		return referencedComponentId;
	}

	public void setReferencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
	}

	public String getReferenceSetId() {
		return refsetId;
	}

	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}

	public String getValueId() {
		return valueId;
	}

	public void setValueId(String valueId) {
		this.valueId = valueId;
	}

	@Override
	public String getReportedName() {
		return refsetId;
	}

	@Override
	public String getReportedType() {
		return "RefsetEntry";
	}
	
	public String toString() {
		return id;
	}
	

	public String getCommitComment() {
		return commitComment;
	}

	public String getEffectiveTime() {
		return effectiveTime;
	}

	public void setEffectiveTime(String effectiveTime) {
		this.effectiveTime = effectiveTime;
	}

	@Override
	public String[] toRF2() throws TermServerScriptException {
		throw new NotImplementedException();
	}

	@Override
	public ComponentType getComponentType() {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public List<String> fieldComparison(Component other) {
		//Actually not expecting to see this called since an RF2 comparison
		//Would examine specific refset objects;
		throw new IllegalStateException("Unexpected comparison of axiom");
	}

}
