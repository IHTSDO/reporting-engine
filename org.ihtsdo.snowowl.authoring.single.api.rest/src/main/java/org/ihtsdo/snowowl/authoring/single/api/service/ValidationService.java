package org.ihtsdo.snowowl.authoring.single.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.ihtsdo.otf.jms.MessagingHelper;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.EntityType;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Validation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

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

	@Autowired
	private MessagingHelper messagingHelper;

	@Autowired
	private NotificationService notificationService;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public Validation startValidation(String projectKey, String taskKey, String username) throws BusinessServiceException {
		return doStartValidation("MAIN/" + projectKey + "/" + taskKey, username, projectKey, taskKey);
	}

	public Validation startValidation(String projectKey, String username) throws BusinessServiceException {
		return doStartValidation("MAIN/" + projectKey, username, projectKey, null);
	}

	private Validation doStartValidation(String path, String username, String projectKey, String taskKey) throws BusinessServiceException {
		try {
			Map<String, String> properties = new HashMap<>();
			properties.put(PATH, path);
			properties.put(USERNAME, username);
			properties.put(PROJECT, projectKey);
			if (taskKey != null) {
				properties.put(TASK, taskKey);
			}
			messagingHelper.send(VALIDATION_REQUEST_QUEUE, properties, VALIDATION_RESPONSE_QUEUE);
			return new Validation(Validation.STATUS_SCHEDULED, "");
		} catch (JsonProcessingException | JMSException e) {
			throw new BusinessServiceException("Failed to send validation request, please contact support.", e);
		}
	}

	@JmsListener(destination = VALIDATION_RESPONSE_QUEUE)
	public void receiveValidationEvent(TextMessage message) {
		try {
			notificationService.queueNotification(
					message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + USERNAME),
					new Notification(
							message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PROJECT),
							message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + TASK),
							EntityType.Validation,
							message.getStringProperty(STATUS)));
		} catch (JMSException e) {
			logger.error("Failed to handle validation event message.", e);
		}
	}

	public Validation getValidation(String projectKey, String taskKey) {
		return new Validation ("NOT YET IMPLEMENTED", "");
	}

	public Validation getValidation(String projectKey) {
		return new Validation("NOT YET IMPLEMENTED", "");
	}

}
