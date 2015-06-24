package org.ihtsdo.snowowl.authoring.single.api.terminology;

import java.util.List;

/**
 * Represents a domain (hierarchy)
 */
public class Domain {

	private String name;
	private String conceptId;
	private List<String> allowedAttributes;

	public Domain() {
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getConceptId() {
		return conceptId;
	}

	public void setConceptId(String conceptId) {
		this.conceptId = conceptId;
	}

	public List<String> getAllowedAttributes() {
		return allowedAttributes;
	}

	public void setAllowedAttributes(List<String> allowedAttributes) {
		this.allowedAttributes = allowedAttributes;
	}
}
