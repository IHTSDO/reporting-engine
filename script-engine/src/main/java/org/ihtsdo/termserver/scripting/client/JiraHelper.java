package org.ihtsdo.termserver.scripting.client;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;

import org.ihtsdo.otf.exception.TermServerScriptException;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import net.rcarz.jiraclient.*;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JiraHelper {

	private static final Logger LOGGER = LoggerFactory.getLogger(JiraHelper.class);

	JiraClient client;
	JiraConfig config;
	public static final String TASK = "Task";
	public static final String CONFIG_FILE_LOCATION = "secure/jira-api-secret.json";
	
	public Issue createJiraTicket(String projectKey, String summary, String description) throws TermServerScriptException {
		Issue jiraIssue;
		try {
			jiraIssue = getJiraClient().createIssue(projectKey, TASK)
					.field(Field.SUMMARY, summary)
					.field(Field.DESCRIPTION, description)
					//.field(Field.ASSIGNEE, username)
					.execute();
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | JiraException e) {
			throw new TermServerScriptException("Failed to create Jira task", e);
		}
		return jiraIssue;
	}
	
	public Issue getJiraTicket(String taskKey) throws TermServerScriptException {
		try {
			return getJiraClient().getIssue(taskKey);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | JiraException e) {
			throw new TermServerScriptException("Failed to recover Jira task", e);
		}
	}

	/**
	 * Get an instance of JiraClient that will make signed OAuth requests as the specified username.
	 * @param username
	 * @return
	 * @throws IOException 
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchAlgorithmException 
	 */
	private JiraClient getJiraClient() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		if (client == null) {
			try {
				loadConfig();
				PrivateKey pk = OAuthCredentials.getPrivateKey(config.getPrivateKeyPath());
				client =  new net.rcarz.jiraclient.JiraClient(
										config.getJiraUrl(), 
										new OAuthCredentials(config.getUsername(), 
											config.getConsumerKey(), 
											pk)
										);
			} catch (JiraException e) {
				throw new RuntimeException("Failed to create JiraClient.", e);
			}
		}
		return client;
	}

	private void loadConfig() throws FileNotFoundException {
		Gson gson = new Gson();
		JsonReader reader = new JsonReader(new FileReader(CONFIG_FILE_LOCATION));
		config = gson.fromJson(reader, JiraConfig.class);
	}
}
