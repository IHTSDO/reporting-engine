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
import org.ihtsdo.snowowl.authoring.single.api.pojo.ReleaseRequest;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Status;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
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
	public static final String EFFECTIVE_TIME = "effective-time";
	public static final String STATUS = "status";
	public static final String STATUS_SCHEDULED = "SCHEDULED";
	public static final String STATUS_COMPLETE = "COMPLETED";
	public static final String STATUS_NOT_TRIGGERED = "NOT_TRIGGERED";

	@Autowired
	private TaskService taskService;

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
								return getValidationStatusesWithoutCache(Collections.singletonList(path)).iterator().next();
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
									final List<String> validationStatuses = getValidationStatusesWithoutCache(pathsToLoad);
									if (validationStatuses != null) {
										for (int i = 0; i < pathsToLoad.size(); i++) {
											String value = validationStatuses.get(i);
											if (value == null) {
												value = STATUS_NOT_TRIGGERED;
											}
											map.put(pathsToLoad.get(i), value);
										}
									} else {
										logger.error("Unable to load Validation Status, {} requested none returned, see logs", pathsToLoad.size());
									}
								}
								return map.build();
							}
						});
	}

	public Status startValidation(String projectKey, String taskKey, String username) throws BusinessServiceException {
		return doStartValidation(taskService.getTaskBranchPathUsingCache(projectKey, taskKey), username, projectKey, taskKey, null);
	}

	public Status startValidation(String projectKey, String username) throws BusinessServiceException {
		return doStartValidation(taskService.getProjectBranchPathUsingCache(projectKey), username, projectKey, null, null);
	}

	private Status doStartValidation(String path, String username, String projectKey, String taskKey, String effectiveTime) throws BusinessServiceException {
		try {
			Map<String, String> properties = new HashMap<>();
			properties.put(PATH, path);
			properties.put(USERNAME, username);
			if (projectKey != null) {
				properties.put(PROJECT, projectKey);
			}
			if (taskKey != null) {
				properties.put(TASK, taskKey);
			}
			if (effectiveTime != null) {
				properties.put(EFFECTIVE_TIME, effectiveTime);
			}
			messagingHelper.send(VALIDATION_REQUEST_QUEUE, "", properties, VALIDATION_RESPONSE_QUEUE);
			validationStatusCache.put(path, STATUS_SCHEDULED);
			return new Status(STATUS_SCHEDULED);
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

	public String getValidationJson(String projectKey, String taskKey) throws BusinessServiceException {
		return getValidationJsonIfAvailable(taskService.getTaskBranchPathUsingCache(projectKey, taskKey));
	}

	public String getValidationJson(String projectKey) throws BusinessServiceException {
		return getValidationJsonIfAvailable(taskService.getProjectBranchPathUsingCache(projectKey));
	}
	
	public String getValidationJson() throws BusinessServiceException {
		return getValidationJsonIfAvailable(PathHelper.getMainPath());
	}
	
	private String getValidationJsonIfAvailable(String path) throws BusinessServiceException {
		try {
		//Only return the validation json if the validation is complete
			String validationStatus = getValidationStatus(path);
			if (STATUS_COMPLETE.equals(validationStatus)) {
				return orchestrationRestClient.retrieveValidation(path);
			} else {
				logger.warn("Ignoring request for validation json for path {} as status {} ", path, validationStatus);
				return null;
			}
		} catch (Exception e) {
			throw new BusinessServiceException ("Unable to recover validation json for " + path, e);
		}
	}

	public ImmutableMap<String, String> getValidationStatuses(Collection<String> paths) throws ExecutionException {
		return validationStatusCache.getAll(paths);
	}
	
	public String getValidationStatus(String path) throws ExecutionException {
		return validationStatusCache.get(path);
	}

	private List<String> getValidationStatusesWithoutCache(List<String> paths) {
		List<String> statuses;
		try {
			statuses = orchestrationRestClient.retrieveValidationStatuses(paths);
		} catch (Exception e) {
			logger.error("Failed to retrieve validation status of tasks {}", paths, e);
			statuses = new ArrayList<>();
			for (int i = 0; i < paths.size(); i++) {
				statuses.add(TaskService.FAILED_TO_RETRIEVE);
			}
		}
		for (int i = 0; i < statuses.size(); i++) {
			if (statuses.get(i) == null) {
				statuses.set(i, STATUS_NOT_TRIGGERED);
			}
		}
		return statuses;
	}

	//Start validation for MAIN
	public Status startValidation(ReleaseRequest releaseRequest,
			String username) throws BusinessServiceException {
		String effectiveDate = null;
		if (releaseRequest != null && releaseRequest.getEffectiveDate() != null) {
			String potentialEffectiveDate = releaseRequest.getEffectiveDate();
			try {
				
				new SimpleDateFormat("yyyyDDmm").parse(potentialEffectiveDate);
				effectiveDate = potentialEffectiveDate;
			} catch (ParseException e) {
				logger.error("Unable to set effective date for MAIN validation, unrecognised: " + potentialEffectiveDate, e);
			}
		}
		return doStartValidation(PathHelper.getMainPath(), username, null, null, effectiveDate);
	}

	public void clearStatusCache() {
		validationStatusCache.invalidateAll();
	}
}
