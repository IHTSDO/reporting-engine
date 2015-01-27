/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain.snomed;

import java.util.Date;
import java.util.UUID;

/**
 * @author apeteri
 * @since 1.0
 */
public class SnomedImportDetails extends SnomedImportRestConfiguration {

	private UUID id;
	private SnomedImportStatus status;
	private Date startDate;
	private Date completionDate;
	
	public UUID getId() {
		return id;
	}
	
	public SnomedImportStatus getStatus() {
		return status;
	}

	public void setId(final UUID id) {
		this.id = id;
	}
	
	public void setStatus(SnomedImportStatus status) {
		this.status = status;
	}
	
	public Date getCompletionDate() {
		return completionDate;
	}
	
	public void setCompletionDate(Date completionDate) {
		this.completionDate = completionDate;
	}
	
	public Date getStartDate() {
		return startDate;
	}
	
	public void setStartDate(Date startDate) {
		this.startDate = startDate;
	}

	@Override
	public String toString() {
		final StringBuilder builder = new StringBuilder();
		builder.append("SnomedImportDetails [id=");
		builder.append(id);
		builder.append(", status=");
		builder.append(id);
		builder.append("]");
		return builder.toString();
	}
}

