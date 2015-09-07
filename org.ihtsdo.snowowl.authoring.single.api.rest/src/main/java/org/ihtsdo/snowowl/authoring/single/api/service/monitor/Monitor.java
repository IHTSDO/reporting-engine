package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

import org.ihtsdo.snowowl.authoring.single.api.pojo.Notification;

public interface Monitor {
	Notification runOnce() throws MonitorException;
}
