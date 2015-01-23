/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import java.util.Map;

import javax.annotation.Nullable;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedDescription extends ISnomedComponent {

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
	 * TODO document
	 * @return
	 */
	Map<String, Acceptability> getAcceptabilityMap();

	/**
	 * Returns with the inactivation indicator (if any) of the description
	 * that can be used to identify the reason why the current description has
	 * been inactivated. 
	 * <p>May return with {@code null} even if the description is 
	 * inactive this means no reason was given for the inactivation.
	 * @return the inactivation reason. Or {@code null} if not available.
	 * @see DescriptionInactivationIndicator
	 */
	@Nullable DescriptionInactivationIndicator getDescriptionInactivationIndicator();
}
