package org.ihtsdo.snowowl.authoring.single.api.service;

import com.google.common.base.Strings;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationService {

	@Autowired
	private TaskService taskService;

	private final Map<String, List<Notification>> pendingNotifications;

	private final Logger logger = LoggerFactory.getLogger(getClass());

	public NotificationService() {
		pendingNotifications = new HashMap<>();
	}

	public List<Notification> retrieveNewNotifications(String username) {
		if (pendingNotifications.containsKey(username)) {
			synchronized (pendingNotifications) {
				return pendingNotifications.remove(username);
			}
		}
		return null;
	}

	public void queueNotification(String username, Notification notification) {
		final String projectKey = notification.getProject();
		if (!Strings.isNullOrEmpty(projectKey)) {
			try {
				notification.setBranchPath(taskService.getBranchPathUsingCache(projectKey, notification.getTask()));
			} catch (BusinessServiceException e) {
				logger.error("Failed to retrieve project base for {}", projectKey);
			}
		}
		logger.info("Notification for user {} - '{}'", username, notification);
		synchronized (pendingNotifications) {
			if (!pendingNotifications.containsKey(username)) {
				pendingNotifications.put(username, new ArrayList<Notification>());
			}
			pendingNotifications.get(username).add(notification);
		}
	}

}
