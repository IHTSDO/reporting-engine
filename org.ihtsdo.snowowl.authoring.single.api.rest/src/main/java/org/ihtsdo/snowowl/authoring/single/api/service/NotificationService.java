package org.ihtsdo.snowowl.authoring.single.api.service;

import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NotificationService {

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
		logger.info("Notification for user {} - '{}'", username, notification);
		synchronized (pendingNotifications) {
			if (!pendingNotifications.containsKey(username)) {
				pendingNotifications.put(username, new ArrayList<Notification>());
			}
			pendingNotifications.get(username).add(notification);
		}
	}

}
