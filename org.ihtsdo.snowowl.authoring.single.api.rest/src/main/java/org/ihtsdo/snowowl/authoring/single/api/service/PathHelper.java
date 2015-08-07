package org.ihtsdo.snowowl.authoring.single.api.service;

import net.rcarz.jiraclient.Issue;

public class PathHelper {

	public static final String MAIN = "MAIN/";
	public static final String SLASH = "/";

	public static String getPath(String projectKey) {
		return MAIN + projectKey;
	}

	public static String getPath(String projectKey, String taskKey) {
		return MAIN + projectKey + SLASH + taskKey;
	}

	public static String getTaskPath(Issue issue) {
		return PathHelper.getPath(issue.getProject().getKey(), issue.getKey());
	}

}
