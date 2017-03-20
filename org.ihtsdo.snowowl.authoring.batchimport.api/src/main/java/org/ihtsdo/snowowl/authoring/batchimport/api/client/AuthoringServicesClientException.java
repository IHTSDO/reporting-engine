package org.ihtsdo.snowowl.authoring.batchimport.api.client;

public class AuthoringServicesClientException extends Exception {

	public AuthoringServicesClientException(Throwable cause) {
		super(cause);
	}

	public AuthoringServicesClientException(String message) {
		super(message);
	}

	public AuthoringServicesClientException(String message, Throwable cause) {
		super(message, cause);
	}
}
