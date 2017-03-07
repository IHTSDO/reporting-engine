package org.ihtsdo.snowowl.authoring.batchimport.api.service.task.jira;

import net.rcarz.jiraclient.JiraClient;

public interface ImpersonatingJiraClientFactory {

	JiraClient getImpersonatingInstance(String username);

}
