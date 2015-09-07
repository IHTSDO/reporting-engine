package org.ihtsdo.snowowl.authoring.single.api.service;

import net.rcarz.jiraclient.Issue;

public class PathHelper {

	private static final String MAIN = "MAIN";
	private static final String SLASH = "/";

	public static String getPath(String projectKey) {
		return getPath(projectKey, null);
	}

	public static String getPath(String projectKey, String taskKey) {
		String path = MAIN;
		if (projectKey != null) {
			path += SLASH + projectKey;
			if (taskKey != null) {
				path += SLASH + taskKey;
			}
		}
		return path;
	}

	public static String getTaskPath(Issue issue) {
		return PathHelper.getPath(issue.getProject().getKey(), issue.getKey());
	}

}
