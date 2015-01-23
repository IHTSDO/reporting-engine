/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.exception;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.UUID;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Exception indicating the absence of a SNOMED&nbsp;CT configuration for a particular
 * import identifier.
 * @author akitta
 *
 */
public class SnomedImportConfigurationNotFoundException extends NotFoundException {

	private static final long serialVersionUID = 4991544292465337742L;

	public SnomedImportConfigurationNotFoundException(final UUID importId) {
		super("SNOMED CT import configuration", checkNotNull(importId, "importId").toString());
	}


}
