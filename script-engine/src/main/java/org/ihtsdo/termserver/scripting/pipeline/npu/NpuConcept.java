package org.ihtsdo.termserver.scripting.pipeline.npu;

import com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import org.ihtsdo.termserver.scripting.pipeline.ExternalConcept;

public class NpuConcept extends ExternalConcept {
	
	@JacksonXmlProperty(localName = "created_date")
	private String createdDate;
	
	@JacksonXmlProperty(localName = "change_date")
	private String changeDate;
	
	@JacksonXmlProperty(localName = "change_comment")
	private String changeComment;
	
	@JacksonXmlProperty(localName = "npu_code")
	private String npuCode; //Actually we'll read and write externalIdentifier
	
	@JacksonXmlProperty(localName = "short_definition")
	private String shortDefinition;
	
	@JacksonXmlProperty(localName = "system_short")
	private String systemShort;
	
	@JacksonXmlProperty(localName = "sys_spec_short")
	private String sysSpecShort;
	
	@JacksonXmlProperty(localName = "component_short")
	private String componentShort;
	
	@JacksonXmlProperty(localName = "comp_spec_short")
	private String compSpecShort;
	
	@JacksonXmlProperty(localName = "kind_of_property_short")
	private String kindOfPropertyShort;
	
	@JacksonXmlProperty(localName = "proc_short")
	private String procShort;
	
	@JacksonXmlProperty(localName = "unit_short")
	private String unitShort;
	
	@JacksonXmlProperty(localName = "full_definition")
	private String fullDefinition;
	
	@JacksonXmlProperty(localName = "system")
	private String system;
	
	@JacksonXmlProperty(localName = "sys_spec")
	private String sysSpec;
	
	@JacksonXmlProperty(localName = "component")
	private String component;
	
	@JacksonXmlProperty(localName = "comp_spec")
	private String compSpec;
	
	@JacksonXmlProperty(localName = "kind_of_property")
	private String kindOfProperty;
	
	@JacksonXmlProperty(localName = "proc")
	private String proc;
	
	@JacksonXmlProperty(localName = "unit")
	private String unit;
	
	@JacksonXmlProperty(localName = "specialty")
	private String specialty;
	
	@JacksonXmlProperty(localName = "context_dependent")
	private String contextDependent;
	
	@JacksonXmlProperty(localName = "group")
	private String group;
	
	@JacksonXmlProperty(localName = "scale_type")
	private String scaleType;
	
	@JacksonXmlProperty(localName = "replaces")
	private String replaces;
	
	@JacksonXmlProperty(localName = "replaced_by")
	private String replacedBy;
	
	@JacksonXmlProperty(localName = "effective_from")
	private String effectiveFrom;
	
	@JacksonXmlProperty(localName = "effective_to")
	private String effectiveTo;
	
	@JacksonXmlProperty(localName = "active")
	private String active;
	
	@JacksonXmlProperty(localName = "current_version")
	private String currentVersion;


	// Getters and Setters
	public String getCreatedDate() {
		return createdDate;
	}

	public void setCreatedDate(String createdDate) {
		this.createdDate = createdDate;
	}

	public String getChangeDate() {
		return changeDate;
	}

	public void setChangeDate(String changeDate) {
		this.changeDate = changeDate;
	}

	public String getChangeComment() {
		return changeComment;
	}

	public void setChangeComment(String changeComment) {
		this.changeComment = changeComment;
	}

	public String getNpuCode() {
		return this.externalIdentifier;
	}

	public void setNpuCode(String npuCode) {
		this.externalIdentifier = npuCode;
	}

	public String getShortDefinition() {
		return shortDefinition;
	}

	public void setShortDefinition(String shortDefinition) {
		this.shortDefinition = shortDefinition;
	}

	public String getSystemShort() {
		return systemShort;
	}

	public void setSystemShort(String systemShort) {
		this.systemShort = systemShort;
	}

	public String getSysSpecShort() {
		return sysSpecShort;
	}

	public void setSysSpecShort(String sysSpecShort) {
		this.sysSpecShort = sysSpecShort;
	}

	public String getComponentShort() {
		return componentShort;
	}

	public void setComponentShort(String componentShort) {
		this.componentShort = componentShort;
	}

	public String getCompSpecShort() {
		return compSpecShort;
	}

	public void setCompSpecShort(String compSpecShort) {
		this.compSpecShort = compSpecShort;
	}

	public String getKindOfPropertyShort() {
		return kindOfPropertyShort;
	}

	public void setKindOfPropertyShort(String kindOfPropertyShort) {
		this.kindOfPropertyShort = kindOfPropertyShort;
	}

	public String getProcShort() {
		return procShort;
	}

	public void setProcShort(String procShort) {
		this.procShort = procShort;
	}

	public String getUnitShort() {
		return unitShort;
	}

	public void setUnitShort(String unitShort) {
		this.unitShort = unitShort;
	}

	public void setFullDefinition(String fullDefinition) {
		this.fullDefinition = fullDefinition;
	}

	public String getSystem() {
		return system;
	}

	public void setSystem(String system) {
		this.system = system;
	}

	public String getSysSpec() {
		return sysSpec;
	}

	public void setSysSpec(String sysSpec) {
		this.sysSpec = sysSpec;
	}

	public String getComponent() {
		return component;
	}

	public void setComponent(String component) {
		this.component = component;
	}

	public String getCompSpec() {
		return compSpec;
	}

	public void setCompSpec(String compSpec) {
		this.compSpec = compSpec;
	}

	public String getKindOfProperty() {
		return kindOfProperty;
	}

	public void setKindOfProperty(String kindOfProperty) {
		this.kindOfProperty = kindOfProperty;
	}

	public String getProc() {
		return proc;
	}

	public void setProc(String proc) {
		this.proc = proc;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getSpecialty() {
		return specialty;
	}

	public void setSpecialty(String specialty) {
		this.specialty = specialty;
	}

	public String getContextDependent() {
		return contextDependent;
	}

	public void setContextDependent(String contextDependent) {
		this.contextDependent = contextDependent;
	}

	public String getGroup() {
		return group;
	}

	public void setGroup(String group) {
		this.group = group;
	}

	public String getScaleType() {
		return scaleType;
	}

	public void setScaleType(String scaleType) {
		this.scaleType = scaleType;
	}

	public String getReplaces() {
		return replaces;
	}

	public void setReplaces(String replaces) {
		this.replaces = replaces;
	}

	public String getReplacedBy() {
		return replacedBy;
	}

	public void setReplacedBy(String replacedBy) {
		this.replacedBy = replacedBy;
	}

	public String getEffectiveFrom() {
		return effectiveFrom;
	}

	public void setEffectiveFrom(String effectiveFrom) {
		this.effectiveFrom = effectiveFrom;
	}

	public String getEffectiveTo() {
		return effectiveTo;
	}

	public void setEffectiveTo(String effectiveTo) {
		this.effectiveTo = effectiveTo;
	}

	public String getActive() {
		return active;
	}

	public void setActive(String active) {
		this.active = active;
	}

	public String getCurrentVersion() {
		return currentVersion;
	}

	public void setCurrentVersion(String currentVersion) {
		this.currentVersion = currentVersion;
	}

	@Override
	public boolean isHighUsage() {
		return false;
	}

	@Override
	public boolean isHighestUsage() {
		return false;
	}

	@Override
	public String getDisplayName() {
		return fullDefinition;
	}

	@Override
	protected String[] getCommonColumns() {
		return new String[] {shortDefinition};
	}

}
