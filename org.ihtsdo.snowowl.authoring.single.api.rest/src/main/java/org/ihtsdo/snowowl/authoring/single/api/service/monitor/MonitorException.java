package org.ihtsdo.snowowl.authoring.single.api.service.monitor;

public class MonitorException extends Exception {
	public MonitorException(String message) {
		super(message);
	}

	public MonitorException(String message, Throwable cause) {
		super(message, cause);
	}
}
