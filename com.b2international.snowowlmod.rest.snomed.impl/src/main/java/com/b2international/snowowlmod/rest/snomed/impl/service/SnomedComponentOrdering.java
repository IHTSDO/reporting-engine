/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowlmod.rest.snomed.impl.service;

import com.b2international.snowowl.rest.snomed.domain.ISnomedComponent;
import com.google.common.base.Function;
import com.google.common.collect.Ordering;

/**
 * @author apeteri
 */
public abstract class SnomedComponentOrdering {

	private static final Ordering<? extends ISnomedComponent> ID_ORDERING = Ordering.natural().onResultOf(new Function<ISnomedComponent, String>() {
		@Override
		public String apply(final ISnomedComponent input) {
			return input.getId();
		}
	});
	
	@SuppressWarnings("unchecked")
	public static <T extends ISnomedComponent> Ordering<T> id() {
		return (Ordering<T>) ID_ORDERING;
	}

	private SnomedComponentOrdering() {
		throw new UnsupportedOperationException("This class is not supposed to be instantiated.");
	}
}
