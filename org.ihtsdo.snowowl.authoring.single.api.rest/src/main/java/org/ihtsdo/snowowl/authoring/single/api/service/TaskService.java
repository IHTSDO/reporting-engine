package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.core.exceptions.NotFoundException;
import net.rcarz.jiraclient.*;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringProject;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTask;
import org.ihtsdo.snowowl.authoring.single.api.pojo.AuthoringTaskCreateRequest;

import java.util.ArrayList;
import java.util.List;

public class TaskService {

	private final JiraClient jiraClient;
	private static final String AUTHORING_TASK_TYPE = "SCA Authoring Task";

	public TaskService(JiraClient jiraClient) {
		this.jiraClient = jiraClient;
	}

	public List<AuthoringProject> listProjects() throws JiraException {
		List<AuthoringProject> authoringProjects = new ArrayList<>();
		for (Issue issue : jiraClient.searchIssues("type = \"SCA Authoring Project\"").issues) {
			Project project = issue.getProject();
			authoringProjects.add(new AuthoringProject(project.getKey(), project.getName()));
		}
		return authoringProjects;
	}

	public List<AuthoringTask> listTasks(String projectKey) throws JiraException {
		List<AuthoringTask> tasks = new ArrayList<>();
		try {
			jiraClient.getProject(projectKey);
		} catch (JiraException e) {
			throw new NotFoundException("Project", projectKey);
		}
		List<Issue> issues = jiraClient.searchIssues("project = " + projectKey + " AND type = \"" + AUTHORING_TASK_TYPE + "\"").issues;
		for (Issue issue : issues) {
			tasks.add(new AuthoringTask(issue));
		}
		return tasks;
	}

	public AuthoringTask createTask(String projectKey, AuthoringTaskCreateRequest taskCreateRequest) throws JiraException {
		Issue execute = jiraClient.createIssue(projectKey, AUTHORING_TASK_TYPE)
				.field(Field.SUMMARY, taskCreateRequest.getSummary())
				.field(Field.DESCRIPTION, taskCreateRequest.getDescription())
				.execute();
		return new AuthoringTask(execute);
	}
}
