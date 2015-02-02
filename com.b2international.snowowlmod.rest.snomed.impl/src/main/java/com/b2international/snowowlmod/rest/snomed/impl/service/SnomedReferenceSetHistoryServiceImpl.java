/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.service;

import com.b2international.snowowl.core.api.IBranchPath;
import com.b2international.snowowl.rest.domain.ComponentCategory;
import com.b2international.snowowl.rest.impl.service.AbstractHistoryServiceImpl;
import com.b2international.snowowl.rest.snomed.service.ISnomedReferenceSetHistoryService;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.SnomedRefSetLookupService;

/**
 * @author apeteri
 */
public class SnomedReferenceSetHistoryServiceImpl extends AbstractHistoryServiceImpl implements ISnomedReferenceSetHistoryService {

	public SnomedReferenceSetHistoryServiceImpl() {
		super(SnomedDatastoreActivator.REPOSITORY_UUID, ComponentCategory.SET);
	}

	@Override
	protected long getStorageKey(final IBranchPath branchPath, final String componentId) {
		return new SnomedRefSetLookupService().getStorageKey(branchPath, componentId);
	}
}
