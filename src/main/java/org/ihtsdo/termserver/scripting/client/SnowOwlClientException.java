package org.ihtsdo.termserver.scripting.client;

public class SnowOwlClientException extends Exception {

	public SnowOwlClientException(Throwable cause) {
		super(cause);
	}

	public SnowOwlClientException(String message) {
		super(message);
	}

	public SnowOwlClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
