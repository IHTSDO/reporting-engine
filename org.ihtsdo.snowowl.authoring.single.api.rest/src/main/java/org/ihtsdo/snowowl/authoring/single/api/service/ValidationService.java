package org.ihtsdo.snowowl.authoring.single.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.client.OrchestrationRestClient;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import us.monoid.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javax.jms.JMSException;
import javax.jms.TextMessage;

@Component
public class ValidationService {

	private static final String VALIDATION_REQUEST_QUEUE = "orchestration.termserver-release-validation";
	private static final String VALIDATION_RESPONSE_QUEUE = "sca.termserver-release-validation.response";
	public static final String PATH = "path";
	public static final String USERNAME = "username";
	public static final String PROJECT = "project";
	public static final String TASK = "task";
	public static final String STATUS = "status";
	public static final String STATUS_SCHEDULED = "SCHEDULED";

	@Autowired
	private MessagingHelper messagingHelper;

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private OrchestrationRestClient orchestrationRestClient;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public String startValidation(String projectKey, String taskKey, String username) throws BusinessServiceException {
		return doStartValidation(getPath(projectKey, taskKey), username, projectKey, taskKey);
	}

	public String startValidation(String projectKey, String username) throws BusinessServiceException {
		return doStartValidation(getPath(projectKey), username, projectKey, null);
	}

	private String doStartValidation(String path, String username, String projectKey, String taskKey) throws BusinessServiceException {
		try {
			Map<String, String> properties = new HashMap<>();
			properties.put(PATH, path);
			properties.put(USERNAME, username);
			properties.put(PROJECT, projectKey);
			if (taskKey != null) {
				properties.put(TASK, taskKey);
			}
			messagingHelper.send(VALIDATION_REQUEST_QUEUE, "", properties, VALIDATION_RESPONSE_QUEUE);
			return STATUS_SCHEDULED;
		} catch (JsonProcessingException | JMSException e) {
			throw new BusinessServiceException("Failed to send validation request, please contact support.", e);
		}
	}

	@JmsListener(destination = VALIDATION_RESPONSE_QUEUE)
	public void receiveValidationEvent(TextMessage message) {
		try {
			if (!MessagingHelper.isError(message)) {
				logger.info("receiveValidationEvent {}", message);
				notificationService.queueNotification(
						message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + USERNAME),
						new Notification(
								message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PROJECT),
								message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + TASK),
								EntityType.Validation,
								message.getStringProperty(STATUS)));
			} else {
				logger.error("receiveValidationEvent response with error {}", message);
			}
		} catch (JMSException e) {
			logger.error("Failed to handle validation event message.", e);
		}
	}

	public String getValidationJson(String projectKey, String taskKey) throws IOException, JSONException {
		return orchestrationRestClient.retrieveValidation(getPath(projectKey, taskKey));
	}

	public String getValidationJson(String projectKey) throws IOException, JSONException {
		return orchestrationRestClient.retrieveValidation(getPath(projectKey));
	}

	private String getPath(String projectKey) {
		return "MAIN/" + projectKey;
	}

	private String getPath(String projectKey, String taskKey) {
		return "MAIN/" + projectKey + "/" + taskKey;
	}

}
