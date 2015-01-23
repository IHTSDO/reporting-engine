/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.service;

import java.util.Map;

import com.b2international.snowowl.rest.domain.IComponentList;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConcept;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConceptInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedConceptUpdate;
import com.b2international.snowowl.rest.snomed.domain.SearchKind;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedConceptService extends ISnomedComponentService<ISnomedConceptInput, ISnomedConcept, ISnomedConceptUpdate> {

	IComponentList<ISnomedConcept> getAllConcepts(String version, String taskId, int offset, int limit);

	IComponentList<ISnomedConcept> search(String version, String taskId, Map<SearchKind, String> queryParams, int offset, int limit);
}
