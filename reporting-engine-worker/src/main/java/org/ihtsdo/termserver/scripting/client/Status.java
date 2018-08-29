package org.ihtsdo.termserver.scripting.client;

import com.google.gson.annotations.Expose;

public class Status {
	public static String SCHEDULED = "SCHEDULED";
	public static String RUNNING = "RUNNING";
	public static String COMPLETED = "COMPLETED";
	public static String FAILED = "FAILED";
	@Expose
	String status;
	
	public Status (String status) {
		this.status = status;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}
	
	@Override
	public String toString() {
		return "status: " + status;
	}
	
	public boolean is(String checkMe) {
		return this.status.equals(checkMe);
	}
	
	public boolean isFinalState() {
		return is(COMPLETED) || is(FAILED);
	}
}
