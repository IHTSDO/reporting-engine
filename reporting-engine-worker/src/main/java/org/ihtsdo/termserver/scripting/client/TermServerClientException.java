package org.ihtsdo.termserver.scripting.client;

public class TermServerClientException extends Exception {

	private static final long serialVersionUID = 1L;

	public TermServerClientException(Throwable cause) {
		super(cause);
	}

	public TermServerClientException(String message) {
		super(message);
	}

	public TermServerClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
