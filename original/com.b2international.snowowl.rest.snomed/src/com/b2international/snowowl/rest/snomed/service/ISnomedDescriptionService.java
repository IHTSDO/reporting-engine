/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.service;

import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import com.b2international.snowowl.rest.domain.IComponentRef;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescription;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescriptionInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedDescriptionUpdate;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedDescriptionService extends ISnomedComponentService<ISnomedDescriptionInput, ISnomedDescription, ISnomedDescriptionUpdate> {

	/**
	 * TODO document
	 * @param conceptRef
	 * @return
	 */
	List<ISnomedDescription> readConceptDescriptions(IComponentRef conceptRef);

	/**
	 * TODO document
	 * @param conceptRef
	 * @param locales
	 * @return
	 */
	ISnomedDescription getPreferredTerm(IComponentRef conceptRef, Enumeration<Locale> locales);
}
