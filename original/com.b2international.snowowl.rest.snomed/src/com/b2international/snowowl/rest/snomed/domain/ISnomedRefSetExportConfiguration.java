/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

/**
 * Representation of a SNOMED&nbsp;CT reference set export configuration.
 * @author Akos Kitta
 * @see ISnomedExportConfiguration
 *
 */
public interface ISnomedRefSetExportConfiguration extends ISnomedExportConfiguration {

	/**
	 * Returns with the SNOMED&nbsp;CT identifier concept ID of the reference set 
	 * that has to be exported into RF2 release format.
	 * @return the identifier concept ID of the reference set.
	 */
	String getRefSetId();
	
}
