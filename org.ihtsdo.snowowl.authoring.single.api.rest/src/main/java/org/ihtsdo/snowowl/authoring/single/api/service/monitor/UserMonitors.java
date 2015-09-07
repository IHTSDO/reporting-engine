package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UserMonitors {

	private String username;
	private String focusProjectId;
	private String focusTaskId;
	private Date lastAccessed;
	private List<Monitor> currentMonitors;
	private Set<Monitor> monitorLoggedError;

	private final MonitorFactory monitorFactory;
	private final NotificationService notificationService;

	public static final int KEEP_ALIVE_MINUTES = 2;
	private static final int PAUSE_SECONDS = 10;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public UserMonitors(String username, MonitorFactory monitorFactory, NotificationService notificationService) {
		this.username = username;
		this.monitorFactory = monitorFactory;
		this.notificationService = notificationService;
		currentMonitors = new ArrayList<>();
		monitorLoggedError = new HashSet<>();
		accessed();
	}

	public void start() {
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					logger.info("Starting user monitors for {}", username);
					while (isStillInUse()) {
						final int size = currentMonitors.size();
						for (int i = 0; i < size; i++) { // Old style for loop to avoid any concurrent modification problem.
							Monitor monitor = null;
							synchronized (currentMonitors) {
								if (currentMonitors.size() > i) {
									monitor = currentMonitors.get(i);
								}
							}
							if (monitor != null) {
								logger.debug("Running monitor {}", monitor);
								try {
									final Notification notification = monitor.runOnce();
									logger.debug("Monitor {}, notification result {}", monitor, notification);
									if (notification != null) {
										notificationService.queueNotification(username, notification);
									}
								} catch (MonitorException e) {
									// Log monitor exception only once per monitor
									synchronized (currentMonitors) {
										if (currentMonitors.contains(monitor)) {
											if (!monitorLoggedError.contains(monitor)) {
												monitorLoggedError.add(monitor);
												logger.error("Monitor run failed.", e);
											} else {
												logger.info("Monitor run failed again.", e);
											}
										}
									}
								}
							}
						}
						Thread.sleep(PAUSE_SECONDS * 1000);
					}
					logger.info("User monitors for {} no longer in use. Closing down.", username);
				} catch (InterruptedException e) {
					// This will probably happen when we restart the application.
					logger.info("User monitor interrupted.", e);
				}
			}
		}).start();
	}

	public void updateFocus(String focusProjectId, String focusTaskId) {
		accessed();
		if(isChanged(this.focusProjectId, focusProjectId) || isChanged(this.focusTaskId, focusTaskId)) {
			this.focusProjectId = focusProjectId;
			this.focusTaskId = focusTaskId;
			synchronized (currentMonitors) {
				currentMonitors.clear();
				monitorLoggedError.clear();
				currentMonitors.addAll(monitorFactory.createMonitors(this.focusProjectId, this.focusTaskId));
				logger.debug("New monitors for user {} - {}", username, currentMonitors);
			}
		}
	}

	private boolean isChanged(String existingValue, String newValue) {
		if (newValue == null) {
			return existingValue != null;
		} else {
			return !newValue.equals(existingValue);
		}
	}

	private void accessed() {
		lastAccessed = new Date();
	}

	private boolean isStillInUse() {
		return lastAccessed.after(getMinLastAccessedTime());
	}

	private Date getMinLastAccessedTime() {
		final GregorianCalendar minLastAccessedTime = new GregorianCalendar();
		minLastAccessedTime.add(Calendar.MINUTE, -KEEP_ALIVE_MINUTES);
		return minLastAccessedTime.getTime();
	}

}
