package com.b2international.snowowlmod.rest.snomed.impl.domain;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.Collection;
import java.util.Collections;
import java.util.Date;

import com.b2international.snowowl.rest.snomed.domain.ISnomedExportConfiguration;
import com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType;

/**
 * @author mczotter
 * @since 3.7
 */
public class SnomedExportConfiguration implements ISnomedExportConfiguration {

	private Rf2ReleaseType type;
	private String version;
	private String namespaceId;
	private Collection<String> moduleIds;
	private Date deltaExportStartEffectiveTime;
	private Date deltaExportEndEffectiveTime;

	public SnomedExportConfiguration(Rf2ReleaseType type, String version, String namespaceId, Collection<String> moduleIds) {
		this.type = checkNotNull(type, "type");
		this.version = checkNotNull(version, "version");
		this.namespaceId = checkNotNull(namespaceId, "namespaceId");
		this.moduleIds = moduleIds == null ? Collections.<String>emptySet() : moduleIds;
	}
	
	@Override
	public Rf2ReleaseType getRf2ReleaseType() {
		return type;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public String getTaskId() {
		return null; // XXX Exporting on tasks are not allowed
	}

	@Override
	public Date getDeltaExportStartEffectiveTime() {
		return deltaExportStartEffectiveTime;
	}
	
	public void setDeltaExportStartEffectiveTime(Date deltaExportStartEffectiveTime) {
		this.deltaExportStartEffectiveTime = deltaExportStartEffectiveTime;
	}
	
	@Override
	public Date getDeltaExportEndEffectiveTime() {
		return deltaExportEndEffectiveTime;
	}
	
	public void setDeltaExportEndEffectiveTime(Date deltaExportEndEffectiveTime) {
		this.deltaExportEndEffectiveTime = deltaExportEndEffectiveTime;
	}

	@Override
	public String getNamespaceId() {
		return namespaceId;
	}

	@Override
	public Collection<String> getModuleDependencyIds() {
		return moduleIds;
	}

}
