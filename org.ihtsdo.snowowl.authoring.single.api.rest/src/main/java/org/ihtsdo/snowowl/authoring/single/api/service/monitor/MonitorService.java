package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.snowowl.authoring.single.api.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.HashMap;
import java.util.Map;

public class MonitorService {

	private Map<String, UserMonitors> userMonitors = new HashMap<>();

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private MonitorFactory monitorFactory;

	public void updateUserFocus(String username, String focusProjectId, String focusTaskId) {
		if (!userMonitors.containsKey(username)) {
			synchronized(getClass()) {
				if (!userMonitors.containsKey(username)) {
					final UserMonitors userMonitors = new UserMonitors(username, monitorFactory, notificationService);
					this.userMonitors.put(username, userMonitors);
					userMonitors.updateFocus(focusProjectId, focusTaskId);
					userMonitors.start();
				}
			}
		}
		userMonitors.get(username).updateFocus(focusProjectId, focusTaskId);
	}

}
