package org.ihtsdo.termserver.scripting.client;

public class SnowOwlClientException extends Exception {

	private static final long serialVersionUID = 1L;

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
