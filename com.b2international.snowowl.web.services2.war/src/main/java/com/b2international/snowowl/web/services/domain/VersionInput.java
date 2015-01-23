/*******************************************************************************
 * Copyright (c) 2014 B2i Healthcare. All rights reserved.
 *******************************************************************************/
package com.b2international.snowowl.web.services.domain;

import java.util.Date;

import com.b2international.snowowl.rest.domain.codesystem.ICodeSystemVersionProperties;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;

/**
 * @author mczotter
 * @since 1.0
 */
public class VersionInput implements ICodeSystemVersionProperties {

	private String version;
	private String description = "";
	private Date effectiveDate;
	
	@Override
	public String getDescription() {
		return description;
	}
	
	@JsonFormat(shape=Shape.STRING, pattern="yyyyMMdd")
	@Override
	public Date getEffectiveDate() {
		return effectiveDate;
	}
	
	@Override
	public String getVersion() {
		return version;
	}
	
	public void setDescription(String description) {
		this.description = description;
	}
	
	public void setEffectiveDate(Date effectiveDate) {
		this.effectiveDate = effectiveDate;
	}
	
	public void setVersion(String version) {
		this.version = version;
	}
	
}
