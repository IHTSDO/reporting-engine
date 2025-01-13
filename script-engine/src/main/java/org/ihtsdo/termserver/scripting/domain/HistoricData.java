package org.ihtsdo.termserver.scripting.domain;

import org.ihtsdo.otf.RF2Constants;

import java.util.Arrays;
import java.util.List;

public class HistoricData implements RF2Constants {
	private long conceptId;
	private int hashCode;
	private String fsn;
	private String moduleId;
	private boolean isActive;
	private boolean isSD;
	private String hierarchy;
	private boolean isIP;
	private boolean hasAttributes;
	private boolean hasSdDescendant;
	private boolean hasSdAncestor;
	private List<String> histAssocTargets;
	private List<String> relIds;
	private List<String> relIdsInact;
	private List<String> relIdsOutOfScope;
	private List<String> descIds;
	private List<String> descIdsInact;
	private List<String> descIdsOutOfScope;
	private List<String> axiomIds;
	private List<String> axiomIdsInact;
	private List<String> axiomIdsOutOfScope;
	private List<String> langRefsetIds;
	private List<String> langRefsetIdsInact;
	private List<String> langRefsetIdsOutOfScope;
	private List<String> inactivationIds;
	private List<String> inactivationIdsInact;
	private List<String> inactivationIdsOutOfScope;
	private List<String> histAssocIds;
	private List<String> histAssocIdsInact;
	private List<String> histAssocIdsOutOfScope;
	private List<String> descHistAssocIds;
	private List<String> descHistAssocIdsInact;
	private List<String> descHistAssocIdsOutOfScope;
	private List<String> descInactivationIds;
	private List<String> descInactivationIdsInact;
	private List<String> descInactivationIdsOutOfScope;
	private List<String> anatomyStructureAssocIds;
	private List<String> anatomyStructureAssocIdsInact;
	private List<String> anatomyStructureAssocIdsOutOfScope;

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

	public String getModuleId() {
		return moduleId;
	}

	public void setModuleId(String moduleId) {
		this.moduleId = moduleId;
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

	public boolean hasAttributes() {
		return hasAttributes;
	}

	public void setHasAttributes(boolean hasAttributes) {
		this.hasAttributes = hasAttributes;
	}

	public List<String> getHistAssocTargets() {
		return histAssocTargets;
	}

	public void setHistAssocTargets(List<String> histAssocTargets) {
		this.histAssocTargets = histAssocTargets;
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

	public List<String> getRelIdsOutOfScope() {
		return relIdsOutOfScope;
	}

	public void setRelIdsOutOfScope(List<String> relIdsOutOfScope) {
		this.relIdsOutOfScope = relIdsOutOfScope;
	}

	public List<String> getDescIdsOutOfScope() {
		return descIdsOutOfScope;
	}

	public void setDescIdsOutOfScope(List<String> descIdsOutOfScope) {
		this.descIdsOutOfScope = descIdsOutOfScope;
	}

	public List<String> getAxiomIdsOutOfScope() {
		return axiomIdsOutOfScope;
	}

	public void setAxiomIdsOutOfScope(List<String> axiomIdsOutOfScope) {
		this.axiomIdsOutOfScope = axiomIdsOutOfScope;
	}

	public List<String> getLangRefsetIdsOutOfScope() {
		return langRefsetIdsOutOfScope;
	}

	public void setLangRefsetIdsOutOfScope(List<String> langRefsetIdsOutOfScope) {
		this.langRefsetIdsOutOfScope = langRefsetIdsOutOfScope;
	}

	public List<String> getInactivationIdsOutOfScope() {
		return inactivationIdsOutOfScope;
	}

	public void setInactivationIdsOutOfScope(List<String> inactivationIdsOutOfScope) {
		this.inactivationIdsOutOfScope = inactivationIdsOutOfScope;
	}

	public List<String> getHistAssocIdsOutOfScope() {
		return histAssocIdsOutOfScope;
	}

	public void setHistAssocIdsOutOfScope(List<String> histAssocIdsOutOfScope) {
		this.histAssocIdsOutOfScope = histAssocIdsOutOfScope;
	}

	public List<String> getDescHistAssocIdsOutOfScope() {
		return descHistAssocIdsOutOfScope;
	}

	public void setDescHistAssocIdsOutOfScope(List<String> descHistAssocIdsOutOfScope) {
		this.descHistAssocIdsOutOfScope = descHistAssocIdsOutOfScope;
	}

	public List<String> getDescInactivationIdsOutOfScope() {
		return descInactivationIdsOutOfScope;
	}

	public void setDescInactivationIdsOutOfScope(List<String> descInactivationIdsOutOfScope) {
		this.descInactivationIdsOutOfScope = descInactivationIdsOutOfScope;
	}

	public List<String> getAnatomyStructureAssocIds() {
		return anatomyStructureAssocIds;
	}

	public void setAnatomyStructureAssocIds(List<String> anatomyStructureAssocIds) {
		this.anatomyStructureAssocIds = anatomyStructureAssocIds;
	}

	public List<String> getAnatomyStructureAssocIdsInact() {
		return anatomyStructureAssocIdsInact;
	}

	public void setAnatomyStructureAssocIdsInact(List<String> anatomyStructureAssocIdsInact) {
		this.anatomyStructureAssocIdsInact = anatomyStructureAssocIdsInact;
	}

	public List<String> getAnatomyStructureAssocIdsOutOfScope() {
		return anatomyStructureAssocIdsOutOfScope;
	}

	public void setAnatomyStructureAssocIdsOutOfScope(List<String> anatomyStructureAssocIdsOutOfScope) {
		this.anatomyStructureAssocIdsOutOfScope = anatomyStructureAssocIdsOutOfScope;
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
		datum.moduleId = lineItems[++idx];
		datum.isActive = lineItems[++idx].equals("Y");
		datum.isSD = lineItems[++idx].equals("SD");
		datum.hierarchy = lineItems[++idx];
		datum.isIP = lineItems[++idx].equals("Y");
		datum.hasAttributes = lineItems[++idx].equals("Y");
		datum.hasSdDescendant = lineItems[++idx].equals("Y");
		datum.hasSdAncestor = lineItems[++idx].equals("Y");
		datum.histAssocTargets = Arrays.asList(lineItems[++idx].split(","));
		if (!minimalSet) {
			datum.relIds = Arrays.asList(lineItems[++idx].split(","));
			datum.relIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.relIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));

			datum.descIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.descIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));

			datum.axiomIds = Arrays.asList(lineItems[++idx].split(","));
			datum.axiomIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.axiomIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));

			datum.langRefsetIds = Arrays.asList(lineItems[++idx].split(","));
			datum.langRefsetIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.langRefsetIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));

			datum.inactivationIds = Arrays.asList(lineItems[++idx].split(","));
			datum.inactivationIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.inactivationIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));

			datum.histAssocIds = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.histAssocIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));

			datum.descHistAssocIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descHistAssocIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.descHistAssocIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));

			datum.descInactivationIds = Arrays.asList(lineItems[++idx].split(","));
			datum.descInactivationIdsInact = Arrays.asList(lineItems[++idx].split(","));
			datum.descInactivationIdsOutOfScope = Arrays.asList(lineItems[++idx].split(","));
		} else {
			if (datum.histAssocTargets.size() == 1 && datum.histAssocTargets.get(0).equals("")) {
				datum.histAssocTargets = null;  //Save the memory!
			}
		}
		return datum;
	}
}
