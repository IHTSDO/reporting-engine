/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import java.util.Map;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedDescriptionInput extends ISnomedComponentInput {

	/**
	 * TODO document
	 * @return
	 */
	String getConceptId();
	
	/**
	 * TODO document
	 * @return
	 */
	String getTypeId();

	/**
	 * TODO document
	 * @return
	 */
	String getTerm();

	/**
	 * TODO document
	 * @return
	 */
	String getLanguageCode();

	/**
	 * TODO document
	 * @return
	 */
	CaseSignificance getCaseSignificance();
	
	/**
	 * 
	 * @return
	 */
	Map<String, Acceptability> getAcceptability();
}
