/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.service;

import java.io.File;

import com.b2international.snowowl.rest.snomed.domain.ISnomedExportConfiguration;

/**
 * Representation of an export service for the SNOMEd&nbsp;CT ontology.
 * This service is responsible for generating RF2 release format from the
 * state of the underlying SNOMED&nbsp;CT ontology.
 * @author Akos Kitta
 *
 */
public interface ISnomedExportService {

	/**
	 * Generates an RF2 release format by exporting the state of the SNOMED&nbsp;CT ontology
	 * based on the export configuration argument.
	 * @param configuration the configuration for the RF2 export.
	 * @return the RF2 release format export file.
	 */
	File export(final ISnomedExportConfiguration configuration);
	
	
}
