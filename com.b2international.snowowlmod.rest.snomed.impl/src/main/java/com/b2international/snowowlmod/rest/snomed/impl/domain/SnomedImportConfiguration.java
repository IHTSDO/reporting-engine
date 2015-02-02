/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.domain;

import com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration;
import com.b2international.snowowl.rest.snomed.domain.Rf2ReleaseType;

import java.util.Date;

import static com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration.ImportStatus.WAITING_FOR_FILE;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Implementation of a {@link ISnomedImportConfiguration SNOMED&nbsp;CT import configuration}.
 * @author Akos Kitta
 *
 */
public class SnomedImportConfiguration implements ISnomedImportConfiguration {

	private final Rf2ReleaseType rf2ReleaseType;
	private final String version;
	private final String taskId;
	private final String languageRefSetId;
	private final boolean createVersion;
	private ImportStatus importStatus = WAITING_FOR_FILE;
	private Date startDate;
	private Date completionDate;

	/**
	 * Creates a new import configuration instance.
	 * @param rf2ReleaseType the RF2 release type.
	 * @param version the version where the import has to be performed.
	 * @param languageRefSetId the language reference set identifier concept ID for the preferred language. 
	 * @param createVersion boolean indicating whether a new version has to be created for each individual 
	 * effective times. Has no effect if the RF2 release type in *NOT* full.
	 */
	public SnomedImportConfiguration(final Rf2ReleaseType rf2ReleaseType, final String version,
			final String languageRefSetId, final boolean createVersion) {

		this.rf2ReleaseType = checkNotNull(rf2ReleaseType, "rf2ReleaseType");
		this.version = checkNotNull(version, "version");
		this.taskId = null;
		this.languageRefSetId = checkNotNull(languageRefSetId, "languageRefSetId");
		this.createVersion = checkNotNull(createVersion, "createVersion");
	}

	/**
	 * Creates a new import configuration instance.
	 * @param rf2ReleaseType the RF2 release type.
	 * @param version the version where the import has to be performed.
	 * @param taskId the version where the import has to be performed.
	 * @param languageRefSetId the language reference set identifier concept ID for the preferred language.
	 * @param createVersion boolean indicating whether a new version has to be created for each individual
	 * effective times. Has no effect if the RF2 release type in *NOT* full.
	 */
	public SnomedImportConfiguration(final Rf2ReleaseType rf2ReleaseType, final String version,
			final String taskId, final String languageRefSetId, final boolean createVersion) {

		this.rf2ReleaseType = checkNotNull(rf2ReleaseType, "rf2ReleaseType");
		this.version = checkNotNull(version, "version");
		this.taskId = taskId;
		this.languageRefSetId = checkNotNull(languageRefSetId, "languageRefSetId");
		this.createVersion = checkNotNull(createVersion, "createVersion");
	}

	@Override
	public Rf2ReleaseType getRf2ReleaseType() {
		return rf2ReleaseType;
	}

	@Override
	public String getVersion() {
		return version;
	}

	public String getTaskId() {
		return taskId;
	}

	@Override
	public String getLanguageRefSetId() {
		return languageRefSetId;
	}

	@Override
	public boolean shouldCreateVersion() {
		return createVersion;
	}

	@Override
	public ImportStatus getStatus() {
		return importStatus;
	}
	
	@Override
	public Date getStartDate() {
		return startDate;
	}
	
	@Override
	public Date getCompletionDate() {
		return completionDate;
	}
	
	/**
	 * Sets the status to the desired value.
	 * @param importStatus the import status to set.
	 */
	public void setStatus(final ImportStatus importStatus) {
		if (ImportStatus.RUNNING == importStatus) {
			this.startDate = new Date();
		} else if (ImportStatus.COMPLETED == importStatus || ImportStatus.FAILED == importStatus) {
			this.completionDate = new Date();
		}
		this.importStatus = checkNotNull(importStatus, "importStatus");
	}

}
