package org.ihtsdo.snowowl.authoring.single.api.service.jira;

import net.rcarz.jiraclient.JiraClient;

public interface ImpersonatingJiraClientFactory {

	JiraClient getImpersonatingInstance(String username);

}
