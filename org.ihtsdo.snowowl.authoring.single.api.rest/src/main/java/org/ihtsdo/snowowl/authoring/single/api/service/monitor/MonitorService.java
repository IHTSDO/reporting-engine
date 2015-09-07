package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.snowowl.authoring.single.api.pojo.ConflictReport;
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
		updateUserFocus(username, focusProjectId, focusTaskId, null);
	}

	public void updateUserFocus(String username, String focusProjectId, String focusTaskId, ConflictReport conflictReport) {
		createIfNotExists(username);
		userMonitors.get(username).updateFocus(focusProjectId, focusTaskId, conflictReport);
	}

	private void createIfNotExists(String username) {
		if (!userMonitors.containsKey(username)) {
			synchronized(getClass()) {
				if (!userMonitors.containsKey(username)) {
					final UserMonitors userMonitors = new UserMonitors(username, monitorFactory, notificationService);
					this.userMonitors.put(username, userMonitors);
				}
			}
		}
	}

}
