package org.ihtsdo.snowowl.authoring.single.api.service;

import net.rcarz.jiraclient.ICredentials;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;

// TODO: Performance - Implement a pool of clients if needed.
public class JiraClientFactory {

	private final String jiraUrl;
	private final ICredentials iCredentials;

	public JiraClientFactory(String jiraUrl, ICredentials iCredentials) {
		this.jiraUrl = jiraUrl;
		this.iCredentials = iCredentials;
	}

	public JiraClient getInstance() throws JiraException {
		return new JiraClient(jiraUrl, iCredentials);
	}

}
