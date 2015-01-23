/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import java.util.Map;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedDescriptionUpdate extends ISnomedComponentUpdate {

	/**
	 * TODO document
	 * @return
	 */
	CaseSignificance getCaseSignificance();

	/**
	 * TODO document
	 * @return
	 */
	Map<String, Acceptability> getAcceptability();
}
