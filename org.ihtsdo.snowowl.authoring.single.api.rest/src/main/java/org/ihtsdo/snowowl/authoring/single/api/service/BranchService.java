package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.datastore.server.branch.Branch;
import com.b2international.snowowl.datastore.server.review.ReviewStatus;
import net.rcarz.jiraclient.JiraException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
import org.ihtsdo.snowowl.authoring.single.api.pojo.MergeRequest;
import org.ihtsdo.snowowl.authoring.single.api.review.pojo.AuthoringTaskReview;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public interface BranchService {
	void createTaskBranchAndProjectBranchIfNeeded(String projectKey, String taskKey) throws ServiceException;

	Branch.BranchState getBranchState(String project, String taskKey) throws ServiceException;

	AuthoringTaskReview diffTaskAgainstProject(String projectKey, String taskKey, List<Locale> locales) throws ExecutionException, InterruptedException;

	ReviewStatus getReviewStatus(String id) throws ExecutionException, InterruptedException;

	String getTaskPath(String projectKey, String taskKey);

	String getProjectPath(String projectKey);

	ConflictReport createConflictReport(String projectKey, String taskKey, List<Locale> list) throws BusinessServiceException;

	Branch rebaseTask(String projectKey, String taskKey,
			MergeRequest mergeRequest, String username) throws BusinessServiceException;

	AuthoringTaskReview diffProjectAgainstTask(String projectKey,
			String taskKey, List<Locale> locales) throws ExecutionException,
			InterruptedException;

	AuthoringTaskReview diffProjectAgainstMain(String projectKey, List<Locale> locales) throws ExecutionException, InterruptedException;

	Branch promoteTask(String projectKey, String taskKey,
			MergeRequest mergeRequest, String username) throws BusinessServiceException, JiraException;

	ConflictReport createConflictReport(String projectKey, List<Locale> locales) throws BusinessServiceException;

	void rebaseProject(String projectKey, MergeRequest mergeRequest,
			String username) throws BusinessServiceException;

	void promoteProject(String projectKey, MergeRequest mergeRequest, String username) throws BusinessServiceException;

}
