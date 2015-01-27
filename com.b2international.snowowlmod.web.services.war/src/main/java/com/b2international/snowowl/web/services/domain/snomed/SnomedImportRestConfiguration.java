/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedImportRestConfiguration {

	private Rf2ReleaseType type;
	private Boolean createVersions = Boolean.FALSE;
	private String languageRefSetId;

	public Rf2ReleaseType getType() {
		return type;
	}

	public Boolean getCreateVersions() {
		return createVersions;
	}

	public void setType(final Rf2ReleaseType type) {
		this.type = type;
	}

	public void setCreateVersions(final Boolean createVersions) {
		this.createVersions = createVersions;
	}

	/**
	 * Returns with the language reference set identifier concept ID for the import configuration.
	 * @return the language reference set ID for the preferred language.
	 */
	public String getLanguageRefSetId() {
		return languageRefSetId;
	}

	/**
	 * Sets the language reference set identifier concept ID based on
	 * the language reference set identifier concept ID argument.
	 * @param languageRefSetId the language reference set ID for the preferred language. 
	 */
	public void setLanguageRefSetId(final String languageRefSetId) {
		this.languageRefSetId = languageRefSetId;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder();
		sb.append("SnomedImportRestConfiguration [type=");
		sb.append(type);
		sb.append(", createVersions=");
		sb.append(createVersions);
		sb.append(", languageRefSetId=");
		sb.append(languageRefSetId);
		sb.append("]");
		return sb.toString();
	}

	
	
}
