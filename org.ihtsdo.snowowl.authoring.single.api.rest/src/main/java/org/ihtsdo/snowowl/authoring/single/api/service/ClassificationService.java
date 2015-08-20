package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.snomed.api.domain.classification.ClassificationStatus;
import org.ihtsdo.otf.rest.client.ClassificationResults;
import org.ihtsdo.otf.rest.client.RestClientException;
import org.ihtsdo.otf.rest.client.SnowOwlRestClient;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import us.monoid.json.JSONException;

public class ClassificationService {
	
	@Autowired
	private BranchServiceImpl branchService;

	@Autowired
	private TaskService taskService;

	@Autowired
	private SnowOwlRestClient snowOwlClient;

	@Autowired
	private NotificationService notificationService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Classification startClassificationOfTask(String projectKey, String taskKey, String username) {
		Classification result;
		//Can we transition from In Progress to In Classification?
		StateTransition startClassification = new StateTransition ( StateTransition.STATE_IN_PROGRESS,
																	StateTransition.TRANSITION_IN_PROGRESS_TO_IN_CLASSIFICATION);

		taskService.doStateTransition(projectKey, taskKey, startClassification);

		if (startClassification.transitionSuccessful()) {
			try {
				result = callClassification(projectKey, taskKey, username);
			} catch (Exception e) {
				String errorMessage = "Classification failed because: " + e.getMessage();
				logger.error(errorMessage, e);
				result = new Classification(errorMessage);
			}
			
			//If the classification failed to start, then we should transition back to In Progress
			if (result.getStatus().equals(ClassificationStatus.FAILED.toString())) {
				StateTransition failClassification = new StateTransition ( StateTransition.STATE_IN_CLASSIFICATION,
						StateTransition.TRANSITION_IN_CLASSIFICATION_TO_IN_PROGRESS);
				taskService.doStateTransition(projectKey, taskKey, failClassification);
			}
		} else {
			result = new Classification("Unable to start classification because: " + startClassification.getErrorMessage());
		}
		
		return result;
	}

	public synchronized Classification startClassificationOfProject(String projectKey, String username) throws RestClientException, JSONException {
		if (!snowOwlClient.isClassificationInProgressOnBranch(PathHelper.getPath(projectKey))) {
			return callClassification(projectKey, null, username);
		} else {
			throw new IllegalStateException("Classification already in progress on this branch.");
		}
	}

	public String getLatestClassification(String path) throws RestClientException {
		return snowOwlClient.getLatestClassificationOnBranch(path);
	}

	private Classification callClassification(String projectKey, String taskKey, String callerUsername) throws RestClientException {
		String path;
		if (taskKey != null) {
			path = branchService.getTaskPath(projectKey, taskKey);
		} else {
			path = branchService.getProjectPath(projectKey);
		}
		ClassificationResults results = snowOwlClient.startClassification(path);
		//If we started the classification without an exception then it's state will be RUNNING (or queued)
		results.setStatus(ClassificationStatus.RUNNING.toString());

		//Now start an asynchronous thread to wait for the results
		(new Thread(new ClassificationPoller(projectKey, taskKey, results, callerUsername))).start();

		return new Classification(results);
	}

	private class ClassificationPoller implements Runnable {

		private ClassificationResults results;
		private String projectKey;
		private String taskKey;
		private final String callerUsername;

		ClassificationPoller(String projectKey, String taskKey, ClassificationResults results, String callerUsername) {
			this.results = results;
			this.projectKey = projectKey;
			this.taskKey = taskKey;
			this.callerUsername = callerUsername;
		}

		@Override
		public void run() {
			String resultMessage;
			try {
				snowOwlClient.waitForClassificationToComplete(results);
				
				if (results.getStatus().equals(ClassificationStatus.COMPLETED.toString())) {
					resultMessage = "Classification completed successfully";
				} else {
					resultMessage = "Classification is in non-successful state: " + results.getStatus();
				}
			} catch (Exception e) {
				resultMessage = "Classification failed to complete due to " + e.getMessage();
				logger.error(resultMessage,e);
			}

			if (taskKey != null) {
				//In every case we'll report what we know to the jira ticket, and move
				//the ticket status out from Classification to In Progress
				taskService.addCommentLogErrors(projectKey, taskKey, resultMessage);
				returnToInProgress(projectKey, taskKey);
			} else {
				// Comment on project magic ticket
				taskService.addCommentLogErrors(projectKey, resultMessage);
			}
			notificationService.queueNotification(callerUsername, new Notification(projectKey, taskKey, EntityType.Classification, resultMessage));
		}

		private void returnToInProgress(String projectKey, String taskKey) {
				
			StateTransition finishClassification = new StateTransition ( StateTransition.STATE_IN_CLASSIFICATION,
					StateTransition.TRANSITION_IN_CLASSIFICATION_TO_IN_PROGRESS);

			taskService.doStateTransition(projectKey, taskKey, finishClassification);
			
			if (!finishClassification.transitionSuccessful()) {
				logger.error(finishClassification.getErrorMessage());
			}
		}
		
	}


}
