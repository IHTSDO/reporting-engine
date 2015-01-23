/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain.codesystem;

import java.util.Date;

import com.b2international.snowowl.rest.domain.codesystem.ICodeSystemVersion;

/**
 * @author apeteri
 */
public class CodeSystemVersion implements ICodeSystemVersion {

	private Date importDate;
	private Date effectiveDate;
	private Date lastModificationDate;
	private String description;
	private String version;
	private boolean patched;

	@Override
	public Date getImportDate() {
		return importDate;
	}

	@Override
	public Date getEffectiveDate() {
		return effectiveDate;
	}

	@Override
	public Date getLastModificationDate() {
		return lastModificationDate;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getVersion() {
		return version;
	}

	@Override
	public boolean isPatched() {
		return patched;
	}

	public void setImportDate(final Date importDate) {
		this.importDate = importDate;
	}

	public void setEffectiveDate(final Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}

	public void setLastModificationDate(final Date lastModificationDate) {
		this.lastModificationDate = lastModificationDate;
	}

	public void setDescription(final String description) {
		this.description = description;
	}

	public void setVersion(final String version) {
		this.version = version;
	}

	public void setPatched(final boolean patched) {
		this.patched = patched;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("CodeSystemVersion [importDate=");
		builder.append(importDate);
		builder.append(", effectiveDate=");
		builder.append(effectiveDate);
		builder.append(", lastModificationDate=");
		builder.append(lastModificationDate);
		builder.append(", description=");
		builder.append(description);
		builder.append(", version=");
		builder.append(version);
		builder.append(", patched=");
		builder.append(patched);
		builder.append("]");
		return builder.toString();
	}
}
