package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.snomed.api.domain.classification.ClassificationStatus;

import org.ihtsdo.otf.rest.client.ClassificationResults;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.SnowOwlRestClient;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.api.rest.common.ControllerHelper;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import us.monoid.json.JSONException;

public class ClassificationService {
	
	@Autowired
	private TaskService taskService;

	@Autowired
	private SnowOwlRestClient snowOwlClient;

	@Autowired
	private NotificationService notificationService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public synchronized Classification startClassification(String projectKey, String taskKey, String branchPath, String username) throws RestClientException, JSONException, BusinessServiceException {
		if (!snowOwlClient.isClassificationInProgressOnBranch(branchPath)) {
			return callClassification(projectKey, taskKey, branchPath, username);
		} else {
			throw new IllegalStateException("Classification already in progress on this branch.");
		}
	}

	public String getLatestClassification(String branchPath) throws RestClientException {
		return snowOwlClient.getLatestClassificationOnBranch(branchPath);
	}

	private Classification callClassification(String projectKey, String taskKey, String branchPath, String callerUsername) throws RestClientException {
		logger.info("Requesting classification of path {} for user {}", branchPath, callerUsername);
		ClassificationResults results = snowOwlClient.startClassification(branchPath);
		//If we started the classification without an exception then it's state will be RUNNING (or queued)
		results.setStatus(ClassificationStatus.RUNNING.toString());

		//Now start an asynchronous thread to wait for the results
		final Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		(new Thread(new ClassificationPoller(projectKey, taskKey, results, authentication))).start();

		return new Classification(results);
	}

	private class ClassificationPoller implements Runnable {

		private ClassificationResults results;
		private String projectKey;
		private String taskKey;
		private final Authentication authentication;

		ClassificationPoller(String projectKey, String taskKey, ClassificationResults results, Authentication authentication) {
			this.results = results;
			this.projectKey = projectKey;
			this.taskKey = taskKey;
			this.authentication = authentication;
		}

		@Override
		public void run() {
			SecurityContextHolder.getContext().setAuthentication(authentication);
			String resultMessage;
			try {
				snowOwlClient.waitForClassificationToComplete(results);
				
				if (results.getStatus().equals(ClassificationStatus.COMPLETED.toString())) {
					resultMessage = "Classification completed successfully";
				} else {
					resultMessage = "Classification is in non-successful state: " + results.getStatus();
				}
			} catch (RestClientException | InterruptedException e) {
				resultMessage = "Classification failed to complete due to " + e.getMessage();
				logger.error(resultMessage,e);
			}

			if (taskKey != null) {
				//In every case we'll report what we know to the jira ticket
				taskService.addCommentLogErrors(projectKey, taskKey, resultMessage);
			} else {
				// Comment on project magic ticket
				taskService.addCommentLogErrors(projectKey, resultMessage);
			}
			notificationService.queueNotification(ControllerHelper.getUsername(), new Notification(projectKey, taskKey, EntityType.Classification, resultMessage));
		}

	}

}
