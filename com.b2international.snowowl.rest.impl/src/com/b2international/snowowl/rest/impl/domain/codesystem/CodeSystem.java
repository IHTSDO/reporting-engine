/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain.codesystem;

import com.b2international.snowowl.rest.domain.codesystem.ICodeSystem;

/**
 * @author apeteri
 */
public class CodeSystem implements ICodeSystem {

	private String oid;
	private String name;
	private String shortName;
	private String organizationLink;
	private String primaryLanguage;
	private String citation;

	@Override
	public String getOid() {
		return oid;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getShortName() {
		return shortName;
	}

	@Override
	public String getOrganizationLink() {
		return organizationLink;
	}

	@Override
	public String getPrimaryLanguage() {
		return primaryLanguage;
	}

	@Override
	public String getCitation() {
		return citation;
	}

	public void setOid(final String oid) {
		this.oid = oid;
	}

	public void setName(final String name) {
		this.name = name;
	}

	public void setShortName(final String shortName) {
		this.shortName = shortName;
	}

	public void setOrganizationLink(final String organizationLink) {
		this.organizationLink = organizationLink;
	}

	public void setPrimaryLanguage(final String primaryLanguage) {
		this.primaryLanguage = primaryLanguage;
	}

	public void setCitation(final String citation) {
		this.citation = citation;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("CodeSystem [oid=");
		builder.append(oid);
		builder.append(", name=");
		builder.append(name);
		builder.append(", shortName=");
		builder.append(shortName);
		builder.append(", organizationLink=");
		builder.append(organizationLink);
		builder.append(", primaryLanguage=");
		builder.append(primaryLanguage);
		builder.append(", citation=");
		builder.append(citation);
		builder.append("]");
		return builder.toString();
	}
}
