package org.ihtsdo.termserver.scripting.client;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraConfig {

	private static final Logger LOGGER = LoggerFactory.getLogger(JiraConfig.class);

	String username;
	String jiraUrl;
	String consumerKey;
	String privateKeyPath;
	
	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username;
	}
	public String getJiraUrl() {
		return jiraUrl;
	}
	public void setJiraUrl(String jiraUrl) {
		this.jiraUrl = jiraUrl;
	}
	public String getConsumerKey() {
		return consumerKey;
	}
	public void setConsumerKey(String consumerKey) {
		this.consumerKey = consumerKey;
	}
	public String getPrivateKeyPath() {
		return privateKeyPath;
	}
	public void setPrivateKeyPath(String privateKeyPath) {
		this.privateKeyPath = privateKeyPath;
	}
}
