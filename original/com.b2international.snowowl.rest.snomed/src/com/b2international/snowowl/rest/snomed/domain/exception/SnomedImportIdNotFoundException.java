/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.exception;

import static com.google.common.base.Preconditions.checkNotNull;

import java.util.UUID;

import com.b2international.snowowl.rest.exception.NotFoundException;

/**
 * Exception for indicating the absence of a SNOMED&nbsp;CT RF2 import identifier.
 * @author Akos Kitta
 *
 */
public class SnomedImportIdNotFoundException extends NotFoundException {

	private static final long serialVersionUID = -256987881675478363L;

	public SnomedImportIdNotFoundException(final UUID importId) {
		super("SNOMED CT import identifier ", checkNotNull(importId, "importId").toString());
	}

}
