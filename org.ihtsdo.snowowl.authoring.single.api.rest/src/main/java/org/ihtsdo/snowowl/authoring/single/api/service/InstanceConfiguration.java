package org.ihtsdo.snowowl.authoring.single.api.service;

import java.util.Set;

public class InstanceConfiguration {

	private Set<String> jiraProjectFilterProductCodes;

	public InstanceConfiguration(Set<String> jiraProjectFilterProductCodes) {
		this.jiraProjectFilterProductCodes = jiraProjectFilterProductCodes;
	}

	public boolean isJiraProjectVisible(String productCode) {
		return jiraProjectFilterProductCodes.contains(productCode);
	}
}
