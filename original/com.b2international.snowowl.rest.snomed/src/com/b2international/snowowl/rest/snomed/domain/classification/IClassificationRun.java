/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.snomed.domain.classification;

import java.util.Date;

/**
 * TODO
 * @author Andras Peteri
 */
public interface IClassificationRun extends IClassificationInput {

	/**
	 * 
	 * @return
	 */
	String getId();

	/**
	 * 
	 * @return
	 */
	ClassificationStatus getStatus();

	/**
	 * 
	 * @return
	 */
	Date getCreationDate();

	/**
	 * 
	 * @return the date of completion, or {@code null} if this classification run has not finished yet
	 */
	Date getCompletionDate();
}
