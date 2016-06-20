package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;
import org.ihtsdo.snowowl.authoring.single.api.service.NotificationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

public class UserMonitors {

	private String username;
	private Date lastAccessed;
	private boolean started;
	private final Runnable deathCallback;

	private final Map<Class, Monitor> currentMonitors;
	private Set<Monitor> monitorLoggedError;

	private final MonitorFactory monitorFactory;
	private final NotificationService notificationService;

	public static final int KEEP_ALIVE_MINUTES = 2;
	private static final int PAUSE_SECONDS = 10;
	private Logger logger = LoggerFactory.getLogger(getClass());

	public UserMonitors(String username, MonitorFactory monitorFactory, NotificationService notificationService, Runnable deathCallback) {
		this.username = username;
		this.monitorFactory = monitorFactory;
		this.notificationService = notificationService;
		currentMonitors = new HashMap<>();
		monitorLoggedError = new HashSet<>();
		this.deathCallback = deathCallback;
		accessed();
	}

	public void start() {
		synchronized (this) {
			started = true;
		}
		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					logger.info("Starting user monitors for {}", username);
					while (isStillInUse()) {
						final List<Class> keys = new ArrayList<>(currentMonitors.keySet());
						final int size = keys.size();
						for (int i = 0; i < size; i++) { // Old style for loop to avoid any concurrent modification problem.
							Monitor monitor = null;
							synchronized (currentMonitors) {
								if (currentMonitors.size() > i) {
									monitor = currentMonitors.get(keys.get(i));
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
										if (currentMonitors.containsValue(monitor)) {
											if (e instanceof FatalMonitorException) {
												logger.warn("Fatal monitor run, removing {}.", monitor, e);
												if (monitor.equals(currentMonitors.get(monitor.getClass()))) {
													currentMonitors.remove(monitor.getClass());
												}
											} else {
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
						}
						Thread.sleep(PAUSE_SECONDS * 1000);
					}
					logger.info("User monitors for {} no longer in use. Closing down.", username);
					deathCallback.run();
				} catch (InterruptedException e) {
					// This will probably happen when we restart the application.
					logger.info("User monitor interrupted.", e);
				}
			}
		}).start();
	}

	public void updateFocus(String focusProjectId, String focusTaskId) throws BusinessServiceException {
		accessed();
		if (focusProjectId != null) {
			addMonitorIfNew(monitorFactory.createMonitor(focusProjectId, focusTaskId));
		}
		safeStart();
	}

	private void addMonitorIfNew(Monitor monitor) {
		synchronized (currentMonitors) {
			if (!currentMonitors.containsValue(monitor)) {
				logger.info("New monitor for user {} - {}", username, monitor);
				final Monitor replaced = currentMonitors.put(monitor.getClass(), monitor);
				if (replaced != null) {
					monitorLoggedError.remove(replaced);
				}
			}
		}
	}

	private void safeStart() {
		if (!started) {
			synchronized (this) {
				if (!started) {
					start();
				}
			}
		}
	}

	public void accessed() {
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
