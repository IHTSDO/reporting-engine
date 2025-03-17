package org.ihtsdo.termserver.scripting.pipeline.npu.domain;

import org.ihtsdo.termserver.scripting.pipeline.Part;
import org.ihtsdo.termserver.scripting.pipeline.npu.NpuScriptConstants;

import java.util.List;

public class NpuDetail implements NpuScriptConstants {

	private static final int IDX_NPU_CODE = 0;
	private static final int IDX_REPLACES = 1;
	private static final int IDX_REPLACED_BY = 2;
	private static final int IDX_SPECIALITY = 3;
	private static final int IDX_SCALE = 4;
	private static final int IDX_CONTEXT_DEPENDENT = 5;
	private static final int IDX_GROUPS = 6;
	private static final int IDX_SHORT_DEFINITION = 7;
	private static final int IDX_FULL_DEFINITION = 8;
	private static final int IDX_ELEMENT_CODES_FOR_SYSTEMS = 9;
	private static final int IDX_ELEMENT_CODES_FOR_COMPONENTS = 10;
	private static final int IDX_ELEMENT_CODES_FOR_PROPERTIES = 11;
	private static final int IDX_ELEMENT_CODES_FOR_UNIT = 12;
	private static final int IDX_RELEASE_CENTER = 13;
	private static final int IDX_STATUS = 14;
	private static final int IDX_UPDATED_AT = 15;
	private static final int IDX_ACTIVE_FROM = 16;
	private static final int IDX_EDITOR = 17;
	private static final int IDX_CREATED_AT = 18;
	private static final int IDX_LAST_VERSION_NOTE = 19;
	private static final int IDX_LAST_NOTE = 20;

	private String npuCode;
	private String replaces;
	private String replacedBy;
	private String speciality;
	private String scale;
	private String contextDependent;
	private String groups;
	private String shortDefinition;
	private String fullDefinition;
	private String elementCodesForSystems;
	private String elementCodesForComponents;
	private String elementCodesForProperties;
	private String elementCodesForUnit;
	private String releaseCenter;
	private String status;
	private String updatedAt;
	private String activeFrom;
	private String editor;
	private String createdAt;
	private String lastVersionNote;
	private String lastNote;

	public static NpuDetail parse(String[] columns) {
		NpuDetail npuDetail = new NpuDetail();
		npuDetail.npuCode = columns[IDX_NPU_CODE];
		npuDetail.replaces = columns[IDX_REPLACES];
		npuDetail.replacedBy = columns[IDX_REPLACED_BY];
		npuDetail.speciality = columns[IDX_SPECIALITY];
		npuDetail.scale = columns[IDX_SCALE];
		npuDetail.contextDependent = columns[IDX_CONTEXT_DEPENDENT];
		npuDetail.groups = columns[IDX_GROUPS];
		npuDetail.shortDefinition = columns[IDX_SHORT_DEFINITION];
		npuDetail.fullDefinition = columns[IDX_FULL_DEFINITION];
		npuDetail.elementCodesForSystems = columns[IDX_ELEMENT_CODES_FOR_SYSTEMS];
		npuDetail.elementCodesForComponents = columns[IDX_ELEMENT_CODES_FOR_COMPONENTS];
		npuDetail.elementCodesForProperties = columns[IDX_ELEMENT_CODES_FOR_PROPERTIES];
		npuDetail.elementCodesForUnit = columns[IDX_ELEMENT_CODES_FOR_UNIT];
		npuDetail.releaseCenter = columns[IDX_RELEASE_CENTER];
		npuDetail.status = columns[IDX_STATUS];
		npuDetail.updatedAt = columns[IDX_UPDATED_AT];
		npuDetail.activeFrom = columns[IDX_ACTIVE_FROM];
		npuDetail.editor = columns[IDX_EDITOR];
		npuDetail.createdAt = columns[IDX_CREATED_AT];
		npuDetail.lastVersionNote = columns[IDX_LAST_VERSION_NOTE];
		npuDetail.lastNote = columns[IDX_LAST_NOTE];
		return npuDetail;
	}

	// Getters and Setters
	public String getNpuCode() {
		return npuCode;
	}

	public void setNpuCode(String npuCode) {
		this.npuCode = npuCode;
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

	public String getSpeciality() {
		return speciality;
	}

	public void setSpeciality(String speciality) {
		this.speciality = speciality;
	}

	public String getScale() {
		return scale;
	}

	public void setScale(String scale) {
		this.scale = scale;
	}

	public String getContextDependent() {
		return contextDependent;
	}

	public void setContextDependent(String contextDependent) {
		this.contextDependent = contextDependent;
	}

	public String getGroups() {
		return groups;
	}

	public void setGroups(String groups) {
		this.groups = groups;
	}

	public String getShortDefinition() {
		return shortDefinition;
	}

	public void setShortDefinition(String shortDefinition) {
		this.shortDefinition = shortDefinition;
	}

	public String getFullDefinition() {
		return fullDefinition;
	}

	public void setFullDefinition(String fullDefinition) {
		this.fullDefinition = fullDefinition;
	}

	public String getElementCodesForSystems() {
		return elementCodesForSystems;
	}

	public void setElementCodesForSystems(String elementCodesForSystems) {
		this.elementCodesForSystems = elementCodesForSystems;
	}

	public String getElementCodesForComponents() {
		return elementCodesForComponents;
	}

	public void setElementCodesForComponents(String elementCodesForComponents) {
		this.elementCodesForComponents = elementCodesForComponents;
	}

	public String getElementCodesForProperties() {
		return elementCodesForProperties;
	}

	public void setElementCodesForProperties(String elementCodesForProperties) {
		this.elementCodesForProperties = elementCodesForProperties;
	}

	public String getElementCodesForUnit() {
		return elementCodesForUnit;
	}

	public void setElementCodesForUnit(String elementCodesForUnit) {
		this.elementCodesForUnit = elementCodesForUnit;
	}

	public String getReleaseCenter() {
		return releaseCenter;
	}

	public void setReleaseCenter(String releaseCenter) {
		this.releaseCenter = releaseCenter;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getUpdatedAt() {
		return updatedAt;
	}

	public void setUpdatedAt(String updatedAt) {
		this.updatedAt = updatedAt;
	}

	public String getActiveFrom() {
		return activeFrom;
	}

	public void setActiveFrom(String activeFrom) {
		this.activeFrom = activeFrom;
	}

	public String getEditor() {
		return editor;
	}

	public void setEditor(String editor) {
		this.editor = editor;
	}

	public String getCreatedAt() {
		return createdAt;
	}

	public void setCreatedAt(String createdAt) {
		this.createdAt = createdAt;
	}

	public String getLastVersionNote() {
		return lastVersionNote;
	}

	public void setLastVersionNote(String lastVersionNote) {
		this.lastVersionNote = lastVersionNote;
	}

	public String getLastNote() {
		return lastNote;
	}

	public void setLastNote(String lastNote) {
		this.lastNote = lastNote;
	}

	public List<Part> getParts(NpuConcept npuConcept) {
		return List.of(
			new Part(elementCodesForComponents, NPU_PART_COMPONENT, npuConcept.getComponent()),
			new Part(elementCodesForSystems, NPU_PART_SYSTEM, npuConcept.getSystem()),
			new Part(elementCodesForProperties, NPU_PART_PROPERTY, npuConcept.getProperty()),
			new Part(scale, NPU_PART_SCALE, npuConcept.getScaleType()),
			new Part(elementCodesForUnit, NPU_PART_UNIT, npuConcept.getUnit())
		);
	}
}