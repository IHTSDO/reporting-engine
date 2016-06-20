package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class MonitorService {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());

	private Map<String, UserMonitors> userMonitorsMap = new HashMap<>();

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MonitorFactory monitorFactory;

	public void updateUserFocus(String username, String focusProjectId, String focusTaskId) throws BusinessServiceException {
		logger.info("Starting: Updating user focus for {} [{}/{}]", username, focusProjectId, focusTaskId);
		createIfNotExists(username);
		userMonitorsMap.get(username).updateFocus(focusProjectId, focusTaskId);
		logger.info("Finished: Updating user focus for {} [{}/{}]", username, focusProjectId, focusTaskId);
	}

	public void keepMonitorsAlive(String username) {
		final UserMonitors userMonitors = userMonitorsMap.get(username);
		if (userMonitors != null) {
			userMonitors.accessed();
		}
	}

	private void createIfNotExists(final String username) {
		if (!userMonitorsMap.containsKey(username)) {
			synchronized(getClass()) {
				if (!userMonitorsMap.containsKey(username)) {
					final Runnable deathCallback = new Runnable() {
						@Override
						public void run() {
							synchronized (MonitorService.class) {
								userMonitorsMap.remove(username);
							}
						}
					};
					final UserMonitors userMonitors = new UserMonitors(username, monitorFactory, notificationService, deathCallback);
					this.userMonitorsMap.put(username, userMonitors);
				}
			}
		}
	}

}
