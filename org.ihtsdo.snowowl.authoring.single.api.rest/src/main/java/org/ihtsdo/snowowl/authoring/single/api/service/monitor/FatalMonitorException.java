package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

public class FatalMonitorException extends MonitorException {
	public FatalMonitorException(String message) {
		super(message);
	}

	public FatalMonitorException(String message, Throwable cause) {
		super(message, cause);
	}
}
