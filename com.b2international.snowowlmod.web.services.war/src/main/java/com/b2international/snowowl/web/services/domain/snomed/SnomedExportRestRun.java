/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import java.util.UUID;

/**
 * @author Andras Peteri
 * @author Mark Czotter
 * @since 1.0
 */
public class SnomedExportRestRun extends SnomedExportRestConfiguration {
	
	private UUID id;

	public UUID getId() {
		return id;
	}
	
	public void setId(UUID id) {
		this.id = id;
	}
	
}
