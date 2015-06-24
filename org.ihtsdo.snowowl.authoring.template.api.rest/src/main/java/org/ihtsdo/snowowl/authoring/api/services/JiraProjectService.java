package org.ihtsdo.snowowl.authoring.single.api.services;

import net.rcarz.jiraclient.*;

public class JiraProjectService {

	public static final String ISSUE_TYPE_TASK = "Task";
	private final JiraClient jiraClient;
	private final String projectKey;

	public JiraProjectService(String uri, String projectKey, String username, String password) {
		jiraClient = new JiraClient(uri, new BasicCredentials(username, password));
		this.projectKey = projectKey;
	}

	public String createJiraTask() throws JiraException {
		Issue issue = jiraClient.createIssue(projectKey, ISSUE_TYPE_TASK)
				.field(Field.SUMMARY, "Content batch.")
				.execute();
		return issue.getKey();
	}

}
