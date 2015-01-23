/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.service;

import java.io.InputStream;
import java.util.UUID;

import com.b2international.snowowl.rest.snomed.domain.ISnomedImportConfiguration;

/**
 * Representation of a SNOMED&nbsp;CT import service for RF2 release archives.
 * @author Akos Kitta
 *
 */
public interface ISnomedRf2ImportService {

	/**
	 * Returns with the previously configured SNOMED&nbsp;CT RF2 import configuration.
	 * @param version the code system version for SNOMED&nbsp;CT.
	 * @param importId the import configuration UUID.
	 * @return the configuration.
	 */
	ISnomedImportConfiguration getImportDetails(final String version, final UUID importId);

	/**
	 * Deletes a previously configured SNOMED&nbsp;CT RF2 import configuration.
	 * @param version the code system version for SNOMED&nbsp;CT.
	 * @param importId the import configuration unique identifier.
	 */
	void deleteImportDetails(final String version, final UUID importId);

	/**
	 * Performs the SNOMED&nbsp;CT RF2 import.
	 * @param version the SNOMED&nbsp;CT version.
	 * @param importId the import configuration unique ID.
	 * @param inputStream the input stream to the RF2 release archive.
	 * @param originalFilename the file name of the release archive.
	 */
	void startImport(final String version, final UUID importId, final InputStream inputStream);

	/**
	 * Creates and registers a new SNOMED&nbsp;CT RF2 import configuration. After the successful registration
	 * it returns with the UUID of configuration.
	 * @param version the SNOMED&nbsp;CT version.
	 * @param configuration the configuration to register.
	 * @return the UUID of the associated configuration.
	 */
	UUID create(final String version, final ISnomedImportConfiguration configuration);

}
