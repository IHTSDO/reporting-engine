/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.service;

import com.b2international.snowowl.rest.impl.service.AbstractTaskServiceImpl;
import com.b2international.snowowl.rest.snomed.service.ISnomedTaskService;
import com.b2international.snowowl.snomed.datastore.SnomedDatastoreActivator;
import com.b2international.snowowl.snomed.datastore.SnomedGeneralAuthoringTaskContext;

/**
 * @author apeteri
 */
public class SnomedTaskServiceImpl extends AbstractTaskServiceImpl implements ISnomedTaskService {

	public SnomedTaskServiceImpl() {
		super(SnomedDatastoreActivator.REPOSITORY_UUID, SnomedGeneralAuthoringTaskContext.ID);
	}
}
