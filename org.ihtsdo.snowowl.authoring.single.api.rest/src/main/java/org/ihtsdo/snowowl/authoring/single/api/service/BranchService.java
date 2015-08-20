package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public interface BranchService {
	void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException;

	AuthoringTaskReview diffTaskBranch(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException;

	String getTaskPath(String projectKey, String taskKey);

	String getProjectPath(String projectKey);
}
