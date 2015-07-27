package org.ihtsdo.snowowl.authoring.single.api.service;

import com.b2international.snowowl.snomed.api.domain.classification.ClassificationStatus;

import net.rcarz.jiraclient.JiraException;

import org.ihtsdo.otf.im.utility.SecurityService;
import org.ihtsdo.otf.rest.client.ClassificationResults;
import org.ihtsdo.otf.rest.client.SnowOwlRestClient;
import org.ihtsdo.otf.rest.client.SnowOwlRestClientException;
//import org.ihtsdo.otf.rest.client.ClassificationResults;
//import org.ihtsdo.otf.rest.client.SnowOwlRestClient;
//import org.ihtsdo.otf.rest.client.SnowOwlRestClientException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Classification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.StateTransition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;


public class ClassificationService {
	
	private Logger logger = LoggerFactory.getLogger(ClassificationService.class);

	@Autowired
	private BranchService branchService;
	
	@Autowired
	private TaskService taskService;

	@Autowired
	SecurityService ims;
	
	@Autowired
	SnowOwlRestClient snowOwlClient;
	
	//public static final String CLASSIFIER_ID = "au.csiro.snorocket.owlapi3.snorocket.factory";

	public Classification startClassification(String projectKey,
			String taskKey) {
		Classification result;
		//Can we transition from In Progress to In Classification?
		StateTransition startClassification = new StateTransition ( StateTransition.STATE_IN_PROGRESS,
																	StateTransition.TRANSITION_IN_PROGRESS_TO_IN_CLASSIFICATION);

		taskService.doStateTransition(projectKey, taskKey, startClassification);

		if (startClassification.transitionSuccessful()) {
			try {
				result = callClassification(projectKey, taskKey);
			} catch (Exception e) {
				String errorMessage = "Classification failed because: " + e.getMessage();
				logger.error(errorMessage, e);
				result = new Classification(errorMessage);
			}
			
			//If the classification failed to start, then we should transition back to In Progress
			if (result.getStatus().equals(ClassificationStatus.FAILED)) {
				StateTransition failClassification = new StateTransition ( StateTransition.STATE_IN_CLASSIFICATION,
						StateTransition.TRANSITION_IN_CLASSIFICATION_TO_IN_PROGRESS);
				taskService.doStateTransition(projectKey, taskKey, failClassification);
			}
		} else {
			result = new Classification("Unable to start classification because: " + startClassification.getErrorMessage());
		}
		
	
		
		return result;
	}

	public String getLatestClassification(String projectKey, String taskKey) throws SnowOwlRestClientException {
		return snowOwlClient.getLatestClassificationOnBranch("MAIN/" + projectKey + "/" + taskKey);
	}

	private Classification callClassification(String projectKey, String taskKey) throws InterruptedException, SnowOwlRestClientException {
		ClassificationResults results = snowOwlClient.startClassification(branchService.getTaskPath(projectKey, taskKey));
		//If we started the classification without an exception then it's state will be RUNNING (or queued)
		results.setStatus(ClassificationStatus.RUNNING.toString());

		//Now start an asynchronous thread to wait for the results
		(new Thread(new ClassificationPoller(projectKey, taskKey, results))).start();

		return new Classification(results);
	}

	private class ClassificationPoller implements Runnable {
		
		ClassificationResults results;
		String projectKey;
		String taskKey;
		
		ClassificationPoller (String projectKey, String taskKey, ClassificationResults results) {
			this.results = results;
			this.projectKey = projectKey;
			this.taskKey = taskKey;
		}

		@Override
		public void run() {
			String taskMessage = "Classification State Unknown";
			try {
				snowOwlClient.waitForClassificationToComplete(results);
				
				if (results.getStatus().equals(ClassificationStatus.COMPLETED.toString())) {
					taskMessage = "Classification completed successfully";
				} else {
					taskMessage = "Classification is in non-successful state: " + results.getStatus();
				}
			} catch (Exception e) {
				taskMessage = "Classification failed to complete due to " + e.getMessage();
				logger.error(taskMessage,e);
			}
			
			//In every case we'll report what we know to the jira ticket, and move
			//the ticket status out from Classification to In Progress
			addJiraComment(projectKey, taskKey, taskMessage);
			returnToInProgress(projectKey, taskKey);
		}

		private void addJiraComment(String projectKey, String taskKey,
				String taskMessage) {
			try {
				taskService.addComment(projectKey, taskKey, taskMessage);
			} catch (JiraException je) {
				logger.error("Failed to set message on jira ticket {}/{}: {}", projectKey, taskKey, taskMessage, je);
			}
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
