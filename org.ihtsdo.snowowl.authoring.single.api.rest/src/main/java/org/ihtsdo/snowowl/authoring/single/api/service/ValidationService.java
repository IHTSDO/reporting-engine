package org.ihtsdo.snowowl.authoring.single.api.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.ImmutableMap;
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
import java.util.*;
import java.util.concurrent.ExecutionException;
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

	private LoadingCache<String, String> validationStatusCache;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void init() {
		validationStatusCache = CacheBuilder.newBuilder()
				.maximumSize(10000)
				.build(
						new CacheLoader<String, String>() {
							public String load(String path) throws Exception {
								return getValidationStatuses(Collections.singletonList(path)).iterator().next();
							}

							@Override
							public Map<String, String> loadAll(Iterable<? extends String> paths) throws Exception {
								final ImmutableMap.Builder<String, String> map = ImmutableMap.builder();
								List<String> pathsToLoad = new ArrayList<>();
								for (String path : paths) {
									final String status = validationStatusCache.getIfPresent(path);
									if (status != null) {
										map.put(path, status);
									} else {
										pathsToLoad.add(path);
									}
								}
								if (!pathsToLoad.isEmpty()) {
									final List<String> validationStatuses = getValidationStatuses(pathsToLoad);
									if (validationStatuses != null) {
										for (int i = 0; i < pathsToLoad.size(); i++) {
											String value = validationStatuses.get(i);
											if (value == null) {
												value = "";
											}
											map.put(pathsToLoad.get(i), value);
										}
									} else {
										logger.error("Unable to Initialise Validation Status Cache - none returned, see logs");
									}
								}
								return map.build();
							}
						});
	}

	public String startValidation(String projectKey, String taskKey, String username) throws BusinessServiceException {
		return doStartValidation(PathHelper.getPath(projectKey, taskKey), username, projectKey, taskKey);
	}

	public String startValidation(String projectKey, String username) throws BusinessServiceException {
		return doStartValidation(PathHelper.getPath(projectKey), username, projectKey, null);
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
			validationStatusCache.put(path, STATUS_SCHEDULED);
			return STATUS_SCHEDULED;
		} catch (JsonProcessingException | JMSException e) {
			throw new BusinessServiceException("Failed to send validation request, please contact support.", e);
		}
	}

	@SuppressWarnings("unused")
	@JmsListener(destination = VALIDATION_RESPONSE_QUEUE)
	public void receiveValidationEvent(TextMessage message) {
		try {
			if (!MessagingHelper.isError(message)) {
				logger.info("receiveValidationEvent {}", message);
				final String validationStatus = message.getStringProperty(STATUS);

				// Update cache
				validationStatusCache.put(message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PATH), validationStatus);

				// Notify user
				notificationService.queueNotification(
						message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + USERNAME),
						new Notification(
								message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + PROJECT),
								message.getStringProperty(MessagingHelper.REQUEST_PROPERTY_NAME_PREFIX + TASK),
								EntityType.Validation,
								validationStatus));
			} else {
				logger.error("receiveValidationEvent response with error {}", message);
			}
		} catch (JMSException e) {
			logger.error("Failed to handle validation event message.", e);
		}
	}

	public String getValidationJson(String projectKey, String taskKey) throws IOException, JSONException {
		return orchestrationRestClient.retrieveValidation(PathHelper.getPath(projectKey, taskKey));
	}

	public String getValidationJson(String projectKey) throws IOException, JSONException {
		return orchestrationRestClient.retrieveValidation(PathHelper.getPath(projectKey));
	}

	public ImmutableMap<String, String> getValidationStatusesUsingCache(Collection<String> paths) throws ExecutionException {
		return validationStatusCache.getAll(paths);
	}

	private List<String> getValidationStatuses(List<String> paths) {
		List<String> statuses;
		try {
			statuses = orchestrationRestClient.retrieveValidationStatuses(paths);
		} catch (JSONException | IOException e) {
			logger.error("Failed to retrieve validation status of tasks {}", paths, e);
			statuses = new ArrayList<>();
			for (int i = 0; i < paths.size(); i++) {
				statuses.add(TaskService.FAILED_TO_RETRIEVE);
			}
		}
		return statuses;
	}
}
