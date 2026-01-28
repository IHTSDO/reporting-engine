package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.RF2Constants;

import java.util.Arrays;
import java.util.List;

public class HistoricData implements RF2Constants {
	private long conceptId;
	private String fsn;
	private boolean isActive;
	private boolean isSD;
	private String hierarchy;
	private boolean isIP;
	private boolean hasSdDescendant;
	private boolean hasSdAncestor;
	private int hashCode;
	private String moduleId;
	private List<String> relIds;
	private List<String> descIds;
	private List<String> axiomIds;
	private List<String> langRefsetIds;
	private List<String> inactivationIds;
	private List<String> histAssocIds;
	private List<String> relIdsInact;
	private List<String> descIdsInact;
	private List<String> axiomIdsInact;
	private List<String> langRefsetIdsInact;
	private List<String> inactivationIdsInact;
	private List<String> histAssocIdsInact;
	private boolean hasAttributes;
	private List<String> descHistAssocIds;
	private List<String> descHistAssocIdsInact;
	private List<String> descInactivationIds;
	private List<String> descInactivationIdsInact;
	private List<String> histAssocTargets;
	private List<String> annotationIds;
	private List<String> annotationIdsInact;

	public long getConceptId() {
		return conceptId;
	}

	public void setConceptId(long conceptId) {
		this.conceptId = conceptId;
	}

	public String getFsn() {
		return fsn;
	}

	public void setFsn(String fsn) {
		this.fsn = fsn;
	}

	public boolean isActive() {
		return isActive;
	}

	public void setActive(boolean isActive) {
		this.isActive = isActive;
	}

	public boolean isSD() {
		return isSD;
	}

	public void setSD(boolean isSD) {
		this.isSD = isSD;
	}

	public String getHierarchy() {
		return hierarchy;
	}

	public void setHierarchy(String hierarchy) {
		this.hierarchy = hierarchy;
	}

	public boolean isIP() {
		return isIP;
	}

	public void setIP(boolean isIP) {
		this.isIP = isIP;
	}

	public boolean hasSdDescendant() {
		return hasSdDescendant;
	}

	public void setHasSdDescendant(boolean hasSdDescendant) {
		this.hasSdDescendant = hasSdDescendant;
	}

	public boolean hasSdAncestor() {
		return hasSdAncestor;
	}

	public void setHasSdAncestor(boolean hasSdAncestor) {
		this.hasSdAncestor = hasSdAncestor;
	}

	public void setHashCode(int hashCode) {
		this.hashCode = hashCode;
	}

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
	}

	public List<String> getRelIds() {
		return relIds;
	}

	public void setRelIds(List<String> relIds) {
		this.relIds = relIds;
	}

	public List<String> getDescIds() {
		return descIds;
	}

	public void setDescIds(List<String> descIds) {
		this.descIds = descIds;
	}

	public List<String> getAxiomIds() {
		return axiomIds;
	}

	public void setAxiomIds(List<String> axiomIds) {
		this.axiomIds = axiomIds;
	}

	public List<String> getLangRefsetIds() {
		return langRefsetIds;
	}

	public void setLangRefsetIds(List<String> langRefsetIds) {
		this.langRefsetIds = langRefsetIds;
	}

	public List<String> getInactivationIds() {
		return inactivationIds;
	}

	public void setInactivationIds(List<String> inactivationIds) {
		this.inactivationIds = inactivationIds;
	}

	public List<String> getHistAssocIds() {
		return histAssocIds;
	}

	public void setHistAssocIds(List<String> histAssocIds) {
		this.histAssocIds = histAssocIds;
	}

	public List<String> getRelIdsInact() {
		return relIdsInact;
	}

	public void setRelIdsInact(List<String> relIdsInact) {
		this.relIdsInact = relIdsInact;
	}

	public List<String> getDescIdsInact() {
		return descIdsInact;
	}

	public void setDescIdsInact(List<String> descIdsInact) {
		this.descIdsInact = descIdsInact;
	}

	public List<String> getAxiomIdsInact() {
		return axiomIdsInact;
	}

	public void setAxiomIdsInact(List<String> axiomIdsInact) {
		this.axiomIdsInact = axiomIdsInact;
	}

	public List<String> getLangRefsetIdsInact() {
		return langRefsetIdsInact;
	}

	public void setLangRefsetIdsInact(List<String> langRefsetIdsInact) {
		this.langRefsetIdsInact = langRefsetIdsInact;
	}

	public List<String> getInactivationIdsInact() {
		return inactivationIdsInact;
	}

	public void setInactivationIdsInact(List<String> inactivationIdsInact) {
		this.inactivationIdsInact = inactivationIdsInact;
	}

	public List<String> getHistAssocIdsInact() {
		return histAssocIdsInact;
	}

	public void setHistAssocIdsInact(List<String> histAssocIdsInact) {
		this.histAssocIdsInact = histAssocIdsInact;
	}

	public boolean hasAttributes() {
		return hasAttributes;
	}

	public void setHasAttributes(boolean hasAttributes) {
		this.hasAttributes = hasAttributes;
	}

	public List<String> getDescHistAssocIds() {
		return descHistAssocIds;
	}

	public void setDescHistAssocIds(List<String> descHistAssocIds) {
		this.descHistAssocIds = descHistAssocIds;
	}

	public List<String> getDescHistAssocIdsInact() {
		return descHistAssocIdsInact;
	}

	public void setDescHistAssocIdsInact(List<String> descHistAssocIdsInact) {
		this.descHistAssocIdsInact = descHistAssocIdsInact;
	}

	public List<String> getDescInactivationIds() {
		return descInactivationIds;
	}

	public void setDescInactivationIds(List<String> descInactivationIds) {
		this.descInactivationIds = descInactivationIds;
	}

	public List<String> getDescInactivationIdsInact() {
		return descInactivationIdsInact;
	}

	public void setDescInactivationIdsInact(List<String> descInactivationIdsInact) {
		this.descInactivationIdsInact = descInactivationIdsInact;
	}

	public List<String> getHistAssocTargets() {
		return histAssocTargets;
	}

	public void setHistAssocTargets(List<String> histAssocTargets) {
		this.histAssocTargets = histAssocTargets;
	}

	public List<String> getAnnotationIds() {
		return annotationIds;
	}

	public void setAnnotationIds(List<String> annotationIds) {
		this.annotationIds = annotationIds;
	}

	public List<String> getAnnotationIdsInact() {
		return annotationIdsInact;
	}

	public void setAnnotationIdsInact(List<String> annotationIdsInact) {
		this.annotationIdsInact = annotationIdsInact;
	}

	@Override
	public int hashCode() {
		return hashCode;
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof HistoricData datum) {
			return this.conceptId == datum.conceptId;
		}
		return false;
	}

	public static HistoricData fromLine(String line, boolean minimalSet) {
		int idx = 0;
		HistoricData datum = new HistoricData();
		String[] lineItems = line.split(TAB, -1);
		datum.conceptId = Long.parseLong(lineItems[idx]);
		datum.hashCode = Long.hashCode(datum.conceptId);
		datum.fsn = lineItems[++idx];
		datum.isActive = lineItems[++idx].equals("Y");
		if (!minimalSet) {
			datum.isSD = lineItems[++idx].equals("SD");
			datum.hierarchy = lineItems[++idx];
			datum.isIP = lineItems[++idx].equals("Y");
			datum.hasSdAncestor = lineItems[++idx].equals("Y");
			datum.hasSdDescendant = lineItems[++idx].equals("Y");
			datum.relIds = Arrays.asList(lineItems[++idx].split(","));
			datum.relIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.descIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.axiomIds = Arrays.asList(lineItems[++idx].split(","));
			datum.axiomIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.langRefsetIds = Arrays.asList(lineItems[++idx].split(","));
			datum.langRefsetIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.inactivationIds = Arrays.asList(lineItems[++idx].split(","));
			datum.inactivationIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocIds = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.moduleId = lineItems[++idx];
			datum.hasAttributes = lineItems[++idx].equals("Y");
			datum.descHistAssocIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descHistAssocIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.descInactivationIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descInactivationIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocTargets = Arrays.asList(lineItems[++idx].split(","));
			datum.annotationIds = Arrays.asList(lineItems[++idx].split(","));
			datum.annotationIdsInact = Arrays.asList(lineItems[++idx].split(","));
		} else {
			idx++;
			datum.hierarchy = lineItems[++idx];
			idx += 21;
			datum.histAssocTargets = Arrays.asList(lineItems[++idx].split(","));
			if (datum.histAssocTargets.size() == 1 && datum.histAssocTargets.get(0).equals("")) {
				datum.histAssocTargets = null;  //Save the memory!
			}
		}
		return datum;
	}
}
