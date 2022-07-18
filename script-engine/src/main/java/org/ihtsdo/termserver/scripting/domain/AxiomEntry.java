package org.ihtsdo.termserver.scripting.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.ihtsdo.otf.rest.client.terminologyserver.pojo.Component;

//id	effectiveTime	active	moduleId	refsetId	referencedComponentId	owlExpression
public class AxiomEntry extends RefsetMember implements ScriptConstants {

	private String owlExpression;
	private boolean isGCI = false;
	
	public AxiomEntry clone(String newComponentSctId, boolean keepIds) {
		AxiomEntry clone = new AxiomEntry();
		clone.id = keepIds ? this.id : UUID.randomUUID().toString();
		clone.effectiveTime = keepIds ? this.effectiveTime :null;
		clone.moduleId = this.moduleId;
		clone.active = this.active;
		clone.refsetId = this.refsetId;
		clone.referencedComponentId = keepIds ? this.referencedComponentId : newComponentSctId;
		clone.owlExpression = this.owlExpression;
		clone.isDirty = true; //New components need to be written to any delta
		return clone;
	}
	
	public static AxiomEntry withDefaults (Concept c, String expression) {
		AxiomEntry axiom = new AxiomEntry();
		axiom.id = UUID.randomUUID().toString();
		axiom.active = true;
		axiom.refsetId = SCTID_OWL_AXIOM_REFSET;
		axiom.referencedComponentId = c.getConceptId();
		axiom.owlExpression = expression;
		return axiom;
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
	
	public String getOwlExpression() {
		return owlExpression;
	}
	public void setOwlExpression(String owlExpression) {
		if (this.owlExpression != null && !this.owlExpression.equals(owlExpression)) {
			setDirty();
		}
		this.owlExpression = owlExpression;
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
		return ComponentType.AXIOM;
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
	
	@Override
	public List<String> fieldComparison(Component other, boolean ignoreEffectiveTime) {
		AxiomEntry otherA = (AxiomEntry)other;
		List<String> differences = new ArrayList<>();
		String name = this.getClass().getSimpleName(); 
		commonFieldComparison(other, differences, ignoreEffectiveTime);
		
		if (!this.getRefsetId().equals(otherA.getRefsetId())) {
			differences.add("RefsetId is different in " + name + ": " + this.getRefsetId() + " vs " + otherA.getRefsetId());
		}
		
		if (!this.getReferencedComponentId().equals(otherA.getReferencedComponentId())) {
			differences.add("RefCompId is different in " + name + ": " + this.getReferencedComponentId() + " vs " + otherA.getReferencedComponentId());
		}
		
		if (!this.getOwlExpression().equals(otherA.getOwlExpression())) {
			differences.add("OwlExpression is different in " + name + ": " + this.getOwlExpression() + " vs " + otherA.getOwlExpression());
		}
		return differences;
	}

	public boolean isGCI() {
		return isGCI;
	}

	public void setGCI(boolean isGCI) {
		this.isGCI = isGCI;
	}
	
}
