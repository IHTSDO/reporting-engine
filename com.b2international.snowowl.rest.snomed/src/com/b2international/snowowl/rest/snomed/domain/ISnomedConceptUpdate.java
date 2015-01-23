/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import com.google.common.collect.Multimap;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedConceptUpdate extends ISnomedComponentUpdate {

	/**
	 * TODO document
	 * @return
	 */
	DefinitionStatus getDefinitionStatus();

	/**
	 * TODO document
	 * @return
	 */
	SubclassDefinitionStatus getSubclassDefinitionStatus();

	/**
	 * TODO document
	 * @return
	 */
	InactivationIndicator getInactivationIndicator();

	/**
	 * TODO document
	 * @return
	 */
	Multimap<AssociationType, String> getAssociationTargets();
}
