/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.exception;

import static com.google.common.base.Strings.nullToEmpty;

/**
 * Runtime exception indicating the failure of the SNOMED&nbsp;CT export process.
 * @author Akos Kitta
 *
 */
public class SnomedExportException extends RuntimeException {

	private static final long serialVersionUID = 3399575806529622257L;
	
	/**
	 * Creates a new exception instance with the given message.
	 * @param message the message for the exception to describe the cause
	 * of the problem.
	 */
	public SnomedExportException(final String message) {
		super(nullToEmpty(message));
	}

}
