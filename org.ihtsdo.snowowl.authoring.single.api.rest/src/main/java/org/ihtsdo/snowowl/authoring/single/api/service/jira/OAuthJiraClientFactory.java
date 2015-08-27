package org.ihtsdo.snowowl.authoring.single.api.service.jira;

import net.rcarz.jiraclient.JiraClient;

public class OAuthJiraClientFactory {

	private final String jiraUrl;
	private final String consumerKey;
	private final String privateKeyPath;

	public OAuthJiraClientFactory(String jiraUrl, String consumerKey, String privateKeyPath) {
		this.jiraUrl = jiraUrl;
		this.consumerKey = consumerKey;
		this.privateKeyPath = privateKeyPath;
	}

	/**
	 * Get an instance of JiraClient that will make signed OAuth requests as the specified username.
	 * @param username
	 * @return
	 */
	public JiraClient getImpersonatingInstance(String username) {
		return new JiraClient(jiraUrl, new OAuthCredentials(username, consumerKey, privateKeyPath));
	}

}
