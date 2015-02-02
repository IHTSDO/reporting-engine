/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import com.b2international.snowowlmod.rest.snomed.impl.domain.SnomedRelationshipUpdate;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedRelationshipRestUpdate extends AbstractSnomedComponentRestUpdate<SnomedRelationshipUpdate> {

	@Override
	protected SnomedRelationshipUpdate createComponentUpdate() {
		return new SnomedRelationshipUpdate();
	}
}
