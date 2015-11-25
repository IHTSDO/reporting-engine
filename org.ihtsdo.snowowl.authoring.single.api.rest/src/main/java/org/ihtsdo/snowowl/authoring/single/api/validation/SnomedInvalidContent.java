package org.ihtsdo.snowowl.authoring.single.api.validation;

import org.ihtsdo.drools.response.InvalidContent;

public class SnomedInvalidContent {

	private InvalidContent invalidContent;

	public SnomedInvalidContent(InvalidContent invalidContent) {
		this.invalidContent = invalidContent;
	}
	
	public String getConceptId() {
		return invalidContent.getConceptId();
	}

	public String getComponentId() {
		return invalidContent.getComponentId();
	}

	public String getMessage() {
		return invalidContent.getMessage();
	}
	
	public String getSeverity() {
		return invalidContent.getSeverity().toString();
	}

}
