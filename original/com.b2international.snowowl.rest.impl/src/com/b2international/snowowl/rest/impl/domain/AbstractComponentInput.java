/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.rest.impl.domain;

import com.b2international.snowowl.rest.domain.IComponentInput;

/**
 * @author apeteri
 */
public abstract class AbstractComponentInput implements IComponentInput {

	private String codeSystemShortName;
	private String codeSystemVersionId;
	private String taskId;

	@Override
	public String getCodeSystemShortName() {
		return codeSystemShortName;
	}

	@Override
	public String getCodeSystemVersionId() {
		return codeSystemVersionId;
	}

	@Override
	public String getTaskId() {
		return taskId;
	}
	
	public void setCodeSystemShortName(String codeSystemShortName) {
		this.codeSystemShortName = codeSystemShortName;
	}

	public void setCodeSystemVersionId(String codeSystemVersionId) {
		this.codeSystemVersionId = codeSystemVersionId;
	}
	
	public void setTaskId(String taskId) {
		this.taskId = taskId;
	}
}
