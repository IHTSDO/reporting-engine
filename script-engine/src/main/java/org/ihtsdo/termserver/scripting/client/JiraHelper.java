package org.ihtsdo.termserver.scripting.client;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.URISyntaxException;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.spec.InvalidKeySpecException;
import java.util.List;

import org.ihtsdo.otf.exception.TermServerScriptException;

import com.google.gson.Gson;
import com.google.gson.stream.JsonReader;

import net.rcarz.jiraclient.*;

public class JiraHelper {

	JiraClient client;
	JiraConfig config;
	public static final String TASK = "Task";
	public static final String CONFIG_FILE_LOCATION = "secure/jira-api-secret.json";

	private static final String INCLUDED_FIELDS = "summary, created, status, assignee, comment, customfield_10401";
	
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

	public List<Issue> getIssues(String projectKey, int limit, int startAt) throws TermServerScriptException {
		try {
			JiraClient jira = getJiraClient();
			String jql = String.format("project = %s ORDER BY created DESC", projectKey);
			return jira.searchIssues(jql, INCLUDED_FIELDS, limit, startAt).issues;
		} catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException | JiraException e) {
			throw new TermServerScriptException("Failed to fetch recent Jira issues", e);
		}
	}

	/**
	 * Get an instance of JiraClient that will make signed OAuth requests as the specified username.
	 * @return
	 * @throws IOException 
	 * @throws InvalidKeySpecException 
	 * @throws NoSuchAlgorithmException 
	 */
	private JiraClient getJiraClient() throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
		if (client == null) {
			try {
				loadConfig();
				if (config.getPrivateKeyPath() != null) {
					PrivateKey pk = OAuthCredentials.getPrivateKey(config.getPrivateKeyPath());
					client = new net.rcarz.jiraclient.JiraClient(
							config.getJiraUrl(),
							new OAuthCredentials(config.getUsername(),
									config.getConsumerKey(),
									pk)
					);
				} else {
					BasicCredentials creds = new BasicCredentials(config.getUsername(), config.getToken());
					client = new JiraClient(config.getJiraUrl(), creds);
					//Test the connection
					client.getRestClient().get("/rest/api/2/myself");
				}
			} catch (URISyntaxException| RestException | JiraException e) {
				throw new RuntimeException("Failed to create authenticated JiraClient.", e);
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
