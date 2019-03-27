package org.ihtsdo.termserver.scripting.domain;

import java.util.UUID;

//id	effectiveTime	active	moduleId	refsetId	referencedComponentId	owlExpression
public class AxiomEntry extends Component implements RF2Constants {

	private String id;
	private String effectiveTime;
	private String moduleId;
	private Boolean active;
	private String refsetId;
	private String referencedComponentId;
	private String owlExpression;
	private boolean dirty = false;
	
	public AxiomEntry clone(String newComponentSctId) {
		AxiomEntry clone = new AxiomEntry();
		clone.id = UUID.randomUUID().toString();
		clone.effectiveTime = null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.referencedComponentId = newComponentSctId;
		clone.owlExpression = this.owlExpression;
		clone.dirty = true; //New components need to be written to any delta
		return clone;
	}
	
	public String toString() {
		String activeIndicator = isActive()?"":"*";
		return "[" + activeIndicator + "OWL]:" + id + " - " + refsetId + " : " + referencedComponentId + "->" + owlExpression;
	}
	
	public String[] toRF2() {
		return new String[] { id, 
				(effectiveTime==null?"":effectiveTime), 
				(active?"1":"0"),
				moduleId, refsetId,
				referencedComponentId,
				owlExpression
		};
	}
	
	public String getId() {
		return id;
	}
	public void setId(String id) {
		this.id = id;
	}
	public String getEffectiveTime() {
		return effectiveTime;
	}
	public void setEffectiveTime(String effectiveTime) {
		if (this.effectiveTime != null && !this.effectiveTime.isEmpty() && effectiveTime == null) {
			//Are we resetting this component to mark a change?
			dirty = true;
		}
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
	public void setActive(boolean newActiveState) {
		if (this.active != null && this.active != newActiveState) {
			setDirty();
			setEffectiveTime(null);
		}
		this.active = newActiveState;
	}
	public String getRefsetId() {
		return refsetId;
	}
	public void setRefsetId(String refsetId) {
		this.refsetId = refsetId;
	}
	public String getReferencedComponentId() {
		return referencedComponentId;
	}
	public void setReferencedComponentId(String referencedComponentId) {
		this.referencedComponentId = referencedComponentId;
	}
	public String getOwlExpression() {
		return owlExpression;
	}
	public void setOwlExpression(String owlExpression) {
		if (this.owlExpression != null && !this.owlExpression.equals(owlExpression)) {
			dirty = true;
		}
		this.owlExpression = owlExpression;
	}

	public boolean isDirty() {
		return dirty;
	}
	
	public void setDirty() {
		dirty = true;
	}
	
	public static AxiomEntry fromRf2(String[] lineItems) {
		AxiomEntry o = new AxiomEntry();
		o.setId(lineItems[REF_IDX_ID]);
		o.setEffectiveTime(lineItems[REF_IDX_EFFECTIVETIME]);
		o.setActive(lineItems[REF_IDX_ACTIVE].equals("1"));
		o.setModuleId(lineItems[REF_IDX_MODULEID]);
		o.setRefsetId(lineItems[REF_IDX_REFSETID]);
		o.setReferencedComponentId(lineItems[REF_IDX_REFCOMPID]);
		o.setOwlExpression(lineItems[REF_IDX_FIRST_ADDITIONAL]);
		return o;
	}

	@Override
	public ComponentType getComponentType() {
		return ComponentType.OWL;
	}

	@Override
	public String getReportedName() {
		return getId();
	}

	@Override
	public String getReportedType() {
		return getComponentType().toString();
	}
	
	@Override 
	public boolean equals(Object o) {
		if (o instanceof AxiomEntry) {
			return this.getId().equals(((AxiomEntry)o).getId());
		}
		return false;
	}
}
