/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import java.util.Date;

import com.b2international.snowowl.rest.snomed.domain.classification.ClassificationStatus;
import com.b2international.snowowl.rest.snomed.domain.classification.IClassificationRun;

/**
 * @author apeteri
 */
public class ClassificationRestRun extends ClassificationRestInput implements IClassificationRun {

	private String id;
	private String userId;
	private ClassificationStatus status;
	private Date creationDate;
	private Date completionDate;

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getUserId() {
		return userId;
	}

	@Override
	public ClassificationStatus getStatus() {
		return status;
	}

	@Override
	public Date getCreationDate() {
		return creationDate;
	}

	@Override
	public Date getCompletionDate() {
		return completionDate;
	}

	public void setId(final String id) {
		this.id = id;
	}

	public void setUserId(final String userId) {
		this.userId = userId;
	}	

	public void setStatus(final ClassificationStatus status) {
		this.status = status;
	}

	public void setCreationDate(final Date creationDate) {
		this.creationDate = creationDate;
	}

	public void setCompletionDate(final Date completionDate) {
		this.completionDate = completionDate;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("ClassificationRestRun [id=,");
		builder.append(id);
		builder.append(", userId=");
		builder.append(userId);
		builder.append(", reasonerId=");
		builder.append(getReasonerId());
		builder.append(", status=");
		builder.append(status);
		builder.append(", creationDate=");
		builder.append(creationDate);
		builder.append(", completionDate=");
		builder.append(completionDate);
		builder.append("]");
		return builder.toString();
	}
}
