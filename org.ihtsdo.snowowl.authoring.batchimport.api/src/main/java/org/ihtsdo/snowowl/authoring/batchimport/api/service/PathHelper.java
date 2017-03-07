package org.ihtsdo.snowowl.authoring.batchimport.api.service;

public class PathHelper {

	private static final String MAIN = "MAIN";
	private static final String SLASH = "/";
	
	public static String getMainPath() {
		return MAIN;
	}

	public static String getProjectPath(String extensionBase, String projectKey) {
		return getTaskPath(extensionBase, projectKey, null);
	}

	public static String getTaskPath(String extensionBase, String projectKey, String taskKey) {
		String path;
		if (extensionBase != null && !extensionBase.isEmpty()) {
			path = extensionBase;
		} else {
			path = MAIN;
		}
		if (projectKey != null) {
			path += SLASH + projectKey;
			if (taskKey != null) {
				path += SLASH + taskKey;
			}
		}
		return path;
	}

	public static String getParentPath(String branchPath) {
		return branchPath.substring(0, branchPath.lastIndexOf("/"));
	}

	public static String getName(String branchPath) {
		return branchPath.substring(branchPath.lastIndexOf("/") + 1);
	}

	public static String getParentName(String branchPath) {
		return getName(getParentPath(branchPath));
	}
}
