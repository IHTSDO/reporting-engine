package org.ihtsdo.snowowl.authoring.single.api.service;

import net.rcarz.jiraclient.Issue;
import net.rcarz.jiraclient.JiraClient;
import net.rcarz.jiraclient.JiraException;
import net.rcarz.jiraclient.Project;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;

import java.util.ArrayList;
import java.util.List;

public class TaskService {

	private final JiraClient jiraClient;

	public TaskService(JiraClient jiraClient) {
		this.jiraClient = jiraClient;
	}

	public List<AuthoringProject> listProjects() throws JiraException {
		List<AuthoringProject> authoringProjects = new ArrayList<>();
		Issue.SearchResult projectStatusTickets = jiraClient.searchIssues("type = \"SCA Authoring Project\"");
		for (Issue issue : projectStatusTickets.issues) {
			Project project = issue.getProject();
			authoringProjects.add(new AuthoringProject(project.getKey(), project.getName()));
		}
		return authoringProjects;
	}

}
