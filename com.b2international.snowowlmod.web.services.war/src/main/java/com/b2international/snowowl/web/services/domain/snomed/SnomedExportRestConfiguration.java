package com.b2international.snowowl.web.services.domain.snomed;

import java.util.Collection;
import java.util.Date;

import com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;

/**
 * @author mczotter
 * @since 1.0
 */
public class SnomedExportRestConfiguration {

	private Rf2ReleaseType type;
	private Collection<String> moduleIds;
	private Date deltaStartEffectiveTime;
	private Date deltaEndEffectiveTime;
	private String namespaceId;

	/**
	 * Returns with the RF2 release type of the current export configuration.
	 * @return the desired RF2 release type.
	 */
	public Rf2ReleaseType getType() {
		return type;
	}
	
	public void setType(Rf2ReleaseType type) {
		this.type = type;
	}
	
	/**
	 * Returns with the delta export start effective time.
	 * <br>Can be {@code null} even 
	 * if the {@link Rf2ReleaseType release type} is {@link Rf2ReleaseType#DELTA delta}.
	 */
	@JsonFormat(shape=Shape.STRING, pattern="yyyyMMdd")
	public Date getDeltaStartEffectiveTime() {
		return deltaStartEffectiveTime;
	}
	
	public void setDeltaStartEffectiveTime(Date deltaStartEffectiveTime) {
		this.deltaStartEffectiveTime = deltaStartEffectiveTime;
	}

	/**
	 * Returns with the delta export end effective time.
	 * <br>May return with {@code null} even 
	 * if the {@link Rf2ReleaseType release type} is {@link Rf2ReleaseType#DELTA delta}.
	 */
	@JsonFormat(shape=Shape.STRING, pattern="yyyyMMdd")
	public Date getDeltaEndEffectiveTime() {
		return deltaEndEffectiveTime;
	}
	
	public void setDeltaEndEffectiveTime(Date deltaEndEffectiveTime) {
		this.deltaEndEffectiveTime = deltaEndEffectiveTime;
	}
	
	/**
	 * Returns with the namespace ID.
	 * <p>The namespace ID will be used when generating the folder structure 
	 * for the RF2 release format export.
	 * @return the namespace ID.
	 */
	public String getNamespaceId() {
		return namespaceId;
	}
	
	public void setNamespaceId(String namespaceId) {
		this.namespaceId = namespaceId;
	}
	
	/**
	 * Returns with a collection of SNOMED&nbsp;CT module concept IDs.
	 * <p>This collection of module IDs will define which components will be included in the export.
	 * Components having a module that is not included in the returning set will be excluded from 
	 * the export result.
	 * @return a collection of module dependency IDs.
	 */
	public Collection<String> getModuleIds() {
		return moduleIds;
	}
	
	public void setModuleIds(Collection<String> moduleIds) {
		this.moduleIds = moduleIds;
	}
	
}
