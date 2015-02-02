/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.service;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.impl.service.AbstractHistoryServiceImpl;
import com.b2international.snowowl.rest.snomed.service.ISnomedConceptHistoryService;
import com.b2international.snowowl.snomed.datastore.SnomedConceptLookupService;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;

/**
 * @author apeteri
 */
public class SnomedConceptHistoryServiceImpl extends AbstractHistoryServiceImpl implements ISnomedConceptHistoryService {

	public SnomedConceptHistoryServiceImpl() {
		super(SnomedDatastoreActivator.REPOSITORY_UUID, ComponentCategory.CONCEPT);
	}

	@Override
	protected long getStorageKey(final IBranchPath branchPath, final String conceptId) {
		return new SnomedConceptLookupService().getStorageKey(branchPath, conceptId);
	}
}
