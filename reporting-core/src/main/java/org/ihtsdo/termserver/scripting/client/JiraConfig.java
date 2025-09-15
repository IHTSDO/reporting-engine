package org.ihtsdo.termserver.scripting.client;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(JiraConfig.class);

	String username;
	String jiraUrl;
	String consumerKey;
	String privateKeyPath;
	String token;
	String jSessionId;

	
	public String getUsername() {
		return username;
	}
	public String getJiraUrl() {
		return jiraUrl;
	}
	public String getConsumerKey() {
		return consumerKey;
	}
	public String getPrivateKeyPath() {
		return privateKeyPath;
	}
	public String getToken() {
		return token;
	}
	public String getJSessionId() {
		return jSessionId;
	}
}
