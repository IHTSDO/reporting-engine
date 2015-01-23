/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.impl.service;

import java.util.Date;

import com.b2international.snowowl.core.util.DateUtils;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponent;
import com.b2international.snowowl.snomed.datastore.index.SnomedIndexEntry;
import com.google.common.base.Function;

/**
 * @author apeteri
 */
public abstract class AbstractSnomedComponentConverter<F extends SnomedIndexEntry, T extends ISnomedComponent> implements Function<F, T> {

	protected Date toEffectiveTime(final long effectiveTimeAsLong) {
		return (DateUtils.UNSET_EFFECTIVE_TIME == effectiveTimeAsLong) ? null : new Date(effectiveTimeAsLong);
	}
}
