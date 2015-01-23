/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.exception;

/**
 * Class indicating that cause of a failed SNOMED&nbsp;CT RF2 import process.
 * @author akitta
 *
 */
public class SnomedImportException extends RuntimeException {

	private static final long serialVersionUID = 9119450317641232155L;

	/**
	 * Creates a new exception instance with the given message.
	 * @param message the message of the exception.
	 */
	public SnomedImportException(final String message) {
		super(message);
	}
	
}
