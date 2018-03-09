package org.ihtsdo.termserver.scripting.client;

import com.google.gson.annotations.Expose;

public class Status {
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
}
