/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.service;

import com.b2international.snowowl.rest.service.IComponentService;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponent;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponentInput;
import com.b2international.snowowl.rest.snomed.domain.ISnomedComponentUpdate;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedComponentService<C extends ISnomedComponentInput, R extends ISnomedComponent, U extends ISnomedComponentUpdate> 
	extends IComponentService<C, R, U> {
	// Empty interface body
}
