package org.ihtsdo.snowowl.authoring.single.api.service.jira;

import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

public class OAuthJiraClientFactory implements ImpersonatingJiraClientFactory {

	private final String jiraUrl;
	private final String consumerKey;
	private final PrivateKey privateKey;

	public OAuthJiraClientFactory(String jiraUrl, String consumerKey, String privateKeyPath) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		this.jiraUrl = jiraUrl;
		this.consumerKey = consumerKey;
		privateKey = OAuthCredentials.getPrivateKey(privateKeyPath.replace("file:", ""));
	}

	/**
	 * Get an instance of JiraClient that will make signed OAuth requests as the specified username.
	 * @param username
	 * @return
	 */
	public JiraClient getImpersonatingInstance(String username) {
		try {
			return new JiraClient(jiraUrl, new OAuthCredentials(username, consumerKey, privateKey));
		} catch (JiraException e) {
			throw new RuntimeException("Failed to create JiraClient.", e);
		}
	}

}
