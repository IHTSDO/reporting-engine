/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain;

import java.util.Date;

import com.b2international.snowowl.rest.domain.IComponent;

/**
 * TODO document
 * @author Andras Peteri
 */
public interface ISnomedComponent extends IComponent {

	/**
	 * TODO document
	 * @return
	 */
	boolean isActive();

	/**
	 * TODO document
	 * @return
	 */
	Date getEffectiveTime();

	/**
	 * TODO document
	 * @return
	 */
	String getModuleId();
}
